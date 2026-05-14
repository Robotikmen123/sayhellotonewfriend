package com.sayhello.circus

import android.app.Application
import com.sayhello.circus.data.CircusPreferences
import com.sayhello.circus.data.RoleplayConsentLedger
import com.sayhello.circus.data.WorldEventStore
import com.sayhello.circus.model3d.AssetExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CircusApp : Application() {
    val preferences: CircusPreferences by lazy { CircusPreferences(this) }
    val consentLedger: RoleplayConsentLedger by lazy { RoleplayConsentLedger(this) }
    val worldEventStore: WorldEventStore by lazy { WorldEventStore(this) }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Unpack any bundled model zips from assets/models/*.zip into
        // filesDir/models/<id>/. Idempotent — a per-zip marker file means we
        // only do real work once per install (or when the zip hash changes).
        ioScope.launch {
            runCatching { AssetExtractor(this@CircusApp).extractAllBundled() }
        }
    }
}
