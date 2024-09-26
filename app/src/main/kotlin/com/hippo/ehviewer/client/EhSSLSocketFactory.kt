/*
 * Copyright 2022 Tarsin Norbin
 *
 * This file is part of EhViewer
 *
 * EhViewer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * EhViewer is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with EhViewer.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package com.hippo.ehviewer.client

import android.util.Log
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.builtInHosts
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import org.conscrypt.Conscrypt

private const val EXCEPTIONAL_DOMAIN = "hath.network"
private val sslSocketFactory: SSLSocketFactory = SSLContext.getInstance("TLS", Conscrypt.newProvider()).apply {
    init(null, null, null)
}.socketFactory

object EhSSLSocketFactory : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> = sslSocketFactory.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = sslSocketFactory.supportedCipherSuites
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket = createConfiguredSocket(sslSocketFactory.createSocket(s, resolveHost(s, host), port, autoClose) as SSLSocket)
    override fun createSocket(host: String, port: Int): Socket = createConfiguredSocket(sslSocketFactory.createSocket(host, port) as SSLSocket)
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = createConfiguredSocket(sslSocketFactory.createSocket(host, port, localHost, localPort) as SSLSocket)
    override fun createSocket(host: InetAddress, port: Int): Socket = createConfiguredSocket(sslSocketFactory.createSocket(host, port) as SSLSocket)
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket = createConfiguredSocket(sslSocketFactory.createSocket(address, port, localAddress, localPort) as SSLSocket)

    private fun createConfiguredSocket(socket: SSLSocket): SSLSocket {
        Conscrypt.setCheckDnsForEch(socket, true)
        logEchConfigList(socket)
        return socket
    }

    private fun logEchConfigList(socket: SSLSocket) {
        Conscrypt.getEchConfigList(socket)?.let { echConfigList ->
            Log.d("ECHConfigList", "ECH Config List (${echConfigList.size} bytes):")
            logHex(echConfigList)
        }
    }

    private fun logHex(buf: ByteArray) {
        val hexString = buf.joinToString(":") { String.format("%02x", it.toInt() and 0xFF) }
        Log.d("ECHConfigList", hexString)
    }

    private fun resolveHost(socket: Socket, host: String): String = socket.inetAddress.hostAddress.takeIf { host in builtInHosts || EXCEPTIONAL_DOMAIN in host || host in Settings.dohUrl } ?: host
}

fun OkHttpClient.Builder.install(sslSocketFactory: SSLSocketFactory) = apply {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())!!
    factory.init(null as KeyStore?)
    val manager = factory.trustManagers!!
    val trustManager = manager.filterIsInstance<X509TrustManager>().first()
    sslSocketFactory(sslSocketFactory, trustManager)
}
