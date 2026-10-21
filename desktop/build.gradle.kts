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
            packageVersion = "0.3.2"
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

// Установщик собираем сами — jpackage по готовой папке createDistributable (`--app-image`):
// в ней уже есть лаунчер mpc-hc64.exe, а свой шаблон WiX (галочка «Запустить Smart Player» в конце,
// после чего программа сама подключается к Lampa) штатный packageExe передать не даёт —
// Compose очищает каталог ресурсов перед вызовом jpackage.
// Сборка: ./gradlew :desktop:packageWindowsInstaller (нужен WiX 3 в PATH)
val packageWindowsInstaller by tasks.registering(Exec::class) {
    group = "compose desktop"
    description = "Установщик Smart Player для Windows с запуском после установки"
    dependsOn("createDistributable")
    val app = compose.desktop.application.nativeDistributions
    val appImage = layout.buildDirectory.dir("compose/binaries/main/app/SmartPlayer")
    val work = layout.buildDirectory.dir("packaging/installer")
    val output = layout.buildDirectory.dir("compose/binaries/main/installer")
    val template = file("packaging/windows/main.wxs")
    val associations = listOf(
        "mkv" to "video/x-matroska", "mp4" to "video/mp4", "avi" to "video/x-msvideo", "m4v" to "video/x-m4v",
        "mov" to "video/quicktime", "webm" to "video/webm", "ts" to "video/mp2t", "m2ts" to "video/mp2t",
    )
    inputs.file(template)
    inputs.dir(appImage)
    outputs.dir(output)

    executable = File(System.getProperty("java.home"), "bin/jpackage.exe").absolutePath
    // WiX — тот, что Compose сам скачивает для packageExe (задача :unzipWix → build/wix311)
    dependsOn(":unzipWix")
    val wixDir = rootProject.layout.buildDirectory.dir("wix311")
    doFirst {
        // В Windows переменная обычно называется «Path» — дописываем в неё же, а не заводим вторую
        val key = environment.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
        environment(key, wixDir.get().asFile.absolutePath + File.pathSeparator + environment[key])
        logger.lifecycle("WiX: ${wixDir.get().asFile} (${File(wixDir.get().asFile, "candle.exe").isFile})")
    }
    doFirst {
        // Все пути — в папке сборки: jpackage не открывает файлы по пути с кириллицей
        val dir = work.get().asFile.apply { deleteRecursively(); mkdirs() }
        val resources = File(dir, "resources").apply { mkdirs() }
        template.copyTo(File(resources, "main.wxs"), overwrite = true)
        val fa = associations.map { (ext, mime) ->
            File(dir, "fa-$ext.properties").apply {
                writeText("mime-type=$mime\nextension=$ext\ndescription=${ext.uppercase()} video - Smart Player\n")
            }
        }
        output.get().asFile.apply { deleteRecursively(); mkdirs() }
        args(
            "--type", "exe",
            "--app-image", appImage.get().asFile.absolutePath,
            "--dest", output.get().asFile.absolutePath,
            "--resource-dir", resources.absolutePath,
            "--name", app.packageName!!,
            "--app-version", app.packageVersion!!,
            "--vendor", app.vendor!!,
            "--description", app.description!!,
            "--copyright", app.copyright!!,
            "--icon", layout.buildDirectory.file("packaging/icon.ico").get().asFile.absolutePath,
            "--win-dir-chooser", "--win-shortcut", "--win-menu",
            "--win-menu-group", app.windows.menuGroup!!,
            "--win-upgrade-uuid", app.windows.upgradeUuid!!,
        )
        fa.forEach { args("--file-associations", it.absolutePath) }
    }
}
