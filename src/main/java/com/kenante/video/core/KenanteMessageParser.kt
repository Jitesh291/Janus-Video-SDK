package com.kenante.video.core

import android.util.Log
import com.example.kenante_janus.enums.KenanteMessageType
import com.kenante.video.enums.MediaType
import com.kenante.video.enums.VideoRoomResponse
import com.kenante.video.interfaces.KenanteCallEventListener
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger

object KenanteMessageParser {

    //val TAG = KenanteMessageParser::class.java.simpleName
    var TAG = "KenanteDebug"
    var listener: KenanteCallEventListener? = null

    fun setCallBackListener(janusConnectionCallBackListener: KenanteCallEventListener) {
        listener = janusConnectionCallBackListener
    }

    fun handleMessage(obj: JSONObject) {
        Log.e(TAG, obj.toString())
        if (obj.has("janus")) {
            val janusMessage = obj.getString("janus")
            when (janusMessage) {
                KenanteMessageType.success.toString() -> {

                    when {
                        obj.getString("transaction") == KenanteTransactions.getCreateTransaction() -> listener!!.onSessionCreated(
                            obj.getJSONObject("data").getLong("id").toBigInteger()
                        )
                        obj.getString("transaction") == KenanteTransactions.getPublisherAttachTransaction() -> {
                            val handleId = obj.getJSONObject("data").getLong("id").toBigInteger()
                            listener!!.onPluginAttached(
                                handleId,
                                true,
                                KenanteSession.getInstance().currentUserId
                            )
                        }
                        KenanteTransactions.isSubscriberAttachTransaction(obj.getString("transaction")) -> {
                            val subscriberId = KenanteTransactions.getSubscriberId(obj.getString("transaction"))
                            if(subscriberId != null) {
                                val handleId =
                                    obj.getJSONObject("data").getLong("id").toBigInteger()
                                listener!!.onPluginAttached(
                                    handleId,
                                    false,
                                    subscriberId
                                )
                            }
                        }
                    }

                }
                KenanteMessageType.event.toString() -> {

                    if (obj.has("plugindata")) {
                        val pluginData = obj.getJSONObject("plugindata")
                        if (pluginData.has("data")) {
                            val senderId = obj.getLong("sender").toBigInteger()
                            var jsep = JSONObject()
                            if (obj.has("jsep")) {
                                jsep = obj.getJSONObject("jsep")
                            }
                            handleVideoRoomMessages(
                                pluginData.getJSONObject("data"),
                                jsep,
                                senderId
                            )
                        }
                    }

                }
                KenanteMessageType.ack.toString() -> {
                    Log.e(TAG, "Ack Acknowledged")
                }
                KenanteMessageType.error.toString() -> {

                }
                KenanteMessageType.timeout.toString() -> {

                }
                KenanteMessageType.detached.toString() -> {

                }
                KenanteMessageType.webrtcup.toString() -> {
                    val handleId = obj.getLong("sender").toBigInteger()
                    listener!!.onWebrtcUp(handleId)
                }
                KenanteMessageType.media.toString() -> {
                    val handleId = obj.getLong("sender").toBigInteger()
                    val type = obj.getString("type")
                    val receiving = obj.getBoolean("receiving")
                    val mediaType = if (type == "video") {
                        MediaType.video
                    } else {
                        MediaType.audio
                    }
                    listener!!.onMedia(handleId, mediaType, receiving)
                }
                KenanteMessageType.hangup.toString() -> {

                }
                KenanteMessageType.trickle.toString() -> {
                    if(obj.has("sender")){
                        if(obj.has("candidate")){
                            val candidate = obj.getJSONObject("candidate")
                            val handleId = obj.getLong("sender").toBigInteger()
                            listener!!.onTrickleReceived(handleId, candidate)
                        }
                    }
                }
            }
        }
    }

    fun handleVideoRoomMessages(data: JSONObject, jsep: JSONObject, handleId: BigInteger) {
        if (data.has("videoroom")) {
            when (data.getString("videoroom")) {
                KenanteMessageType.event.toString() -> {

                    if (data.has(VideoRoomResponse.error.toString())) {
                        val errorCode = data.getInt("error_code")
                        val errorMessage = data.getString("error")
                        if(errorCode == 436){
                            //User already exists
                            KenanteWsSendMessages.leave(KenanteSession.sessionId, KenanteSession.handleId)
                            KenanteSession.getInstance().handler.post {
                                KenanteSession.getInstance().startSessionListener?.onError(errorMessage)
                            }
                        }
                        return
                    }

                    when {
                        data.has(VideoRoomResponse.joining.toString()) -> {

                        }
                        data.has(VideoRoomResponse.configured.toString()) -> {

                            if (data.getString("configured") == "ok") {
                                listener!!.onConfigured(handleId, jsep)
                            }

                        }
                        data.has(VideoRoomResponse.unpublished.toString()) -> {
                            listener!!.onUnpublished(data.getInt("unpublished"))
                        }
                        data.has(VideoRoomResponse.leaving.toString()) -> {
                            val leavingVal = data.get("leaving")
                            if(leavingVal == "ok"){
                                listener!!.onLeaving(KenanteSession.getInstance().currentUserId)
                            }
                            else {
                                listener!!.onLeaving(data.getInt("leaving"))
                            }
                        }
                        data.has(VideoRoomResponse.publishers.toString()) -> {

                            val publishers: JSONArray = data.getJSONArray("publishers")
                            for (i in 0 until publishers.length()) {
                                val each = publishers.get(i) as JSONObject
                                val pubId = each.getInt("id")
                                val display = each.getString("display")
                                val audioCodec = each.getString("audio_codec")
                                val videoCodec = each.getString("video_codec")
                                val talking = each.getBoolean("talking")
                                listener!!.onOtherPublisherAvailable(
                                    pubId,
                                    display,
                                    audioCodec,
                                    videoCodec,
                                    talking
                                )
                            }

                        }
                        data.has(VideoRoomResponse.started.toString()) -> {
                            listener!!.onStarted(handleId)
                        }
                    }

                }
                KenanteMessageType.joined.toString() -> {

                    listener!!.onPublisherJoined(handleId)

                    val publishers = data.getJSONArray("publishers")
                    for (i in 0 until publishers.length()) {
                        val each = publishers.get(i) as JSONObject
                        val pubId = each.getInt("id")
                        val display = each.getString("display")
                        val audioCodec = each.getString("audio_codec")
                        val videoCodec = each.getString("video_codec")
                        val talking = each.getBoolean("talking")
                        listener!!.onOtherPublisherAvailable(
                            pubId,
                            display,
                            audioCodec,
                            videoCodec,
                            talking
                        )
                    }

                }
                KenanteMessageType.attached.toString() -> {

                    listener!!.onSubscriberAttached(handleId, jsep)

                }
            }
        }
    }

}