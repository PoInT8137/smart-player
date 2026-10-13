// Google Maven отдаёт только свои группы — чтобы его недоступность (бывает за прокси/VPN)
// не ломала поиск плагинов Kotlin/Compose, которые лежат в Maven Central.
fun RepositoryHandler.googleOnly() = google {
    content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google\\.(android|testing|firebase).*")
        includeGroupByRegex("androidx.*")
    }
}

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.(android|testing|firebase).*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Если dl.google.com недоступен (прокси/VPN), библиотеки androidx можно взять с зеркала
        // Alibaba Cloud — только по явному флагу: gradlew ... -Psmartplayer.googleMirror=true
        if (providers.gradleProperty("smartplayer.googleMirror").orNull == "true") {
            maven("https://maven.aliyun.com/repository/google") {
                name = "GoogleMirrorAliyun"
                content { includeGroupByRegex("androidx.*") }
            }
        }
        googleOnly()
        mavenCentral()
    }
}

rootProject.name = "SmartPlayer"
include(":app")
// Общий код без Android: клиент TorrServer и утилиты
include(":core")
// Версия для Windows
include(":desktop")
