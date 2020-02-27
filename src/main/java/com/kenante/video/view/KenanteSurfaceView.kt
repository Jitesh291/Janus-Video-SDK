package com.kenante.video.view

import android.content.Context
import android.util.AttributeSet
import com.kenante.video.core.KenanteEglBase
import org.webrtc.RendererCommon

class KenanteSurfaceView(context: Context?, attrs: AttributeSet?) :
    KBaseSurfaceViewRenderer(context, attrs) {

    override fun init() {
        if (!this.inited) {
            val eglContext = KenanteEglBase.GetEglBaseContext()
            this.init(eglContext.eglBaseContext, null as RendererCommon.RendererEvents?)
            this.inited = true
        }
    }

}