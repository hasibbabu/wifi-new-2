package com.freenet.mobile.mesh.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities

data class LocalNetwork(
    val transport: String,
    val interfaceName: String?,
    val addresses: List<String>
)

class LocalNetworkInfo(private val context: Context) {
    fun activeNetworks(): List<LocalNetwork> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.allNetworks.mapNotNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
            val lp: LinkProperties = cm.getLinkProperties(network) ?: return@mapNotNull null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@mapNotNull null
            LocalNetwork(
                transport = "wifi",
                interfaceName = lp.interfaceName,
                addresses = lp.linkAddresses.map { it.address.hostAddress ?: "" }
            )
        }
    }
}
