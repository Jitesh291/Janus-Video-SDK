package com.kenante.video.media

import com.kenante.video.core.KenantePluginHandler
import com.kenante.video.core.KenanteSession
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import kotlin.math.sin

class KenanteVideoTrack(val userId: Int, private val videoTrack: VideoTrack?) {

    private var enabled: Boolean? = null
    init {
        for ((_, value) in KenanteSession.pluginHandles) {
            if (value.id == userId)
                enabled = value.video
        }
    }

    private var sink: VideoSink? = null

    fun addSink(sink: VideoSink) {
        this.sink = sink
        this.videoTrack?.addSink(sink)
    }

    fun setEnabled(enable: Boolean){
        var pluginHandle: KenantePluginHandler? = null
        for ((key, value) in KenanteSession.pluginHandles) {
            if (value.id == userId) {
                pluginHandle = value
                pluginHandle.video = enable
                enabled = enable
                break
            }
        }
        pluginHandle?.configureThisUser()
    }

    fun enabled(): Boolean {
        return enabled!!
    }

    fun getSink(): VideoSink? {
        return sink
    }

    fun removeSink(sink: VideoSink?) {
        if (sink != null)
            this.videoTrack?.removeSink(sink)
    }

}