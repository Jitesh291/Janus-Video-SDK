package com.kenante.video.interfaces

import com.kenante.video.enums.MediaType
import org.json.JSONObject
import java.math.BigInteger

interface KenanteCallEventListener {

    fun onSessionCreated(sessionId: BigInteger)
    fun onPluginAttached(handleId: BigInteger, isPublisher: Boolean, userId: Int)
    fun onPublisherJoined(handleId: BigInteger)
    fun onTrickleReceived(handleId: BigInteger, candidate: JSONObject)
    fun onConfigured(handleId: BigInteger, jsep: JSONObject?)
    fun onWebrtcUp(handleId: BigInteger)
    fun onMedia(handleId: BigInteger, type: MediaType, flowing: Boolean)
    fun onPublisherHangUp(handleId: BigInteger)
    fun onOtherPublisherAvailable(
        id: Int,
        display: String,
        audioCodec: String,
        videoCodec: String,
        talking: Boolean
    )

    fun onSubscriberAttached(handleId: BigInteger, jsep: JSONObject?)
    fun onStarted(handleId: BigInteger)
    fun onUnpublished(userId: Int)
    fun onLeaving(userId: Int)

}