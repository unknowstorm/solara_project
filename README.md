
<!-- ==================== HEADER ==================== -->

<table>
  <tr>
    <td width="180" align="center" valign="middle">
      <img src="designs/logo.png" width="140" alt="Solara Logo"/>
    </td>
    <td valign="middle">
      <h1>SOLARA</h1>
      <h3>Your music. Your space.</h3>
      <p>
        A unified music experience built with Kotlin Multiplatform,
        designed for Android.
      </p>
    </td>
  </tr>
  <tr>
    <td colspan="2" align="center">
      <img src="https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?style=flat-square&logo=kotlin&logoColor=white"/>
      <img src="https://img.shields.io/badge/Compose-Multiplatform-4285F4?style=flat-square"/>
      <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white"/>
      <img src="https://img.shields.io/badge/Backend-Supabase-3FCF8E?style=flat-square&logo=supabase&logoColor=white"/>
    </td>
  </tr>
</table>

---

<!-- ==================== NAVIGATION ==================== -->

## Navigation

<p align="center">
  <a href="#about-solara">About</a> &nbsp;·&nbsp;
  <a href="#features">Features</a> &nbsp;·&nbsp;
  <a href="#screenshots">Screenshots</a> &nbsp;·&nbsp;
  <a href="#music-providers">Providers</a> &nbsp;·&nbsp;
  <a href="#tech-stack">Tech Stack</a> &nbsp;·&nbsp;
  <a href="#architecture">Architecture</a> &nbsp;·&nbsp;
  <a href="#installation">Installation</a> &nbsp;·&nbsp;
  <a href="#roadmap">Roadmap</a> &nbsp;·&nbsp;
  <a href="#license">License</a>
</p>

---

<!-- ==================== ABOUT SOLARA ==================== -->

## About Solara

<p align="center">
  <img src="designs/Screenshot-1.png" width="32%" alt="Solara Screenshot 1"/>
  <img src="designs/Screenshot-2.png" width="32%" alt="Solara Screenshot 2"/>
  <img src="designs/Screenshot-3.png" width="32%" alt="Solara Screenshot 3"/>
</p>

<p align="center">
  <strong>Solara</strong> is a music application built with Kotlin Multiplatform and Compose Multiplatform, designed to bring multiple music streaming services together in a unified Android experience.
</p>

<p align="center">
  It combines music playback, discovery, and personal library management within a single application.
</p>

<p align="center">
  Built around the idea of a connected music experience, Solara aims to simplify how users discover, access, and organize their music — bringing different sources together in one personal space.
</p>

---

<!-- ==================== FEATURES ==================== -->

## Features

<p align="center">
  <img
    src="designs/features.svg"
    width="100%"
    alt="Solara Features"
  />
</p>

### Music & Playback
- Access music from multiple streaming providers in one application.
- Unified playback experience.
- Dedicated player interface and playback controls.
- Track discovery and search across supported providers.

### Personal Library
- Save and manage liked tracks.
- Create and manage playlists.
- Keep track of listening history.
- Discover music through recommendations.

### Account & Personalization
- User authentication.
- Personal profiles with avatar support.
- Cloud-backed user data.
- Material 3 interface and theming.
- English and Ukrainian interface languages.

---

<!-- ==================== SCREENSHOTS ==================== -->

## Screenshots

<p align="center">
  <img src="docs/images/screenshot-1.png" width="24%" alt="Solara Screenshot 1"/>
  <img src="docs/images/screenshot-2.png" width="24%" alt="Solara Screenshot 2"/>
  <img src="docs/images/screenshot-3.png" width="24%" alt="Solara Screenshot 3"/>
  <img src="docs/images/screenshot-4.png" width="24%" alt="Solara Screenshot 4"/>
</p>

---


<!-- ==================== MUSIC PROVIDERS ==================== -->

<h2 align="center">Music Providers</h2>

<p align="center">
  Solara integrates several music services, each with its own playback capabilities and limitations.
</p>

<table align="center">
  <tr>
    <td width="50%" align="center" valign="middle">
      <h3>Jamendo</h3>
      <p>Full-track streaming</p>
      <sub>Search and play full tracks directly inside Solara.</sub>
    </td>
    <td width="50%" align="center" valign="middle">
      <h3>Audius</h3>
      <p>Full-track streaming</p>
      <sub>Discover and play available tracks directly inside Solara.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" align="center" valign="middle">
      <h3>Deezer</h3>
      <p>Music discovery</p>
      <sub>Search and listen to 30-second previews through the public API.</sub>
    </td>
    <td width="50%" align="center" valign="middle">
      <h3>YouTube</h3>
      <p>Video-based playback</p>
      <sub>Search and play music using the embedded YouTube player.</sub>
    </td>
  </tr>
  <tr>
    <td colspan="2" align="center" valign="middle">
      <h3>Spotify</h3>
      <p>Remote playback control · Android · Premium</p>
      <sub>Control the Spotify application through the official Spotify App Remote SDK.</sub>
    </td>
  </tr>
</table>

<p align="center">
  <strong>Playback limitations</strong>
</p>

<p align="center">
  Deezer provides 30-second previews through its public API.<br/>
  YouTube playback requires the embedded player to remain visible. Background playback is not supported.<br/>
  Spotify integration requires Spotify Premium and the Spotify application. Solara does not store Spotify music or account data.
</p>


<!-- ==================== TECH STACK ==================== -->

## Tech Stack

<table align="center">
  <tr>
    <td align="center" width="12.5%">
      <img src="https://skillicons.dev/icons?i=kotlin" width="58" alt="Kotlin"/><br/><br/>
      <strong>Kotlin</strong><br/>
      <sub>Programming Language</sub>
    </td>
    <td align="center" width="12.5%">
      <img src="https://cdn.simpleicons.org/jetpackcompose" width="58" height="58" alt="Compose"/><br/><br/>
      <strong>Compose</strong><br/>
      <sub>UI Framework</sub>
    </td>
    <td align="center" width="12.5%">
      <img src="https://cdn.simpleicons.org/ktor" width="58" height="58" alt="Ktor"/><br/><br/>
      <strong>Ktor</strong><br/>
      <sub>HTTP Client</sub>
    </td>
    <td align="center" width="12.5%">
      <img src="https://skillicons.dev/icons?i=kotlin" width="58" alt="Coroutines"/><br/><br/>
      <strong>Coroutines</strong><br/>
      <sub>Async & Flow</sub>
    </td>
    <td align="center" width="12.5%">
      <img src="https://skillicons.dev/icons?i=supabase" width="58" alt="Supabase"/><br/><br/>
      <strong>Supabase</strong><br/>
      <sub>Cloud Services</sub>
    </td>
    <td align="center" width="12.5%">
      <img src="https://cdn.simpleicons.org/android" width="58" height="58" alt="Android Media3"/><br/><br/>
      <strong>Media3</strong><br/>
      <sub>Audio Playback</sub>
    </td>
    <td align="center" width="12.5%">
      <img src="https://skillicons.dev/icons?i=gradle" width="58" alt="Gradle"/><br/><br/>
      <strong>Gradle</strong><br/>
      <sub>Build System</sub>
    </td>
    <td align="center" width="12.5%">
      <img src="https://skillicons.dev/icons?i=androidstudio" width="58" alt="Android"/><br/><br/>
      <strong>Android</strong><br/>
      <sub>Target Platform</sub>
    </td>
  </tr>
</table>

---

<!-- ==================== ARCHITECTURE ==================== -->

## Architecture

Solara is built with a modular Kotlin Multiplatform architecture, using shared application logic and platform-specific playback integrations.

### Project Modules

| Module | Responsibility |
|---|---|
| `shared` | Shared application logic, UI, data and provider integrations |
| `androidApp` | Android application entry point and platform-specific functionality |

### Core Technologies

- **Compose Multiplatform** — declarative UI.
- **Kotlin Coroutines & Flow** — asynchronous operations and reactive data streams.
- **Ktor** — HTTP communication with external services.
- **Kotlinx Serialization** — data serialization.
- **Supabase** — authentication, database and storage services.
- **Android Media3** — Android audio playback.
- **Spotify App Remote SDK** — remote Spotify playback control.

### Provider Integration

Each music provider has its own integration and playback behavior. This keeps provider-specific API logic separate from the common application experience.

---

<!-- ==================== INSTALLATION ==================== -->

## Installation

To install Solara on your Android device:

1. Open the [Releases](https://github.com/unknowstorm/solara_project/releases) page.
2. Download the APK file from the latest release.
3. Open the downloaded APK on your Android device.
4. If required, allow installation from unknown sources.
5. Complete the installation and launch Solara.

---

<!-- ==================== ROADMAP ==================== -->

## Roadmap

Solara is an evolving project. The following areas represent potential future improvements.

- [ ] Improve and expand music provider integrations.
- [ ] Refine the unified playback experience.
- [ ] Improve library and playlist management.
- [ ] Expand personalization and discovery features.
- [ ] Continue UI and UX improvements.
- [ ] Improve application stability and performance.

---

<!-- ==================== LICENSE ==================== -->

## License

Solara is licensed under the [MIT License](LICENSE).

Third-party services, SDKs and trademarks belong to their respective owners. Use of their APIs and integrations must comply with their terms and developer policies.

---

<p align="center">
  <strong>SOLARA</strong><br/>
  <sub>Your music. Your space.</sub>
</p>
