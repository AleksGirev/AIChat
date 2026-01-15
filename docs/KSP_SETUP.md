# KSP Setup Alternative

If you want to use KSP instead of kapt (recommended for better performance), you'll need to:

1. **Find the correct KSP version** for Kotlin 2.2.0
2. **Update the version catalog** with the correct version

## Finding the Correct KSP Version

KSP versions follow the format: `{KotlinVersion}-{KSPVersion}`

For Kotlin 2.2.0, try one of these versions:
- `2.2.0-1.0.25`
- `2.2.0-1.0.26` 
- `2.2.0-1.0.27`
- `2.2.0-1.0.28`

Or check the latest available version at: https://github.com/google/ksp/releases

## Current Setup

Currently using **kapt** with **Room 2.7.0**, which should work with Kotlin 2.2.0.

If you want to switch to KSP later:

1. Update `gradle/libs.versions.toml`:
   ```toml
   ksp = "2.2.0-1.0.XX"  # Replace XX with correct version
   ```

2. Update `app/build.gradle.kts`:
   ```kotlin
   plugins {
       // ... other plugins
       alias(libs.plugins.ksp)  // Instead of kotlin-kapt
   }
   
   dependencies {
       // ...
       ksp(libs.androidx.room.compiler)  // Instead of kapt
   }
   ```

3. Sync Gradle and rebuild

## Why kapt Works Now

Room 2.7.0 includes updated kotlinx-metadata-jvm that supports Kotlin 2.2.0, so kapt should work fine.
