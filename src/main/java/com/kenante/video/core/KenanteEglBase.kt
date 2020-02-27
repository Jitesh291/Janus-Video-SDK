package com.kenante.video.core

import org.webrtc.EglBase

object KenanteEglBase {

    private var eglBaseContext: EglBase? = null

    fun GetEglBaseContext(): EglBase {
        if(eglBaseContext == null){
            eglBaseContext = EglBase.create()
            return eglBaseContext!!
        }
        return eglBaseContext!!
    }

    fun releaseEglContext() {
        if (eglBaseContext != null) {
            eglBaseContext!!.release()
            eglBaseContext = null
        }
    }

}