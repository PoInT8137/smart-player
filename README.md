# Smart Player

Видеоплеер для Android TV, заточенный под торренты (TorrServer) и работу в связке с Lampa.

## Возможности

- Media3 ExoPlayer + FFmpeg: MKV/MP4/AVI/TS, HLS/DASH, H.264/HEVC/AV1/HDR, звук AC3/E-AC3/DTS/TrueHD
- Внешний плеер для Lampa: extras в формате MX Player / VLC / Vimu, возврат позиции просмотра
- Управление с пульта: OK — пауза, ◀ ▶ — перемотка с ускорением, ▼ — меню
- Выбор звука и субтитров, режим кадра, скорость; лента серий и карточка «Далее»
- Auto Frame Rate — переключение частоты экрана под частоту кадров фильма
- Запоминание позиции и «Продолжить просмотр»

## Сборка

Нужны JDK 17 и Android SDK (или Android Studio).

```
gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`. Подробный план и заметки — в [PLAN.md](PLAN.md).
