package com.kenante.video.interfaces

import com.kenante.video.enums.MediaType
import com.kenante.video.media.KenanteAudioTrack
import com.kenante.video.media.KenanteVideoTrack


interface KenanteMediaStreamEventListener {

    fun onLocalAudioStream(audioTrack: KenanteAudioTrack)
    fun onLocalVideoStream(videoTrack: KenanteVideoTrack)
    fun onRemoteAudioStream(audioTrack: KenanteAudioTrack)
    fun onRemoteVideoStream(videoTrack: KenanteVideoTrack)
    fun onMediaStartedFlowing(userId: Int, mediaType: MediaType)
    fun onMediaStoppedFlowing(userId: Int, mediaType: MediaType)

}