package com.example.smartmotorapp.data

/**
 * Backend base URL (no trailing slash). Retrofit will use this + "/" as baseUrl.
 * - Emulator: http://10.0.2.2:8000 (10.0.2.2 = host machine)
 * - Physical device: use your machine's LAN IP, e.g. http://192.168.1.5:8000
 */
object BackendConfig {
    const val BACKEND_BASE_URL = "http://10.0.2.2:8000"
}
