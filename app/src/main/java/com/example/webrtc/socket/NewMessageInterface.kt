package com.example.webrtc.socket

import com.example.webrtc.model.MessageModel

interface NewMessageInterface {
    fun onNewMessage(message: MessageModel)
}