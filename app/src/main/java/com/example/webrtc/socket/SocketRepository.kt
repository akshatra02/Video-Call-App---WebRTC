package com.example.webrtc.socket

import android.util.Log
import com.example.webrtc.model.MessageModel
import com.google.gson.Gson
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketRepository @Inject constructor() {
    private var webSocket: WebSocketClient? = null
    private var userName: String? = null
    private val TAG = "SocketRepository"
    private val gson = Gson()

    fun initSocket(username: String, messageInterface: NewMessageInterface) {
        userName = username

        webSocket = object : WebSocketClient(URI("ws://10.0.2.2:3000")) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                sendMessageToSocket(
                    MessageModel(
                        "store_user", username, null, null
                    )
                )
            }

            override fun onMessage(message: String?) {
                try {
                    Log.d(com.example.webrtc.rtc.TAG, "onMessage-=-=")

                    messageInterface.onNewMessage(gson.fromJson(message, MessageModel::class.java))
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.d(TAG, "onClose: $reason")
            }

            override fun onError(ex: Exception?) {
                Log.d(TAG, "onError: $ex")
            }

        }
        webSocket?.connect()

    }

    fun sendMessageToSocket(message: MessageModel) {
        try {
            webSocket?.send(Gson().toJson(message))
        } catch (e: Exception) {
            Log.d(TAG, "sendMessageToSocket: $e")
        }
    }
}