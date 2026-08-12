package com.freenet.mobile.mesh

import android.Manifest
import android.app.Activity
import android.os.Build

object AndroidPermissions {
    fun required(): Array<String> {
        val p = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            p += Manifest.permission.BLUETOOTH_SCAN
            p += Manifest.permission.BLUETOOTH_CONNECT
            p += Manifest.permission.BLUETOOTH_ADVERTISE
        } else {
            p += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33) {
            p += Manifest.permission.POST_NOTIFICATIONS
            p += Manifest.permission.NEARBY_WIFI_DEVICES
            p += Manifest.permission.READ_MEDIA_IMAGES
            p += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            p += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        // Messaging works without these, but requesting them up front avoids
        // a mid-call permission prompt interrupting an incoming call.
        p += Manifest.permission.RECORD_AUDIO
        p += Manifest.permission.CAMERA
        return p.toTypedArray()
    }

    fun request(activity: Activity, requestCode: Int = 9001) {
        val permissions = required()
        if (permissions.isNotEmpty()) {
            activity.requestPermissions(permissions, requestCode)
        }
    }
}
