package com.kenante.video.core

import android.util.Log
import com.example.kenante_janus.enums.KenanteMessageType
import com.example.kenante_janus.enums.UserRoles
import com.kenante.video.enums.KenanteAudioCodec
import com.kenante.video.enums.KenanteBitrate
import com.kenante.video.enums.KenanteVideoCodec
import com.kenante.video.helper.KHelper
import com.varenia.kenante_core.core.KenanteSettings
import org.json.JSONObject
import java.math.BigInteger

object KenanteWsSendMessages {

    val TAG = KenanteWsSendMessages::class.java.simpleName

    fun connectToJanus() {

        val transactionId = KHelper.GenerateTransactionId()
        KenanteTransactions.setCreateTransaction(transactionId)

        val connect = JSONObject()
        connect.put("janus", KenanteMessageType.create)
        connect.put("transaction", transactionId)

        val server = KenanteVideoWebSocket.getInstance()
        server.sendMessage(connect.toString())

        Log.e(TAG, connect.toString())
    }

    fun sendKeepAlive(session_id: BigInteger) {
        val keepalive = JSONObject()
        keepalive.put("janus", KenanteMessageType.keepalive)
        keepalive.put("transaction", KHelper.GenerateTransactionId())
        keepalive.put("session_id", session_id)

        val server = KenanteVideoWebSocket.getInstance()
        server.sendMessage(keepalive.toString())
    }

    fun attachJanusPlugin(session_id: BigInteger, isPublisher: Boolean, userId: Int) {

        val transactionId = KHelper.GenerateTransactionId()
        if (isPublisher)
            KenanteTransactions.setPublisherAttachTransaction(transactionId)
        else
            KenanteTransactions.addSubscriberAttachTransaction(transactionId, userId)


        val attach = JSONObject()
        attach.put("janus", KenanteMessageType.attach)
        //Check if plugin key can have multiple values for registering with multiple plugins
        attach.put("plugin", KenanteSettings.getInstance().getPlugin())
        attach.put("session_id", session_id)
        attach.put("transaction", transactionId)

        val server = KenanteVideoWebSocket.getInstance()
        server.sendMessage(attach.toString())

        Log.e(TAG, server.toString())

    }

    fun createARoom(sessionId: BigInteger, handleId: BigInteger) {
        val newRoom = JSONObject()

        val json = JSONObject()
        json.put("request", KenanteMessageType.create)
        json.put("room", KenanteSession.getInstance().roomId)
        json.put("permanent", false)
        json.put("description", "A testing room")
        json.put("is_private", false)
        json.put("audiocodec", "opus")
        json.put("videocodec", "vp8")
        json.put("bitrate", 128000)
        json.put("notify_joining", true)
        /*json.put("record", false)
        json.put("rec_dir", "/home/varenia")*/

        newRoom.put("janus", KenanteMessageType.message)
        newRoom.put("body", json)
        newRoom.put("transaction", KHelper.GenerateTransactionId())
        newRoom.put("session_id", sessionId)
        newRoom.put("handle_id", handleId)

        val server = KenanteVideoWebSocket.getInstance()
        server.sendMessage(newRoom.toString())
    }

    fun joinRoom(
        roomId: Int,
        sessionId: BigInteger,
        handleId: BigInteger,
        display: String,
        userRole: UserRoles,
        id: Int?,
        audio: Boolean?,
        video: Boolean?
    ) {
        val joinRoom = JSONObject()

        val json = JSONObject()
        json.put("request", KenanteMessageType.join)
        json.put("room", roomId)
        json.put("ptype", userRole)
        json.put("display", display)
        if (userRole == UserRoles.subscriber) {
            json.put("audio", audio)
            json.put("video", video)
            json.put("feed", id)
        } else {
            json.put("id", id)
        }

        joinRoom.put("janus", KenanteMessageType.message)
        joinRoom.put("body", json)

        val transactionId = KHelper.GenerateTransactionId()
        if (userRole == UserRoles.publisher)
            KenanteTransactions.setPublsherJoinRoomTransaction(transactionId)

        joinRoom.put("transaction", transactionId)
        joinRoom.put("session_id", sessionId)
        joinRoom.put("handle_id", handleId)

        val server = KenanteVideoWebSocket.getInstance()
        server.sendMessage(joinRoom.toString())
    }

    fun publishUser(
        sessionId: BigInteger,
        handleId: BigInteger,
        jsep: JSONObject,
        audio: Boolean,
        video: Boolean,
        audioCodec: KenanteAudioCodec,
        videoCodec: KenanteVideoCodec,
        bitrate: KenanteBitrate
    ) {
        val publish = JSONObject()

        val json = JSONObject()
        json.put("request", KenanteMessageType.publish)
        json.put("audio", audio)
        json.put("video", video)
        json.put("audiocodec", audioCodec)
        json.put("videocodec", videoCodec)
        json.put("bitrate", bitrate.bitrate)
        //json.put("keyframe", true)

        publish.put("janus", KenanteMessageType.message)
        publish.put("body", json)
        publish.put("jsep", jsep)
        publish.put("transaction", KHelper.GenerateTransactionId())
        publish.put("session_id", sessionId)
        publish.put("handle_id", handleId)

        val server = KenanteVideoWebSocket.getInstance()
        server.sendMessage(publish.toString())
    }

    fun sendIceCandidates(sessionId: BigInteger, handleId: BigInteger, iceCandidate: JSONObject) {
        val candidate = JSONObject()
        candidate.put("janus", KenanteMessageType.trickle)
        candidate.put("transaction", KHelper.GenerateTransactionId())
        candidate.put("candidate", iceCandidate)
        candidate.put("session_id", sessionId)
        candidate.put("handle_id", handleId)

        val server = KenanteVideoWebSocket.getInstance()
        server.sendMessage(candidate.toString())
    }

    fun startSubscriber(
        sessionId: BigInteger,
        handleId: BigInteger,
        jsep: JSONObject,
        room: Int
    ) {
        val ready = JSONObject()

        val json = JSONObject()
        json.put("request", KenanteMessageType.start)
        json.put("room", room)

        ready.put("janus", KenanteMessageType.message)
        ready.put("body", json)
        ready.put("jsep", jsep)
        ready.put("transaction", KHelper.GenerateTransactionId())
        ready.put("session_id", sessionId)
        ready.put("handle_id", handleId)

        val server = KenanteVideoWebSocket.getInstance()
        server.sendMessage(ready.toString())
    }

    fun configureUser(
        sessionId: BigInteger,
        handleId: BigInteger,
        jsep: JSONObject,
        audio: Boolean,
        video: Boolean
    ) {
        val publish = JSONObject()

        val json = JSONObject()
        json.put("request", KenanteMessageType.configure)
        json.put("audio", audio)
        json.put("video", video)
        json.put("audiocodec", "opus")
        json.put("videocodec", "vp8")
        //json.put("keyframe", true)

        publish.put("janus", KenanteMessageType.message)
        publish.put("body", json)
        publish.put("jsep", jsep)
        publish.put("transaction", KHelper.GenerateTransactionId())
        publish.put("session_id", sessionId)
        publish.put("handle_id", handleId)

        val server = KenanteVideoWebSocket.getInstance()
        server.sendMessage(publish.toString())
    }

    fun leave(sessionId: BigInteger,
              handleId: BigInteger) {

        val leave = JSONObject()

        val json = JSONObject()
        json.put("request", KenanteMessageType.leave)

        leave.put("janus", KenanteMessageType.message)
        leave.put("body", json)
        leave.put("transaction", KHelper.GenerateTransactionId())
        leave.put("session_id", sessionId)
        leave.put("handle_id", handleId)

        val server = KenanteVideoWebSocket.getInstance()
        server.sendMessage(leave.toString())
    }

}