# DefaultRenderersFactory загружает FFmpeg-рендереры через рефлексию по полному имени.
# Consumer-правила сборки Jellyfin сохраняют только конструктор, но не имя класса.
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { <init>(...); }
-keep class androidx.media3.decoder.ffmpeg.ExperimentalFfmpegVideoRenderer { <init>(...); }
