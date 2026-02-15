package com.example.smartmotorapp.data

import com.example.smartmotorapp.BuildConfig

/**
 * Backend base URL derived from BuildConfig.
 *
 * - Debug builds: http://10.0.2.2:8000/  (emulator → local backend)
 * - Release builds: https://YOUR_RENDER_URL/ (public Render backend)
 *
 * Values are configured in `build.gradle.kts` via `buildConfigField`.
 */
object BackendConfig {
    val baseUrl: String = BuildConfig.BASE_URL.trimEnd('/')
}
