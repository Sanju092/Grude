# Grude — Native Android Git & GitHub Client

**Grude** is a Native Android mobile application (Kotlin + Jetpack Compose + Material 3 + Eclipse JGit) designed to connect your GitHub account, push local phone project folders with a single tap, monitor GitHub Actions workflows and build files, and download/install compiled Android `.apk` artifacts directly on-device.

## Key Capabilities

- **5-Method Encrypted Auth Vault (`auth/AuthVault.kt`, `auth/DeviceFlowAuth.kt`)**
  - Fine-grained / Classic Personal Access Tokens (PAT)
  - Serverless GitHub Device Flow (no backend required)
  - GitHub App user access tokens + refresh token support
  - OAuth App Web Flow (`grude://callback` deep link ready for the optional Relay Server)
  - On-device Ed25519 SSH key pair generator
- **One-Button Folder Push -> Instant APK Install (`git/LocalGitEngine.kt`, `actions/PushAndBuildOrchestrator.kt`)**
  - Create a new GitHub repository inside Grude.
  - Pick any local project folder on your Android device via Storage Access Framework (`OpenDocumentTree`).
  - Optional toggle to auto-inject `.github/workflows/build-apk.yml` when no workflow exists.
  - Single-tap **Push Folder & Build APK** commits and pushes via JGit, watches the GitHub Actions run, downloads the 60-second signed artifact `.zip` (stripping the GitHub `Authorization` header on redirect), safely unzips the `.apk`, and opens Android's native `FileProvider` PackageInstaller.
- **Actions Monitor, Build-File Editor & Local Git (`api/GitHubApiClient.kt`, `ui/screens/ActionsAndCodeScreens.kt`)**
  - Inspect workflow runs, job steps, and plain-text job logs, and trigger re-runs.
  - Browse repository files, edit `.github/workflows/*.yml` or `build.gradle.kts`, and commit directly.
  - Clone, pull, commit, and push locally with Eclipse JGit, plus inspect open Pull Requests and Issues.

## How to Build & Run

1. **Open in Android Studio (Ladybug / 2024.2+):** Open the `grude-android` folder, let Gradle sync with JDK 17, and click **Run** on your Android device (Android 8.0 / API 26+).
2. **Or Build via GitHub Actions:** Push this folder to a GitHub repository—the included `.github/workflows/build-apk.yml` automatically builds `app-debug.apk` and uploads it as the `grude-debug-apk` artifact.
