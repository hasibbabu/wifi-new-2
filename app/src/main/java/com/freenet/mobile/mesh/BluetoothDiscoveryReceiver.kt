package com.freenet.mobile.mesh

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BluetoothDiscoveryReceiver(
    private val onDeviceFound: (BluetoothDevice) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (BluetoothDevice.ACTION_FOUND == intent.action) {
            val device = intent.getParcelableExtra<BluetoothDevice>(
                BluetoothDevice.EXTRA_DEVICE
            )
            if (device != null) onDeviceFound(device)
        }
    }
}
