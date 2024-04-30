package com.example.webrtc


import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.webrtc.model.IceCandidateModel
import com.example.webrtc.model.MessageModel
import com.example.webrtc.rtc.PeerConnectionObserver
import com.example.webrtc.rtc.RTCClient
import com.example.webrtc.rtc.TAG
import com.example.webrtc.socket.NewMessageInterface
import com.example.webrtc.socket.SocketRepository
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject


@SuppressLint("StaticFieldLeak")
@HiltViewModel
class MainViewModel @Inject constructor(
    private val application: Application,
    private val socketRepository: SocketRepository
) : ViewModel(), NewMessageInterface {


    private var localViewRenderer: SurfaceViewRenderer? = null
    private var remoteViewRenderer: SurfaceViewRenderer? = null

    override fun onCleared() {
        localViewRenderer = null
        remoteViewRenderer = null
        super.onCleared()
    }

    private val gson = Gson()
    private var rtcClient: RTCClient? = null
    private var userName: String? = null
    private var target: String = ""

    //state sections
    val incomingCallerSection: MutableStateFlow<MessageModel?> = MutableStateFlow(null)

    fun init(userName: String) {
        this.userName = userName

        socketRepository.initSocket(userName, this)

        rtcClient = RTCClient(application,
            userName,
            socketRepository,
            object : PeerConnectionObserver() {
                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)
                    rtcClient?.addIceCandidate(p0)
                    val candidate = hashMapOf(
                        "sdpMid" to p0?.sdpMid,
                        "sdpMLineIndex" to p0?.sdpMLineIndex,
                        "sdpCandidate" to p0?.sdp
                    )

                    socketRepository.sendMessageToSocket(
                        MessageModel("ice_candidate", userName, target, candidate)
                    )

                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    super.onConnectionChange(newState)
                    Log.d("TAG", "onConnectionChange: $newState")
                    viewModelScope.launch {
                        if (newState == PeerConnection.PeerConnectionState.CLOSED||
                            newState == PeerConnection.PeerConnectionState.DISCONNECTED){
                            incomingCallerSection.emit(null)
                            remoteViewRenderer?.isVisible = false
                        }
                    }
                }

                override fun onAddStream(p0: MediaStream?) {
                    super.onAddStream(p0)
                    p0?.videoTracks?.get(0)?.addSink(remoteViewRenderer)
                }
            })
        rtcClient?.initializeSurfaceView(localViewRenderer!!)
        rtcClient?.startLocalVideo(localViewRenderer!!)
        rtcClient?.initializeSurfaceView(remoteViewRenderer!!)
    }

    fun setLocalView(surfaceViewRenderer: SurfaceViewRenderer) {
        this.localViewRenderer = surfaceViewRenderer

    }

    fun setRemoteView(surfaceViewRenderer: SurfaceViewRenderer) {
        this.remoteViewRenderer = surfaceViewRenderer
    }

    fun startCall(target: String) {
        this.target = target
        socketRepository.sendMessageToSocket(
            MessageModel(
                "start_call", userName, target, null
            )
        )

    }

    fun acceptCall() {

        val session = SessionDescription(
            SessionDescription.Type.OFFER,
            incomingCallerSection.value?.data.toString()
        )
        Log.d(TAG, "Accept call $session")
        rtcClient?.onRemoteSessionReceived(session)
        rtcClient?.answer(incomingCallerSection.value?.name!!)
        target = incomingCallerSection.value?.name!!
        viewModelScope.launch {
            incomingCallerSection.emit(null)
            Log.d("aaa",incomingCallerSection.value.toString())
        }
    }

    fun rejectCall() {
        viewModelScope.launch {
            incomingCallerSection.emit(null)
        }
    }

    fun onAudioButtonClicked(b: Boolean) {
        rtcClient?.toggleAudio(b)
    }

    fun onCameraButtonClicked(b: Boolean) {
        rtcClient?.toggleCamera(b)
    }

    fun onEndCallClicked() {
        rtcClient?.endCall()
        remoteViewRenderer?.release()
        remoteViewRenderer?.clearImage()
        remoteViewRenderer?.isVisible = false
    }

    fun onSwitchCameraClicked() {
        rtcClient?.switchCamera()
    }

    override fun onNewMessage(message: MessageModel) {
        CoroutineScope(Dispatchers.Main).launch {
            Log.d("aksh", "onConnectionChange: $message")

            when (message.type) {
                "call_response" -> {
                    if (message.data == "user is not online") {
                        //user is not reachable
                        Toast.makeText(application, "user is not reachable", Toast.LENGTH_LONG)
                            .show()
                    } else {
                        //we are ready for call, we started a call
                        rtcClient?.call(target)
                    }
                }

                "answer_received" -> {
                    remoteViewRenderer?.isVisible = true

                    val session = SessionDescription(
                        SessionDescription.Type.ANSWER,
                        message.data.toString()
                    )
                    rtcClient?.onRemoteSessionReceived(session)
                }

                "offer_received" -> {
                    remoteViewRenderer?.isVisible = true
                    viewModelScope.launch {
                        incomingCallerSection.emit(message)
                    }
                }

                "ice_candidate" -> {
                    try {
                        val receivingCandidate = gson.fromJson(
                            gson.toJson(message.data),
                            IceCandidateModel::class.java
                        )
                        rtcClient?.addIceCandidate(
                            IceCandidate(
                                receivingCandidate.sdpMid,
                                Math.toIntExact(receivingCandidate.sdpMLineIndex.toLong()),
                                receivingCandidate.sdpCandidate
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

        }
    }


}