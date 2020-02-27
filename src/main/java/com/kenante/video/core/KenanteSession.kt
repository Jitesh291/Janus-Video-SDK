package com.kenante.video.core

import android.os.Handler
import com.example.kenante_janus.enums.UserRoles
import com.kenante.video.enums.*
import com.kenante.video.interfaces.*
import com.varenia.kenante_core.core.KenanteSettings
import com.varenia.kenante_core.interfaces.KenanteWsConnEventListener
import org.json.JSONObject
import java.lang.Exception
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

class KenanteSession : KenanteWsConnEventListener {

    private val TAG = KenanteSession::class.java.simpleName
    private var kInstance: KenanteSession? = null
    internal var webSocketConnected = false
    private var isSessionCreated = false
    internal var startSessionListener: SessionEventListener? = null
    internal var roomId = 0
    internal var audio = false
    internal var video = false
    internal var bitrate = KenanteBitrate.low
    internal var currentUserId = 0
    internal var sessionOngoing = false
    internal var handler = Handler(KenanteSettings.getInstance().getContext()?.mainLooper!!)

    private var previousSessionKillers = HashMap<BigInteger, BigInteger>()

    companion object {
        private var kInstance: KenanteSession? = null
        fun getInstance(): KenanteSession {
            if (kInstance == null) {
                kInstance = KenanteSession()
            }
            return kInstance!!
        }

        //Call variables
        internal var sessionId = BigInteger.valueOf(0)
        internal var handleId = BigInteger.valueOf(0)
        internal var pluginHandles = ConcurrentHashMap<BigInteger, KenantePluginHandler>()
    }

    // Open web socket connection if it is not already connected.
    private fun openWebSocket() {
        if (!webSocketConnected) {
            KenanteVideoWebSocket.setListener(this)
            KenanteVideoWebSocket.connect()
        }
    }

    //This method creates a session with Janus.
    fun createSession(currentUserId: Int, sessionEventListener: SessionEventListener) {
        //Release old session
        if (sessionOngoing)
            releaseSession()
        this.currentUserId = currentUserId
        startSessionListener = sessionEventListener
        if (!webSocketConnected)
            openWebSocket()
        else {
            handler.post {
                startSessionListener!!.onSuccess("Session already exists")
            }
        }
    }

    //This method start conference call
    fun startCall(roomId: Int, audio: Boolean, video: Boolean, bitrate: KenanteBitrate) {
        this.roomId = roomId
        this.audio = audio
        this.video = video
        this.bitrate = bitrate
        for ((_, value) in pluginHandles) {
            if (value.id == currentUserId) {
                val role = if (value.isPublisher!!) {
                    UserRoles.publisher
                } else {
                    UserRoles.subscriber
                }
                KenanteWsSendMessages.joinRoom(
                        roomId,
                        sessionId,
                        value.handleId,
                        value.display!!,
                        role,
                        currentUserId,
                        null,
                        null
                )
            }
        }
    }

    fun configureUser(
            userId: Int,
            audio: Boolean,
            video: Boolean,
            bitrate: KenanteBitrate
    ) {
        KenanteUsers.setUserCallParameters(userId, audio, video, bitrate)
    }

    fun leave() {
        if(sessionOngoing) {
            KenanteWsSendMessages.leave(sessionId, handleId)
            releaseSession()
        }
        else{
            KenanteSendCallbacks.getInstance().sendSessionEventCallbacks(KenanteSessionEvent.close)
        }
    }

    fun leaveOldSession() {
        for((sessionId, handleId) in previousSessionKillers) {
            KenanteWsSendMessages.leave(sessionId, handleId)
            previousSessionKillers.remove(sessionId)
        }
    }

    //This method subscribes to available publishers joining the call
    fun subscribeToPublisher(userId: Int) {
        KenanteWsSendMessages.attachJanusPlugin(sessionId, false, userId)
    }

    //Register user call event listeners
    fun registerUserCallEventListener(listener: UserCallEventListener) {
        KenanteSendCallbacks.getInstance().registerUserCallEventListener(listener)
    }

    //Unregister user call event listeners
    fun unregisterUserCallEventListener(listener: UserCallEventListener) {
        KenanteSendCallbacks.getInstance().unregisterUserCallEventListener(listener)
    }

    //Register media stream event listeners
    fun registerMediaStreamEventListener(listener: KenanteMediaStreamEventListener) {
        KenanteSendCallbacks.getInstance().registerMediaStreamEventListener(listener)
    }

    //Unregister media stream event listeners
    fun unregisterMediaStreamEventListener(listener: KenanteMediaStreamEventListener) {
        KenanteSendCallbacks.getInstance().unregisterMediaStreamEventListener(listener)
    }

    //Register session event listeners
    fun registerSessionEventListeners(listener: KenanteSessionEventListener) {
        KenanteSendCallbacks.getInstance().registerSessionEventListener(listener)
    }

    //Unregister session event listeners
    fun unregisterSessionEventListeners(listener: KenanteSessionEventListener) {
        KenanteSendCallbacks.getInstance().unregisterSessionEventListener(listener)
    }

    override fun onOpen() {
        webSocketConnected = true
        KenanteMessageParser.setCallBackListener(KenanteMessageHandler)
        leaveOldSession()
        KenanteWsSendMessages.connectToJanus()
        sessionOngoing = true
    }

    internal fun notifyConnectedToJanus() {
        isSessionCreated = true
        handler.post {
            startSessionListener!!.onSuccess("New session started")
        }
    }

    override fun onMessage(obj: JSONObject) {
        KenanteMessageParser.handleMessage(obj)
    }

    override fun onError(ex: Exception) {
        handler.post {
            startSessionListener!!.onError("Kenante session could not be started")
        }
        //Session got interrupted
        previousSessionKillers[sessionId] = handleId
        releaseSession()
    }

    override fun onClose() {
        webSocketConnected = false
        isSessionCreated = false
        releaseSession()
        handler.post {
            startSessionListener!!.onError("Error occurred")
        }
    }

    override fun onDisconnected() {
        webSocketConnected = false
    }

    internal fun releaseSession() {
        isSessionCreated = false
        sessionOngoing = false
        sessionId = BigInteger.valueOf(0)
        handleId = BigInteger.valueOf(0)
        if (webSocketConnected)
            KenanteVideoWebSocket.disconnect()
        KenanteEglBase.releaseEglContext()
        for ((_, value) in pluginHandles) {
            value.releaseObjects()
        }
        pluginHandles.clear()
        for (each in KenanteUsers.liveUsers) {
            KenanteSendCallbacks.getInstance()
                    .sendUserCallEventCB(UserCallEvent.connection_closed, each)
        }
        KenanteUsers.liveUsers.clear()
        KenanteMediaStreamManager.GetManager().clearAllObjects()
        KenanteSendCallbacks.getInstance().sendSessionEventCallbacks(KenanteSessionEvent.close)
    }

}