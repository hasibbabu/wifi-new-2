package com.freenet.mobile

import android.app.Application
import com.freenet.mobile.mesh.FreeNetBridge
import com.freenet.mobile.mesh.call.DirectCallManager

class FreeNetApp : Application() {
    lateinit var bridge: FreeNetBridge
        private set
    lateinit var callManager: DirectCallManager
        private set

    override fun onCreate() {
        super.onCreate()
        bridge = FreeNetBridge(this)
        callManager = DirectCallManager(this, bridge)
    }
}
