package app.tvplayer.demo

/**
 * Демо-контент для отладочной сборки. Ссылки указывают на локальный сервер,
 * имитирующий TorrServer (порт 8090 пробрасывается в эмулятор через adb reverse).
 */
object DemoContent {
    private const val SERVER = "http://127.0.0.1:8090/stream"

    val items = listOf(
        DemoItem(
            title = "Синтел",
            subtitle = "Фильм · 1080p · 3 дорожки",
            description = "Как типичный торрент-релиз: звук AC3 5.1, DTS 5.1 и стерео, пять дорожек субтитров.",
            episodes = listOf("Синтел (2010)" to "$SERVER/Sintel.2010.1080p.MULTi.mkv"),
        ),
        DemoItem(
            title = "Синтел: сериал",
            subtitle = "3 серии",
            description = "Плейлист серий, как его передаёт Lampa: переключение серий, карточка «Далее» в конце эпизода.",
            episodes = (1..3).map { "Серия $it" to "$SERVER/Sintel.S01E0$it.mkv" },
        ),
        DemoItem(
            title = "Продолжить с 5:30",
            subtitle = "Позиция из Lampa",
            description = "Lampa передаёт позицию, с которой продолжить, а по выходу получает её обратно.",
            episodes = listOf("Синтел (2010)" to "$SERVER/Sintel.2010.1080p.MULTi.mkv"),
            startPositionMs = 330_000,
        ),
        DemoItem(
            title = "Сервер недоступен",
            subtitle = "Экран ошибки",
            description = "Что будет, если TorrServer выключен: понятное сообщение и кнопка «Повторить».",
            episodes = listOf("Недоступный торрент" to "http://127.0.0.1:8099/stream/missing.mkv"),
        ),
    )
}
