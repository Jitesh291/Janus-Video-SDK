package com.kenante.video.media

import android.os.Build
import androidx.annotation.RequiresApi
import com.kenante.video.core.KenantePluginHandler
import com.kenante.video.core.KenanteSession
import org.webrtc.AudioTrack

class KenanteAudioTrack(val userId: Int, private val audioTrack: AudioTrack?) {

    private var enabled: Boolean? = null

    init {
        for ((_, value) in KenanteSession.pluginHandles) {
            if (value.id == userId)
                enabled = value.audio
        }
    }

    fun setVolume(vol: Double) {
        this.audioTrack?.setVolume(vol)
    }

    fun setEnabled(enable: Boolean) {
        if (enable)
            setVolume(5.0)
        else
            setVolume(0.0)
        enabled = enable
        /*var pluginHandle: KenantePluginHandler? = null
        for ((key, value) in KenanteSession.pluginHandles) {
            if (value.id == userId) {
                pluginHandle = value
                pluginHandle.audio = enable
                enabled = enable
                break
            }
        }
        pluginHandle?.configureThisUser()*/
    }

    fun enabled(): Boolean {
        return enabled!!
    }

}