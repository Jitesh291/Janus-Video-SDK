package com.kenante.video.view

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import org.webrtc.SurfaceViewRenderer

abstract class KBaseSurfaceViewRenderer(context: Context?, attrs: AttributeSet?) :
    SurfaceViewRenderer(context, attrs) {

    protected var inited: Boolean = false

    override fun surfaceCreated(holder: SurfaceHolder){
        super.surfaceCreated(holder)
        this.init()
    }

    protected abstract fun init()

}