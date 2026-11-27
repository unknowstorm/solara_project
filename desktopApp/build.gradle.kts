import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.unknowstorm.musicapp.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "solara"
            packageVersion = "1.0.0"
            description = "Solara: one music player for Jamendo, Audius, Deezer, YouTube and Spotify"
            vendor = "unknowstorm"
            licenseFile.set(rootProject.file("LICENSE"))

            // The bundled runtime is trimmed by jlink; these modules are needed for HTTPS, JSON and Supabase.
            modules("java.sql", "java.naming", "java.net.http", "java.management", "jdk.crypto.ec", "jdk.unsupported")

            linux {
                packageName = "solara"
                debMaintainer = "artemrimskiy8000@gmail.com"
                menuGroup = "Sound & Video"
                appCategory = "AudioVideo"
                shortcut = true
                // Optional icon: put a square PNG (512x512) at desktopApp/icon.png
                val icon = file("icon.png")
                if (icon.exists()) iconFile.set(icon)
            }
        }
    }
}