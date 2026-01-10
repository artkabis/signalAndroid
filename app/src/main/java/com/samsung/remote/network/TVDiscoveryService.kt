package com.samsung.remote.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.samsung.remote.model.SamsungTV
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class TVDiscoveryService(private val context: Context) {

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    companion object {
        private const val TAG = "TVDiscoveryService"
        private const val SERVICE_TYPE = "_samsung-remote._tcp."
    }

    fun discoverTVs(): Flow<SamsungTV> = callbackFlow {
        val discoveredTVs = mutableSetOf<String>()

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: Error code $errorCode")
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: Error code $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "Service discovery started")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "Service discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "Service found: ${serviceInfo?.serviceName}")
                serviceInfo?.let {
                    nsdManager.resolveService(it, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            Log.e(TAG, "Resolve failed: Error code $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                            serviceInfo?.let { info ->
                                val host = info.host?.hostAddress
                                val port = info.port

                                if (host != null && !discoveredTVs.contains(host)) {
                                    discoveredTVs.add(host)
                                    val tv = SamsungTV(
                                        name = info.serviceName,
                                        ip = host,
                                        port = port
                                    )
                                    Log.d(TAG, "TV resolved: ${tv.name} at ${tv.ip}:${tv.port}")
                                    trySend(tv)
                                }
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "Service lost: ${serviceInfo?.serviceName}")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            close(e)
        }

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop discovery", e)
            }
        }
    }

    fun discoverTVsSimple(onTVFound: (SamsungTV) -> Unit) {
        // Fallback: Try common Samsung TV IPs on the local network
        // This is a simplified version that scans common IPs
        val commonTVName = "Samsung TV"

        // Try to find TVs by scanning the local network
        // In a real implementation, you would scan the network or use SSDP
        // For now, we'll create a placeholder that can be manually configured

        // Example: User could manually enter their TV's IP
        // This would be handled by the UI
    }
}
