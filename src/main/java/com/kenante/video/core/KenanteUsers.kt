package com.kenante.video.core

import android.util.SparseArray
import androidx.core.util.set
import com.kenante.video.enums.KenanteAudioCodec
import com.kenante.video.enums.KenanteBitrate
import com.kenante.video.enums.KenanteVideoCodec

object KenanteUsers {

    private val users = SparseArray<User>()
    internal val liveUsers = mutableListOf<Int>()

    fun setUserCallParameters(
            id: Int,
            audio: Boolean,
            video: Boolean,
            bitrate: KenanteBitrate
    ) {
        users[id] = User(audio, video, bitrate)
    }

    fun getUser(id: Int): User? {
        return users[id]
    }

    fun setUsersContainer(user: Int) {
        users[user] = User(audio = true, video = true, bitrate = KenanteBitrate.low)
    }

}

data class User(
        var audio: Boolean,
        var video: Boolean,
        var bitrate: KenanteBitrate = KenanteBitrate.low
)