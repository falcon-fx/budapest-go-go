package com.example.myapplication.data.util

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class Tls12SocketFactory(private val delegateSocketFactory: SSLSocketFactory): SSLSocketFactory() {
    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
        return patch(delegateSocketFactory.createSocket(s, host, port, autoClose))
    }

    override fun createSocket(host: String?, port: Int): Socket {
        return patch(delegateSocketFactory.createSocket(host, port))
    }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
        return patch(delegateSocketFactory.createSocket(host, port, localHost, localPort))
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        return patch(delegateSocketFactory.createSocket(host, port))
    }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
        return patch(delegateSocketFactory.createSocket(address, port, localAddress, localPort))
    }

    override fun getDefaultCipherSuites(): Array<String> {
        return delegateSocketFactory.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return delegateSocketFactory.supportedCipherSuites
    }

    private fun patch(s: Socket): Socket {
        if (s is SSLSocket) {
            s.enabledProtocols = arrayOf("TLSv1.2")
        }
        return s
    }

}
