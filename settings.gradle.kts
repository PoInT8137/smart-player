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
