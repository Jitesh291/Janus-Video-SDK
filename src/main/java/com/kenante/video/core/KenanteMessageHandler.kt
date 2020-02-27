package com.kenante.video.core

import android.util.Log
import com.example.kenante_janus.enums.UserRoles
import com.kenante.video.enums.*
import com.kenante.video.helper.Constants
import com.kenante.video.helper.KHelper
import com.kenante.video.interfaces.KenanteCallEventListener
import org.json.JSONObject
import org.webrtc.IceCandidate
import java.math.BigInteger

object KenanteMessageHandler : KenanteCallEventListener {

    //private val TAG = KenanteMessageHandler::class.java.simpleName
    var TAG = "KenanteDebug"

    override fun onSessionCreated(sessionId: BigInteger) {
        KenanteSession.sessionId = sessionId
        Log.e(TAG, "onSessionCreated called")
        Log.e(TAG, "Session id: $sessionId")
        KHelper.startKeepAlive()
        KenanteWsSendMessages.attachJanusPlugin(sessionId, true, 0)
    }

    override fun onPluginAttached(handleId: BigInteger, isPublisher: Boolean, userId: Int) {
        Log.e(TAG, "onPluginAttached called. isPublisher: $isPublisher")
        createPluginHandler(handleId, isPublisher, userId)
    }

    override fun onPublisherJoined(handleId: BigInteger) {
        //Initialize this publisher's WebRTC
        val pluginHandle = KenanteSession.pluginHandles[handleId]
        pluginHandle?.startWebRTCStuff()

        //Notify classes that publisher joined
        KenanteSendCallbacks.getInstance()
                .sendUserCallEventCB(UserCallEvent.connected, pluginHandle!!.id)

        KenanteUsers.liveUsers.add(KenanteSession.pluginHandles[handleId]!!.id)
    }

    override fun onTrickleReceived(handleId: BigInteger, candidate: JSONObject) {
        val pluginHandle = KenanteSession.pluginHandles[handleId]

        var iceCandidate: IceCandidate? = null

        if (!candidate.has("completed")) {
            iceCandidate = IceCandidate(
                    candidate["sdpMid"].toString(),
                    candidate["sdpMLineIndex"].toString().toInt(),
                    candidate["candidate"].toString()
            )
        }

        //Convert json object to ice candidate
        if (pluginHandle?.isRemoteDescriptionSet!!) {
            pluginHandle.addTrickleCandidate(candidate = iceCandidate)
        } else {
            pluginHandle.cacheIceCandidates(iceCandidate)
        }
    }

    override fun onConfigured(handleId: BigInteger, jsep: JSONObject?) {
        Log.e(TAG, "onConfigured called with jsep")
        KenanteSession.pluginHandles[handleId]!!.setRemoteDescription(jsep!!, false)
    }

    override fun onWebrtcUp(handleId: BigInteger) {
        val pluginHandle = KenanteSession.pluginHandles[handleId]
        pluginHandle?.startSendingMediaStream()
    }

    override fun onMedia(handleId: BigInteger, type: MediaType, flowing: Boolean) {
        val pluginHandle = KenanteSession.pluginHandles[handleId]
        if (flowing) {
            KenanteSendCallbacks.getInstance().sendMediaStreamMediaFlowCallEventCB(
                    MediaEvent.mediastarted,
                    pluginHandle!!.id,
                    type
            )
        } else {
            KenanteSendCallbacks.getInstance().sendMediaStreamMediaFlowCallEventCB(
                    MediaEvent.mediastopped,
                    pluginHandle!!.id,
                    type
            )
        }
    }

    override fun onPublisherHangUp(handleId: BigInteger) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onOtherPublisherAvailable(
            id: Int,
            display: String,
            audioCodec: String,
            videoCodec: String,
            talking: Boolean
    ) {
        KenanteSendCallbacks.getInstance().sendUserCallEventCB(UserCallEvent.available, id)
    }

    override fun onSubscriberAttached(handleId: BigInteger, jsep: JSONObject?) {

        val pluginHandle = KenanteSession.pluginHandles[handleId]
        KenanteSendCallbacks.getInstance()
                .sendUserCallEventCB(UserCallEvent.connected, pluginHandle!!.id)

        KenanteUsers.liveUsers.add(KenanteSession.pluginHandles[handleId]!!.id)

        pluginHandle.initSubscriberPeerConnection()
        if (jsep != null) {
            pluginHandle.setRemoteDescription(jsep, true)
        }

        Log.e(TAG, "onSubscriberAttached called with userId: ${pluginHandle.id}")
    }

    override fun onStarted(handleId: BigInteger) {
        val pluginHandle = KenanteSession.pluginHandles[handleId]
        Log.e(TAG, "onStarted called with userId: ${pluginHandle?.id}")
    }

    override fun onUnpublished(userId: Int) {
        Log.e(TAG, "onUnpublished called with userId: $userId")
        for ((key, value) in KenanteSession.pluginHandles) {
            if (value.id == userId) {
                KenanteSendCallbacks.getInstance()
                        .sendUserCallEventCB(UserCallEvent.disconnected, userId)
            }
        }
    }

    override fun onLeaving(userId: Int) {
        for ((key, value) in KenanteSession.pluginHandles) {
            if (value.id == userId) {
                KenanteSendCallbacks.getInstance()
                        .sendUserCallEventCB(UserCallEvent.connection_closed, userId)
                //if (value.id == KenanteSession.getInstance().currentUserId)
                value.releaseObjects()
                KenanteMediaStreamManager.GetManager().removeVideoTrack(userId)
                KenanteMediaStreamManager.GetManager().removeAudioTrack(userId)
                KenanteSession.pluginHandles.remove(value.handleId)
                if (KenanteUsers.liveUsers.contains(value.id))
                    KenanteUsers.liveUsers.remove(value.id)
                break
            }
        }
        if (userId == KenanteSession.getInstance().currentUserId) {
            KenanteSession.getInstance().releaseSession()
        }
        Log.e(TAG, "onLeaving called with userId: $userId")
    }

    private fun createPluginHandler(handleId: BigInteger, isPublisher: Boolean, userId: Int) {
        if (isPublisher) {
            KenanteSession.handleId = handleId
            val kenantePluginHandler = KenantePluginHandler()
            kenantePluginHandler.handleId = handleId
            kenantePluginHandler.id = userId
            kenantePluginHandler.audioCodec = KenanteAudioCodec.opus
            kenantePluginHandler.videoCodec = KenanteVideoCodec.vp8
            kenantePluginHandler.bitrate = KenanteSession.getInstance().bitrate
            kenantePluginHandler.audio = KenanteSession.getInstance().audio
            kenantePluginHandler.video = KenanteSession.getInstance().video
            kenantePluginHandler.isPublisher = true

            KenanteUsers.setUsersContainer(userId)

            // Adding this plugin handler
            KenanteSession.pluginHandles[handleId] = kenantePluginHandler

            Log.e(TAG, "Plugin with id: $handleId added")

            KenanteSession.getInstance().notifyConnectedToJanus()


        } else {
            val kenantePluginHandler = KenantePluginHandler()
            kenantePluginHandler.handleId = handleId
            kenantePluginHandler.id = userId
            kenantePluginHandler.audioCodec = KenanteAudioCodec.opus
            kenantePluginHandler.videoCodec = KenanteVideoCodec.vp8
            // Default values
            var bitrate = KenanteBitrate.low
            var audio = true
            var video = true
            val user = KenanteUsers.getUser(userId)
            if(user != null){
                bitrate = user.bitrate
                audio = user.audio
                video = user.video
            }
            kenantePluginHandler.bitrate = bitrate
            kenantePluginHandler.audio = audio
            kenantePluginHandler.video = video
            kenantePluginHandler.isPublisher = false

            KenanteUsers.setUsersContainer(userId)

            // Adding this plugin handler
            KenanteSession.pluginHandles[handleId] = kenantePluginHandler

            Log.e(TAG, "Plugin with id: $handleId added")
            joinSubscriber(kenantePluginHandler)
        }
    }

    private fun joinSubscriber(pluginHandle: KenantePluginHandler) {

        KenanteWsSendMessages.joinRoom(
                KenanteSession.getInstance().roomId,
                KenanteSession.sessionId,
                pluginHandle.handleId,
                pluginHandle.display!!,
                UserRoles.subscriber,
                pluginHandle.id,
                pluginHandle.audio,
                pluginHandle.video
        )
    }

}