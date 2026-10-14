package app.smartplayer.desktop.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32C

/**
 * Дозапись в журнал LevelDB — так Chromium (а значит, и Electron-приложения вроде Lampa для ПК)
 * хранит localStorage. Записываем пакет «положить ключ» в конец текущего .log-файла: при следующем
 * запуске LevelDB сама его прочитает. Базу при этом должна держать закрытой сама программа-владелец.
 *
 * Формат: https://github.com/google/leveldb/blob/main/doc/log_format.md
 */
class LevelDbLog(private val dir: File) {

    /** Записывает ключи одним пакетом. Если проверка записанного не прошла — файл возвращается как был. */
    fun put(entries: List<Pair<ByteArray, ByteArray>>) {
        val (lastSequence, logNumber) = readManifest()
        val logs = dir.listFiles { f -> LOG_NAME.matches(f.name) }.orEmpty()
            .map { it to LOG_NAME.find(it.name)!!.groupValues[1].toLong() }
            .filter { it.second >= logNumber }
            .sortedBy { it.second }
        val maxInLogs = logs.maxOfOrNull { (f, _) -> batches(f).maxOfOrNull { it.lastSequence } ?: 0L } ?: 0L
        val sequence = maxOf(lastSequence, maxInLogs) + 1
        val target = logs.lastOrNull()?.first ?: File(dir, "%06d.log".format(logNumber))

        val batch = encodeBatch(sequence, entries)
        val originalLength = if (target.exists()) target.length() else 0L
        RandomAccessFile(target, "rw").use { raf ->
            raf.seek(originalLength)
            appendRecord(raf, originalLength, batch)
            raf.fd.sync()
        }

        // Проверяем, что LevelDB прочитает ровно то, что мы записали
        val written = batches(target).lastOrNull()
        if (written == null || written.sequence != sequence || !written.raw.contentEquals(batch)) {
            RandomAccessFile(target, "rw").use { it.setLength(originalLength) }
            error("запись в базу Lampa не прошла проверку")
        }
    }

    private class Batch(val sequence: Long, val count: Int, val raw: ByteArray) {
        val lastSequence: Long get() = sequence + count - 1
    }

    private fun encodeBatch(sequence: Long, entries: List<Pair<ByteArray, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putLong(sequence).putInt(entries.size).array())
        for ((key, value) in entries) {
            out.write(TYPE_VALUE)
            out.writeVarint(key.size.toLong()); out.write(key)
            out.writeVarint(value.size.toLong()); out.write(value)
        }
        return out.toByteArray()
    }

    /** Запись журнала: блоки по 32 КБ, длинная запись режется на части FIRST/MIDDLE/LAST. */
    private fun appendRecord(raf: RandomAccessFile, startOffset: Long, data: ByteArray) {
        var offset = startOffset
        var pos = 0
        var first = true
        do {
            val left = BLOCK - (offset % BLOCK).toInt()
            if (left < HEADER) {
                raf.write(ByteArray(left)) // хвост блока — нули
                offset += left
                continue
            }
            val chunk = minOf(left - HEADER, data.size - pos)
            val last = pos + chunk == data.size
            val type = when {
                first && last -> FULL
                first -> FIRST
                last -> LAST
                else -> MIDDLE
            }
            val crc = CRC32C().apply { update(type); update(data, pos, chunk) }.value
            val header = ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(mask(crc)).putShort(chunk.toShort()).put(type.toByte()).array()
            raf.write(header)
            raf.write(data, pos, chunk)
            offset += HEADER + chunk
            pos += chunk
            first = false
        } while (pos < data.size)
    }

    /** Все целые записи журнала (с проверкой CRC); на первой повреждённой чтение останавливается. */
    private fun records(file: File): List<ByteArray> {
        val data = file.readBytes()
        val result = mutableListOf<ByteArray>()
        var pos = 0
        var pending: ByteArrayOutputStream? = null
        while (pos + HEADER <= data.size) {
            val left = BLOCK - pos % BLOCK
            if (left < HEADER) { pos += left; continue }
            val buf = ByteBuffer.wrap(data, pos, HEADER).order(ByteOrder.LITTLE_ENDIAN)
            val crc = buf.int
            val length = buf.short.toInt() and 0xFFFF
            val type = buf.get().toInt()
            if (type == 0 && length == 0) { pos += left; continue } // нули в конце блока
            if (pos + HEADER + length > data.size) break
            val actual = CRC32C().apply { update(type); update(data, pos + HEADER, length) }.value
            if (mask(actual) != crc) break
            val payload = data.copyOfRange(pos + HEADER, pos + HEADER + length)
            pos += HEADER + length
            when (type) {
                FULL -> result += payload
                FIRST -> pending = ByteArrayOutputStream().apply { write(payload) }
                MIDDLE -> pending?.write(payload)
                LAST -> pending?.let { it.write(payload); result += it.toByteArray(); pending = null }
                else -> break
            }
        }
        return result
    }

    private fun batches(file: File): List<Batch> = records(file).mapNotNull { rec ->
        if (rec.size < 12) return@mapNotNull null
        val b = ByteBuffer.wrap(rec).order(ByteOrder.LITTLE_ENDIAN)
        Batch(b.long, b.int, rec)
    }

    /** Из MANIFEST: последний номер операции и номер текущего журнала. */
    private fun readManifest(): Pair<Long, Long> {
        val name = File(dir, "CURRENT").readText().trim()
        var lastSequence = 0L
        var logNumber = 0L
        for (rec in records(File(dir, name))) {
            val r = Reader(rec)
            while (r.hasMore()) {
                when (val tag = r.varint().toInt()) {
                    1 -> r.skip(r.varint().toInt())                     // comparator
                    2 -> logNumber = r.varint()
                    3, 9 -> r.varint()                                  // next file, prev log
                    4 -> lastSequence = r.varint()
                    5 -> { r.varint(); r.skip(r.varint().toInt()) }     // compact pointer
                    6 -> { r.varint(); r.varint() }                     // deleted file
                    7 -> { r.varint(); r.varint(); r.varint(); r.skip(r.varint().toInt()); r.skip(r.varint().toInt()) } // new file
                    else -> error("неизвестная запись MANIFEST: $tag")
                }
            }
        }
        return lastSequence to logNumber
    }

    private class Reader(private val b: ByteArray) {
        private var i = 0
        fun hasMore() = i < b.size
        fun skip(n: Int) { i += n }
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val x = b[i++].toInt() and 0xFF
                result = result or ((x and 0x7F).toLong() shl shift)
                if (x and 0x80 == 0) return result
                shift += 7
            }
        }
    }

    private fun ByteArrayOutputStream.writeVarint(value: Long) {
        var v = value
        while (v >= 0x80) { write(((v and 0x7F) or 0x80).toInt()); v = v ushr 7 }
        write(v.toInt())
    }

    private fun mask(crc: Long): Int {
        val c = crc.toInt()
        return ((c ushr 15) or (c shl 17)) + MASK_DELTA
    }

    private companion object {
        const val BLOCK = 32768
        const val HEADER = 7
        const val FULL = 1
        const val FIRST = 2
        const val MIDDLE = 3
        const val LAST = 4
        const val TYPE_VALUE = 1
        const val MASK_DELTA = 0xa282ead8.toInt()
        val LOG_NAME = Regex("""^(\d+)\.log$""")
    }
}
