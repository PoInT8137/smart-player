import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// jlink/jpackage на Windows читают пути в системной кодировке и ломаются на кириллице
// («Плеер для Android TV»). Флагом можно собрать в папку с латиницей:
//   gradlew :desktop:createDistributable -Psmartplayer.desktopBuildDir=C:/build/smartplayer
providers.gradleProperty("smartplayer.desktopBuildDir").orNull?.let { layout.buildDirectory.set(file(it)) }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    // Тёмный заголовок окна Windows 11 (DwmSetWindowAttribute)
    implementation(libs.jna.platform)

    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "app.smartplayer.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "SmartPlayer"
            packageVersion = "0.2.0"
            description = "Smart Player — видеоплеер с поддержкой TorrServer"
            vendor = "PoInT8137"
            copyright = "© 2026 PoInT8137. Все права защищены."
            // mpv и uosc кладутся рядом с программой (resources/windows → app/resources)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            // jdk.httpserver — веб-интерфейс в стиле MPC-HC, который опрашивает Lampa
            modules("java.net.http", "jdk.crypto.ec", "jdk.httpserver")
            windows {
                // Иконку кладём в папку сборки: jpackage не открывает файлы по пути с кириллицей
                iconFile.set(
                    layout.buildDirectory.file("packaging/icon.ico").get().asFile.also {
                        it.parentFile.mkdirs()
                        project.file("icon.ico").copyTo(it, overwrite = true)
                    }
                )
                menuGroup = "Smart Player"
                shortcut = true
                dirChooser = true
                upgradeUuid = "6b1d3c3e-6f7c-4a3e-9d2a-5d6f0b2b8a11"
            }
        }
    }
}

// Lampa для ПК узнаёт тип плеера по пути: с «mpc-hc» в имени она передаёт позицию старта
// и опрашивает веб-интерфейс, чтобы сохранить прогресс. Кладём рядом такую копию лаунчера.
tasks.matching { it.name == "createDistributable" }.configureEach {
    doLast {
        val dir = layout.buildDirectory.dir("compose/binaries/main/app/SmartPlayer").get().asFile
        File(dir, "SmartPlayer.exe").copyTo(File(dir, "SmartPlayer-mpc-hc.exe"), overwrite = true)
        File(dir, "app/SmartPlayer.cfg").copyTo(File(dir, "app/SmartPlayer-mpc-hc.cfg"), overwrite = true)
    }
}
