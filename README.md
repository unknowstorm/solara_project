# Solara

A music player for Android and desktop, built with Kotlin Multiplatform and Compose Multiplatform.

## What it does
- Full tracks from Jamendo and Audius, played inside the app.
- Deezer: search and 30-second previews (Deezer does not provide full audio through its public API).
- YouTube: search and an embedded YouTube player (the video stays on screen; no background playback, per YouTube rules).
- Spotify (Android, Premium): remote control of the Spotify app. Solara stores nothing from Spotify.
- Accounts, likes, playlists and listening history are stored in Supabase.
- Interface languages: English (default) and Ukrainian.

## Build
Requirements: JDK 21, the Android SDK, and an Android device or emulator for the Android app.

1. Copy `shared/src/commonMain/kotlin/com/unknowstorm/musicapp/Secrets.kt.example` to `Secrets.kt` in the same folder and fill in your own keys. `Secrets.kt` is git-ignored: never commit it.
2. Android: `./gradlew :androidApp:installDebug`
3. Desktop compile check: `./gradlew :shared:compileKotlinJvm`

## Spotify setup (developers)
The Spotify section uses the Spotify App Remote SDK. Register your package name and your debug keystore SHA-1 in the Spotify Developer Dashboard, and add your Spotify account to the app's User Management (Development Mode allows up to five users).

## Third-party services
Service names and logos belong to their owners. Follow each service's developer terms and brand guidelines when changing the related screens.
