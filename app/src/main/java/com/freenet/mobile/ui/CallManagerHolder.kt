package com.freenet.mobile.ui

import android.content.Context
import com.freenet.mobile.FreeNetApp
import com.freenet.mobile.mesh.call.DirectCallManager

object CallManagerHolder {
    fun get(context: Context): DirectCallManager =
        (context.applicationContext as FreeNetApp).callManager
}
