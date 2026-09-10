# CampusVerse — Release Signing Configuration Guide

## Overview
CampusVerse builds are configured to use R8 code minification and resource shrinking for all release builds. Production signing credentials must never be committed to source control.

## Environment Variables / CI Setup
To sign release APKs / AABs in a CI/CD pipeline (e.g., GitHub Actions, Bitrise) or secure local environment, provide the following environment variables or Gradle project properties:

| Variable | Description |
| :--- | :--- |
| `KEYSTORE_FILE` | Absolute path to the release `.jks` or `.keystore` file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias name |
| `KEY_PASSWORD` | Key password |

### Example Local Build Command
```bash
./gradlew assembleRelease \
  -PKEYSTORE_FILE=/path/to/release.keystore \
  -PKEYSTORE_PASSWORD=superSecretKeystorePass \
  -PKEY_ALIAS=campusverse_release \
  -PKEY_PASSWORD=superSecretKeyPass
```

### Local Development / Evaluation Fallback
When `KEYSTORE_FILE` is not set or the file is not found, `app/build.gradle.kts` automatically initializes signing with the local debug signing config. This ensures developers and automated CI checks can run `./gradlew assembleRelease` to verify R8 bytecode optimization and resource shrinking without exposing production keys.
