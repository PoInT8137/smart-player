import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
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
            packageVersion = "0.3.0"
            // Строки установщика — только латиница: MSI (WiX, кодовая страница 1252) не принимает кириллицу
            description = "Smart Player - video player with TorrServer support"
            vendor = "PoInT8137"
            copyright = "(c) 2026 PoInT8137. All rights reserved."
            // mpv и uosc кладутся рядом с программой (resources/windows → app/resources)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            // jdk.httpserver — веб-интерфейс в стиле MPC-HC, который опрашивает Lampa
            modules("java.net.http", "jdk.crypto.ec", "jdk.httpserver")
            // «Открыть с помощью → Smart Player» для видеофайлов
            listOf(
                "mkv" to "video/x-matroska", "mp4" to "video/mp4", "avi" to "video/x-msvideo", "m4v" to "video/x-m4v",
                "mov" to "video/quicktime", "webm" to "video/webm", "ts" to "video/mp2t", "m2ts" to "video/mp2t",
            ).forEach { (ext, mime) -> fileAssociation(mime, ext, "${ext.uppercase()} video - Smart Player") }
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
                menu = true
                dirChooser = true
                upgradeUuid = "6b1d3c3e-6f7c-4a3e-9d2a-5d6f0b2b8a11"
            }
        }
    }
}

// Lampa для ПК узнаёт тип плеера по пути: с «mpc-hc» она передаёт позицию старта и опрашивает
// веб-интерфейс, чтобы сохранить прогресс. Настольная сборка lampa-desktop к тому же запускает
// только файлы из белого списка имён (vlc.exe, mpc-hc64.exe, mpv.exe…). Поэтому кладём рядом
// копию лаунчера с именем mpc-hc64.exe — это тот же Smart Player в режиме совместимости.
tasks.matching { it.name == "createDistributable" }.configureEach {
    doLast {
        val dir = layout.buildDirectory.dir("compose/binaries/main/app/SmartPlayer").get().asFile
        File(dir, "SmartPlayer.exe").copyTo(File(dir, "mpc-hc64.exe"), overwrite = true)
        File(dir, "app/SmartPlayer.cfg").copyTo(File(dir, "app/mpc-hc64.cfg"), overwrite = true)
    }
}

// В установщик (jpackage собирает его заново, не из папки выше) тот же лаунчер mpc-hc64.exe
// добавляем штатно — через --add-launcher, без ярлыков в меню «Пуск».
tasks.withType<AbstractJPackageTask>().matching { it.name == "packageExe" || it.name == "packageMsi" }.configureEach {
    val props = layout.buildDirectory.file("packaging/mpc-hc64.properties").get().asFile
    props.parentFile.mkdirs()
    props.writeText("win-shortcut=false\nwin-menu=false\n")
    freeArgs.addAll("--add-launcher", "mpc-hc64=${props.absolutePath}")
}
