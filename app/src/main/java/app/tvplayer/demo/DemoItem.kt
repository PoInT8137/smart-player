package app.tvplayer.demo

/** Демо-запуск «как из Lampa»: один файл или сериал из нескольких серий. */
data class DemoItem(
    val title: String,
    val subtitle: String,
    val description: String,
    /** Пары «название серии» → ссылка. */
    val episodes: List<Pair<String, String>>,
    val startPositionMs: Long? = null,
)
