package com.kenante.video.interfaces

interface SessionEventListener {

    fun onSuccess(message: String)
    fun onError(error: String)

}