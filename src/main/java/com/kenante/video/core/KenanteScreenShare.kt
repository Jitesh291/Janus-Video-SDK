package com.kenante.video.core

import android.annotation.TargetApi
import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.VideoCapturer

@TargetApi(21)
object KenanteScreenShare {

    const val REQUEST_MEDIA_PROJECTION = 101
    const val MediaProjectionService = "media_projection"

    fun requestPermissions(context: Activity) {
        val mMediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        context.startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), 101)
    }

    fun requestPermissions(context: Fragment) {
        val mMediaProjectionManager = context.activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        context.startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), 101)
    }

    fun KenanteScreenCapturer(mediaProjectionpermissionResultDate: Intent?, callback: MediaProjection.Callback?) : VideoCapturer?{
        var callback = callback
        if (callback == null) {
            callback = ProjectionCallback()
        }
        val videoCapturer = ScreenCapturerAndroid(mediaProjectionpermissionResultDate, callback as MediaProjection.Callback?)
        return videoCapturer
    }

    private class ProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
           Log.e(CLASS_TAG, "User revoked permission to capture the screen.")
        }

        companion object {
            private val CLASS_TAG: String = KenanteScreenShare::class.java.simpleName
        }
    }

}