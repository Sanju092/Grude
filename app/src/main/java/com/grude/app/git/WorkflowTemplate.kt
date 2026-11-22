package com.grude.app.git

object WorkflowTemplate {
    const val DEFAULT_WORKFLOW_RELATIVE_PATH = ".github/workflows/build-apk.yml"

    val ANDROID_APK_WORKFLOW_YAML: String = """
        name: Build Android APK

        on:
          push:
            branches: [ main, master ]
          workflow_dispatch:

        jobs:
          build-apk:
            runs-on: ubuntu-latest
            steps:
              - name: Checkout code
                uses: actions/checkout@v4

              - name: Set up JDK 17
                uses: actions/setup-java@v4
                with:
                  java-version: '17'
                  distribution: 'temurin'

              - name: Setup Gradle
                uses: gradle/actions/setup-gradle@v4

              - name: Build Debug APK
                run: |
                  if [ -f "./gradlew" ]; then
                    chmod +x ./gradlew
                    ./gradlew assembleDebug
                  else
                    gradle assembleDebug
                  fi

              - name: Upload Android APK Artifact
                uses: actions/upload-artifact@v4
                with:
                  name: android-debug-apk
                  path: |
                    app/build/outputs/apk/debug/*.apk
                    **/build/outputs/apk/debug/*.apk
                  if-no-files-found: error
    """.trimIndent() + "\n"
}
