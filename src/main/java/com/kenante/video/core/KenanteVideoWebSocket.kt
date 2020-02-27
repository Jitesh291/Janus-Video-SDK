package com.kenante.video.core

import android.util.Log
import com.koushikdutta.async.callback.CompletedCallback
import com.koushikdutta.async.callback.DataCallback
import com.koushikdutta.async.callback.WritableCallback
import com.koushikdutta.async.http.AsyncHttpClient
import com.koushikdutta.async.http.WebSocket
import com.varenia.kenante_core.core.KenanteSettings
import com.kenante.video.helper.SslUtils
import com.varenia.kenante_core.interfaces.KenanteWsConnEventListener
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.Exception

/*
    This class is used to create a live connection between Android app and Janus web socket.
 */

object KenanteVideoWebSocket {

    val TAG = KenanteVideoWebSocket::class.java.simpleName

    var kenanteWsInstance: KenanteVideoWebSocket? = null

    fun getInstance(): KenanteVideoWebSocket {
        if (kenanteWsInstance == null) {
            kenanteWsInstance = this
        }
        return kenanteWsInstance!!
    }

    var webSocket: WebSocket? = null
    var webSocketListener: KenanteWsConnEventListener? = null

    fun setListener(webSocketListener: KenanteWsConnEventListener) {
        KenanteVideoWebSocket.webSocketListener = webSocketListener
    }

    // connect method tries to establish a connection with the websocket server.
    fun setCertificates(){
        val sslContext = SslUtils.getSslContextForCertificateFile(KenanteSettings.getInstance().getContext()!!, "certificate.pem")
        AsyncHttpClient.getDefaultInstance().sslSocketMiddleware.sslContext = sslContext
    }

    fun connect() {
        setCertificates()
        KenanteSettings.getInstance().checkVideoInit()
        AsyncHttpClient.getDefaultInstance().websocket(KenanteSettings.getInstance().getJanusUrl(), KenanteSettings.getInstance().getProtocol(), object : AsyncHttpClient.WebSocketConnectCallback {
            override fun onCompleted(p0: Exception?, p1: WebSocket?) {
                if (p0 != null) {
                    if (p0.message != null)
                        Log.e(TAG, p0.message!!)
                    onError(p0)
                    return
                }
                Log.i(TAG, "Connected to websocket")
                //runOnUiThread{HelperMethods.ShowToastShort(this@JanusActivity, "Connected to Janus")}
                webSocket = p1
                webSocket?.writeableCallback = WritableCallback { Log.d(TAG, "On writable") }
                webSocket?.pongCallback = WebSocket.PongCallback { Log.d(TAG, "Pong callback") }
                webSocket?.dataCallback = DataCallback { emitter, bb -> Log.d(TAG, "New Data") }
                webSocket?.endCallback = CompletedCallback { Log.d(TAG, "Client End") }
                webSocket?.stringCallback = WebSocket.StringCallback { s -> onMessage(s) }
                webSocket?.closedCallback = CompletedCallback { ex ->
                    Log.d(TAG, "Socket closed for some reason")
                    if (ex != null) {
                        Log.d(TAG, "SOCKET EX " + ex.message)
                        val writer = StringWriter()
                        val printWriter = PrintWriter(writer)
                        ex.printStackTrace(printWriter)
                        printWriter.flush()
                        Log.d(TAG, "StackTrace \n\t$writer")
                    }
                    ex?.let { onError(it) } ?: onClose(-1, "unknown", true)
                }
                onOpen()
            }

        })
    }

    fun disconnect() {
        webSocket?.close()
        webSocket?.end()
        webSocketListener?.onDisconnected()
    }

    fun sendMessage(message: String) {
        webSocket?.send(message)
    }

    private fun onOpen() {
        //Websocket got opened
        Log.e(TAG, "Websocket opened")
        webSocketListener?.onOpen()
    }

    private fun onMessage(s: String) {
        //Got a message from websocket
        webSocketListener?.onMessage(JSONObject(s))
    }

    private fun onError(exception: Exception) {
        //Got an exception
        Log.e(TAG, "Websocket onError called with ${exception.message}")
        webSocketListener?.onError(exception)
    }

    private fun onClose(code: Int, reason: String, remote: Boolean) {
        //Websocket closed
        Log.e(TAG, "Web socket closed. Code: $code, Reason: $reason, Remote: $remote")
        webSocketListener?.onClose()
    }


}