package com.kenante.video.core

import android.os.Handler
import android.os.Looper
import com.kenante.video.enums.KenanteSessionEvent
import com.kenante.video.enums.MediaEvent
import com.kenante.video.enums.MediaType
import com.kenante.video.enums.UserCallEvent
import com.kenante.video.interfaces.KenanteMediaStreamEventListener
import com.kenante.video.interfaces.KenanteSessionEventListener
import com.kenante.video.interfaces.UserCallEventListener
import com.kenante.video.media.KenanteAudioTrack
import com.kenante.video.media.KenanteVideoTrack
import com.varenia.kenante_core.core.KenanteSettings

class KenanteSendCallbacks {

    private var userCallEventListeners: ArrayList<UserCallEventListener> = ArrayList()
    private var mediaStreamEventListeners: ArrayList<KenanteMediaStreamEventListener> = ArrayList()
    private var sessionEventListeners: ArrayList<KenanteSessionEventListener> = ArrayList()
    internal var handler = Handler(KenanteSettings.getInstance().getContext()!!.mainLooper)

    companion object {
        private var instance = KenanteSendCallbacks()
        fun getInstance(): KenanteSendCallbacks {
            return instance
        }
    }

    fun registerUserCallEventListener(listener: UserCallEventListener) {
        if (!userCallEventListeners.contains(listener)) {
            userCallEventListeners.add(listener)
        }
    }

    fun unregisterUserCallEventListener(listener: UserCallEventListener) {
        if (userCallEventListeners.contains(listener)) {
            userCallEventListeners.remove(listener)
        }
    }

    fun registerMediaStreamEventListener(listener: KenanteMediaStreamEventListener) {
        if (!mediaStreamEventListeners.contains(listener)) {
            mediaStreamEventListeners.add(listener)
        }
    }

    fun unregisterMediaStreamEventListener(listener: KenanteMediaStreamEventListener) {
        if (mediaStreamEventListeners.contains(listener)) {
            mediaStreamEventListeners.remove(listener)
        }
    }

    fun registerSessionEventListener(listener: KenanteSessionEventListener) {
        if (!sessionEventListeners.contains(listener)) {
            sessionEventListeners.add(listener)
        }
    }

    fun unregisterSessionEventListener(listener: KenanteSessionEventListener) {
        if (sessionEventListeners.contains(listener)) {
            sessionEventListeners.remove(listener)
        }
    }

    fun sendUserCallEventCB(event: UserCallEvent, userId: Int) {
        handler.post {
            when (event) {
                UserCallEvent.available -> {
                    for (each in userCallEventListeners) {
                        each.onUserAvailable(userId)
                    }
                }
                UserCallEvent.connected -> {
                    for (each in userCallEventListeners) {
                        each.onUserConnectedToCall(userId)
                    }
                }
                UserCallEvent.disconnected -> {
                    for (each in userCallEventListeners) {
                        each.onUserDisconnectedFromCall(userId)
                    }
                }
                UserCallEvent.connection_closed -> {
                    for (each in userCallEventListeners) {
                        each.onUserConnectionClosed(userId)
                    }
                }
            }
        }
    }

    fun sendMediaStreamCallEventCB(
            event: MediaEvent,
            audioTrack: KenanteAudioTrack?,
            videoTrack: KenanteVideoTrack?
    ) {
        handler.post {
            when (event) {
                MediaEvent.localaudio -> {
                    for (each in mediaStreamEventListeners) {
                        each.onLocalAudioStream(audioTrack!!)
                    }
                }
                MediaEvent.localvideo -> {
                    for (each in mediaStreamEventListeners) {
                        KenanteSettings.getInstance().getContext()?.mainLooper.run {
                            each.onLocalVideoStream(videoTrack!!)
                        }
                    }

                }
                MediaEvent.remoteaudio -> {
                    for (each in mediaStreamEventListeners) {
                        each.onRemoteAudioStream(audioTrack!!)
                    }
                }
                MediaEvent.remotevideo -> {
                    for (each in mediaStreamEventListeners) {
                        each.onRemoteVideoStream(videoTrack!!)
                    }
                }
                else -> {

                }
            }
        }
    }

    fun sendMediaStreamMediaFlowCallEventCB(event: MediaEvent, userId: Int, mediaType: MediaType) {
        handler.post {
            when (event) {
                MediaEvent.mediastarted -> {
                    for (each in mediaStreamEventListeners) {
                        each.onMediaStartedFlowing(userId, mediaType)
                    }
                }
                MediaEvent.mediastopped -> {
                    for (each in mediaStreamEventListeners) {
                        each.onMediaStoppedFlowing(userId, mediaType)
                    }
                }
                else -> {

                }
            }
        }
    }

    fun sendSessionEventCallbacks(event: KenanteSessionEvent) {
        handler.post {
            when (event) {
                KenanteSessionEvent.close -> {
                    for (each in sessionEventListeners) {
                        each.onSessionClosed()
                    }
                }
            }
        }
    }

}