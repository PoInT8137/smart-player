package app.smartplayer.desktop.data

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.CRC32C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelDbLogTest {

    /** Минимальная база: MANIFEST с номером журнала 7 и последней операцией 41. */
    private fun database(existingLog: ByteArray? = null): File {
        val dir = Files.createTempDirectory("ldb").toFile()
        File(dir, "CURRENT").writeText("MANIFEST-000001\n")
        val edit = byteArrayOf(2, 7, 4, 41) // log number = 7, last sequence = 41
        File(dir, "MANIFEST-000001").writeBytes(record(1, edit))
        existingLog?.let { File(dir, "000007.log").writeBytes(it) }
        return dir
    }

    private fun record(type: Int, payload: ByteArray): ByteArray {
        val c = CRC32C().apply { update(type); update(payload) }.value.toInt()
        val masked = ((c ushr 15) or (c shl 17)) + 0xa282ead8.toInt()
        return ByteBuffer.allocate(7 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(masked).putShort(payload.size.toShort()).put(type.toByte()).put(payload).array()
    }

    private fun sequences(log: File): List<Long> {
        // Записи FULL/FIRST: первые 8 байт данных — номер операции
        val data = log.readBytes()
        val out = mutableListOf<Long>()
        var pos = 0
        while (pos + 7 <= data.size) {
            val left = 32768 - pos % 32768
            if (left < 7) { pos += left; continue }
            val b = ByteBuffer.wrap(data, pos, 7).order(ByteOrder.LITTLE_ENDIAN)
            b.int
            val len = b.short.toInt() and 0xFFFF
            val type = b.get().toInt()
            if (type == 0 && len == 0) { pos += left; continue }
            if (type == 1 || type == 2) out += ByteBuffer.wrap(data, pos + 7, 8).order(ByteOrder.LITTLE_ENDIAN).long
            pos += 7 + len
        }
        return out
    }

    @Test
    fun `новый журнал создаётся с номером из MANIFEST`() {
        val dir = database()
        LevelDbLog(dir).put(listOf("k".toByteArray() to "v".toByteArray()))
        assertEquals(listOf(42L), sequences(File(dir, "000007.log")))
    }

    @Test
    fun `номер операции больше всех записанных в журнал`() {
        val batch = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putLong(100).putInt(3).array()
        val dir = database(record(1, batch))
        LevelDbLog(dir).put(listOf("k".toByteArray() to "v".toByteArray()))
        assertEquals(listOf(100L, 103L), sequences(File(dir, "000007.log")))
    }

    @Test
    fun `длинная запись режется по блокам и проходит проверку`() {
        // Журнал заканчивается за 3 байта до конца блока — заголовок туда не влезет
        val dir = database(ByteArray(32768 - 3))
        val big = ByteArray(70_000) { (it % 251).toByte() }
        LevelDbLog(dir).put(listOf("big".toByteArray() to big))
        LevelDbLog(dir).put(listOf("next".toByteArray() to "x".toByteArray()))
        val log = File(dir, "000007.log")
        assertEquals(listOf(42L, 43L), sequences(log))
        assertTrue(log.length() > 32768 * 3)
    }
}
