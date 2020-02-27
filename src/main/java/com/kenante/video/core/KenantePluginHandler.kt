package com.kenante.video.core

import android.content.Context
import android.os.Handler
import android.util.Log
import com.example.kenante_janus.enums.KenanteMessageType
import com.kenante.video.enums.KenanteAudioCodec
import com.kenante.video.enums.KenanteBitrate
import com.kenante.video.enums.KenanteVideoCodec
import com.varenia.kenante_core.core.KenanteSettings
import org.json.JSONObject
import org.webrtc.*
import java.lang.Exception
import java.math.BigInteger


class KenantePluginHandler : CameraVideoCapturer.CameraEventsHandler {

    //TAGS
    //var TAG = KenantePluginHandler::class.java.simpleName
    var TAG = "KenanteDebug"

    var handleId = BigInteger.valueOf(0)

    var id = 0

    //Default values
    var audioCodec: KenanteAudioCodec? = null
    var videoCodec: KenanteVideoCodec? = null
    var bitrate: KenanteBitrate? = null
    var talking: Boolean? = null
    var isPublisher: Boolean? = null
    var video: Boolean? = null
    var audio: Boolean? = null
    var display: String? = ""

    //WebRTC Stuff
    var pc: PeerConnection? = null
    var localJsep = JSONObject()
    var remoteJsep = JSONObject()
    var trickle = true

    //Peer Connection Checks
    var onIceGatheringCompleted = false
    var cachedAnswerJsep: JSONObject? = null
    var isRemoteDescriptionSet = false
    var cachedIceCandidates = ArrayList<IceCandidate>()

    var iceConnectionClosed = false

    //Media
    private val mediaStreamManager = KenanteMediaStreamManager.GetManager()

    fun startWebRTCStuff() {
        Log.e(TAG, "init webrtc")
        initPeerConnection(KenanteSettings.getInstance().getContext()!!)
        Log.e(TAG, "init peer connection done")
        initLocalPeerConnection()
        Log.e(TAG, "init local peer connection done")
        /*initStreams()
        Log.e(TAG, "init streams done")
        addLocalStream()
        Log.e(TAG, "adding local streams")*/
        createStreams()
        Log.e(TAG, "streams created")
        createOffer()
        Log.e(TAG, "creating offer")
    }

    fun initSubscriberPeerConnection() {
        initPeerConnection(KenanteSettings.getInstance().getContext()!!)
        initLocalPeerConnection()
    }


    private fun initPeerConnection(context: Context) { 

        val builder = PeerConnectionFactory.InitializationOptions.builder(context)
        builder.setEnableInternalTracer(true)
        //builder.setFieldTrials("WebRTC-H264HighProfile/Enabled/")
        PeerConnectionFactory.initialize(builder.createInitializationOptions())

        val peerConnectionFactoryBuilder = PeerConnectionFactory
                .builder()
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(KenanteEglBase.GetEglBaseContext().eglBaseContext))
                .setVideoEncoderFactory(
                        DefaultVideoEncoderFactory(
                                KenanteEglBase.GetEglBaseContext().eglBaseContext,
                                true,
                                false
                        )
                )

        mediaStreamManager.peerConnectionFactory = peerConnectionFactoryBuilder.createPeerConnectionFactory()

    }

    fun initStreams() {

        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(
                MediaConstraints.KeyValuePair(
                        "googEchoCancellation",
                        "true"
                )
        )
        audioConstraints.mandatory.add(
                MediaConstraints.KeyValuePair(
                        "googEchoCancellation2",
                        "true"
                )
        )
        audioConstraints.mandatory.add(
                MediaConstraints.KeyValuePair(
                        "googDAEchoCancellation",
                        "true"
                )
        )

        audioConstraints.mandatory.add(
                MediaConstraints.KeyValuePair(
                        "googTypingNoiseDetection",
                        "true"
                )
        )

        audioConstraints.mandatory.add(
                MediaConstraints.KeyValuePair(
                        "googAutoGainControl",
                        "true"
                )
        )

        audioConstraints.mandatory.add(
                MediaConstraints.KeyValuePair(
                        "googAutoGainControl2",
                        "true"
                )
        )

        audioConstraints.mandatory.add(
                MediaConstraints.KeyValuePair(
                        "googNoiseSuppression",
                        "true"
                )
        )
        audioConstraints.mandatory.add(
                MediaConstraints.KeyValuePair(
                        "googNoiseSuppression2",
                        "true"
                )
        )

        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))

        //mediaStreamManager.videoSource = mediaStreamManager.peerConnectionFactory?.createVideoSource(false)
        //mediaStreamManager.localVideoTrack = mediaStreamManager.peerConnectionFactory?.createVideoTrack("100", mediaStreamManager.videoSource)

        mediaStreamManager.audioSource = mediaStreamManager.peerConnectionFactory?.createAudioSource(audioConstraints)
        mediaStreamManager.localAudioTrack  = mediaStreamManager.peerConnectionFactory?.createAudioTrack("101", mediaStreamManager.audioSource)

    }

    internal fun startSendingMediaStream() {
        if (!isPublisher!!)
            return
        if (video!!) {
            //KenanteMediaStreamManager.GetManager().setVideoCapturer(KenanteCamera.GetCamera())
        }
        if (audio!!) {

        }
    }

    private fun initLocalPeerConnection() {

        val iceServers = ArrayList<PeerConnection.IceServer>()
        val iceServer =
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        iceServers.add(iceServer)

        val rtcConfiguration = PeerConnection.RTCConfiguration(iceServers)
        rtcConfiguration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        //rtcConfiguration.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        //rtcConfiguration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        //rtcConfiguration.continualGatheringPolicy =
                PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
        //rtcConfiguration.keyType = PeerConnection.KeyType.ECDSA

        pc = mediaStreamManager.peerConnectionFactory?.createPeerConnection(
                rtcConfiguration,
                object : PeerConnection.Observer {
                    override fun onIceCandidate(p0: IceCandidate?) {
                        //Log.e(TAG, "local onIceCandidate called with $p0")
                        //localIceCandidates.add(p0!!)
                        Log.e(TAG, "candidate received: $p0")
                        //Send Candidates to janus server
                        sendTrickleCandidates(p0)
                    }

                    override fun onDataChannel(p0: DataChannel?) {
                        Log.e(TAG, "onDataChannel called with $p0")
                    }

                    override fun onIceConnectionReceivingChange(p0: Boolean) {
                        Log.e(TAG, "onIceConnectionReceivingChange called with $p0")
                    }

                    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                        Log.e(TAG, "onIceConnectionChange called with ${p0?.name}")
                        when (p0){
                            PeerConnection.IceConnectionState.CHECKING -> {
                                iceConnectionClosed = false
                            }
                            PeerConnection.IceConnectionState.CONNECTED -> {
                                iceConnectionClosed = false
                            }
                            PeerConnection.IceConnectionState.CLOSED -> {
                                iceConnectionClosed = true
                            }
                        }
                    }

                    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                        Log.e(TAG, "onIceGatheringChange called with ${p0?.name}")
                        when (p0) {
                            PeerConnection.IceGatheringState.NEW -> {

                            }
                            PeerConnection.IceGatheringState.GATHERING -> {

                            }
                            PeerConnection.IceGatheringState.COMPLETE -> {

                                if(iceConnectionClosed)
                                    return

                                /*onIceGatheringCompleted = true
                                if (cachedAnswerJsep != null) {
                                    setRemoteDescription(cachedAnswerJsep!!, false)
                                }*/
                                if (trickle)
                                    sendTrickleCandidates(null)
                            }
                        }
                    }

                    override fun onAddStream(p0: MediaStream?) {
                        Log.e(TAG, "onAddStream called with ${p0?.id}")
                        val audioTrack = p0?.audioTracks?.get(0)
                        val videoTrack = p0?.videoTracks?.get(0)

                        mediaStreamManager.setAudioTrack(id, audioTrack, isPublisher!!)
                        mediaStreamManager.setVideoTrack(id, videoTrack, isPublisher!!)
                    }

                    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
                        //Log.e(TAG, "local onSignalingChange called")
                        Log.e(TAG, "onSignalingChange call with ${p0?.name}")
                    }

                    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
                        Log.e(TAG, "onIceCandidatesRemoved called")
                    }

                    override fun onRemoveStream(p0: MediaStream?) {
                        //Log.e(TAG, "local onRemoveStream called")

                        mediaStreamManager.removeAudioTrack(id)
                        mediaStreamManager.removeVideoTrack(id)
                    }

                    override fun onRenegotiationNeeded() {
                        Log.e(TAG, "onRenegotiationNeeded called")
                    }

                    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                        Log.e(TAG, "onAddTrack called")
                    }

                })

    }

    fun addLocalStream() {
        mediaStreamManager.mediaStream = mediaStreamManager.peerConnectionFactory?.createLocalMediaStream("ARDAMS")
        mediaStreamManager.mediaStream?.addTrack(mediaStreamManager.localVideoTrack)
        mediaStreamManager.mediaStream?.addTrack(mediaStreamManager.localAudioTrack)
        pc?.addStream(mediaStreamManager.mediaStream)

        mediaStreamManager.setAudioTrack(id, mediaStreamManager.localAudioTrack, isPublisher!!)
        mediaStreamManager.setVideoTrack(id, mediaStreamManager.localVideoTrack, isPublisher!!)
    }

    private fun createStreams() {
        mediaStreamManager.mediaStream = mediaStreamManager.peerConnectionFactory?.createLocalMediaStream("ARDAMS")
        mediaStreamManager.createAudioStream()
        mediaStreamManager.setVideoCapturer(KenanteCamera.GetCamera())
        pc?.addStream(mediaStreamManager.mediaStream)
    }

    private fun createOffer() {

        val sdpConstraints = MediaConstraints()
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"))
        sdpConstraints.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))

        pc?.createOffer(object : SdpObserver {
            override fun onSetFailure(p0: String?) {

            }

            override fun onSetSuccess() {

            }

            override fun onCreateSuccess(p0: SessionDescription?) {
                pc?.setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                        Log.e(TAG, "onSetFailure called with $p0")
                    }

                    override fun onSetSuccess() {
                        Log.e(TAG, "onSetSuccess called - local description")
                        localJsep.put("type", p0?.type?.canonicalForm())
                        localJsep.put("sdp", p0?.description)

                        /*
                        On setting the local description, we have successfully completed the JOIN part of user.
                        Now it is required to publish this user with the jsep we created on the janus end.
                        */

                        KenanteWsSendMessages.publishUser(
                                KenanteSession.sessionId,
                                handleId,
                                localJsep,
                                audio!!,
                                video!!,
                                audioCodec!!,
                                videoCodec!!,
                                bitrate!!
                        )
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                        Log.e(TAG, "onCreateSuccess called")
                    }

                    override fun onCreateFailure(p0: String?) {
                        Log.e(TAG, "onCreateFailure called")
                    }
                }, p0)
            }

            override fun onCreateFailure(p0: String?) {

            }
        }, sdpConstraints)

    }

    fun createAnswer() {
        val sdpConstraints = MediaConstraints()
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"))
        sdpConstraints.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))

        pc?.createAnswer(object : SdpObserver {
            override fun onSetFailure(p0: String?) {

            }

            override fun onSetSuccess() {

            }

            override fun onCreateSuccess(p0: SessionDescription?) {
                pc?.setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                        Log.d(TAG, p0)
                    }

                    override fun onSetSuccess() {
                        Log.d(TAG, "$handleId peer connection local description set")
                        localJsep.put("type", p0?.type?.canonicalForm())
                        localJsep.put("sdp", p0?.description)
                        startThisUser()
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {

                    }

                    override fun onCreateFailure(p0: String?) {

                    }
                }, p0)
            }

            override fun onCreateFailure(p0: String?) {

            }
        }, sdpConstraints)
    }

    fun setRemoteDescription(jsep: JSONObject, isSubscriber: Boolean) {
        val type = jsep.getString("type")
        val sdp = jsep.getString("sdp")

        var sessionDescription: SessionDescription? = null

        if (type == SessionDescription.Type.OFFER.canonicalForm()) {
            sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
        } else if (type == SessionDescription.Type.ANSWER.canonicalForm()) {
            sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        }

        /*if (!isSubscriber) {
            if (!onIceGatheringCompleted) {
                cachedAnswerJsep = jsep
                Log.e(TAG, "cached Answer Jsep")
                return
            }
        }*/

        Log.e(TAG, "setRemoteDescription called with jsep: $jsep")

        try {
            pc?.setRemoteDescription(object : SdpObserver {
                override fun onSetFailure(p0: String?) {
                    Log.e(TAG, "onSetFailure call with $p0")
                }

                override fun onSetSuccess() {
                    isRemoteDescriptionSet = true

                    Log.e(TAG, "onSetSuccess called - remote description")

                    if (cachedIceCandidates.size != 0) {
                        for (each in cachedIceCandidates) {
                            Log.e(TAG, "Adding cached candidates")
                            addTrickleCandidate(each)
                        }
                        cachedIceCandidates.clear()
                    }


                    remoteJsep.put("type", sessionDescription?.type?.canonicalForm())
                    remoteJsep.put("sdp", sessionDescription?.description)
                    if (isSubscriber)
                        createAnswer()
                }

                override fun onCreateSuccess(p0: SessionDescription?) {
                    Log.e(TAG, "onCreateSuccess called")
                }

                override fun onCreateFailure(p0: String?) {
                    Log.e(TAG, "onCreateFailure called")
                }

            }, sessionDescription)
        }
        catch (e: Exception){
            Log.e(TAG, e.message)
        }

    }

    fun sendTrickleCandidates(p0: IceCandidate?) {

        val iceCandidate = JSONObject()
        if (p0 == null) {
            iceCandidate.put("completed", true)
        } else {
            iceCandidate.put("candidate", p0.sdp)
            iceCandidate.put("sdpMid", p0.sdpMid)
            iceCandidate.put("sdpMLineIndex", p0.sdpMLineIndex)
        }
        KenanteWsSendMessages.sendIceCandidates(KenanteSession.sessionId, handleId, iceCandidate)

    }

    fun addTrickleCandidate(candidate: IceCandidate?) {
        pc?.addIceCandidate(candidate)
        Log.e(TAG, "Candidate added")
    }

    fun cacheIceCandidates(candidate: IceCandidate?) {
        Log.e(TAG, "Caching ICE Candidate")
        if (candidate != null)
            cachedIceCandidates.add(candidate)
    }

    fun startThisUser() {
        KenanteWsSendMessages.startSubscriber(
                KenanteSession.sessionId,
                handleId,
                localJsep,
                KenanteSession.getInstance().roomId
        )
    }

    fun configureThisUser() {
        KenanteWsSendMessages.configureUser(
                KenanteSession.sessionId,
                handleId,
                localJsep,
                audio!!,
                video!!
        )
    }

    fun releaseObjects() {
        pc?.close()
    }

    override fun onCameraError(p0: String?) {
        Log.e(TAG, "onCameraError called with $p0")
    }

    override fun onCameraOpening(p0: String?) {
        Log.e(TAG, "onCameraOpening called with $p0")
    }

    override fun onCameraDisconnected() {
        Log.e(TAG, "onCameraDisconnected called")
    }

    override fun onCameraFreezed(p0: String?) {
        Log.e(TAG, "onCameraFreezed called with $p0")
    }

    override fun onFirstFrameAvailable() {
        Log.e(TAG, "onFirstFrameAvailable called")
    }

    override fun onCameraClosed() {
        Log.e(TAG, "onCameraClosed called")
    }

}