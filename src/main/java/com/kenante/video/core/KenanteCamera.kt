package com.kenante.video.core

import android.util.Log
import org.webrtc.*

object KenanteCamera : CameraVideoCapturer.CameraEventsHandler {

    private val TAG = KenanteCamera::class.java.simpleName
    private val mediaStreamManager = KenanteMediaStreamManager.GetManager()

    private fun createVideoCapturer(): VideoCapturer? {
        return createCameraCapturer(Camera1Enumerator(false))
    }

    private fun createCameraCapturer(enumarator: CameraEnumerator): VideoCapturer? {

        enumarator.run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                return enumarator.createCapturer(it, this@KenanteCamera)
            }
        }

        return null
    }

    fun GetCamera() : VideoCapturer? {
        val videoCapturer = createVideoCapturer()
        return videoCapturer
    }

    /*internal fun openCamera() {
        val rootEglBase = KenanteEglBase.GetEglBaseContext()

        mediaStreamManager.videoCapturerAndroid = createVideoCapturer()

        val surfaceTextureHelper =
                SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
        mediaStreamManager.videoCapturerAndroid?.initialize(
                surfaceTextureHelper,
                KenanteSettings.getInstance().getContext()!!,
                mediaStreamManager.videoSource?.capturerObserver
        )
        mediaStreamManager.videoCapturerAndroid?.startCapture(1280, 720, 30)
    }*/

    internal fun switchCamera(observer: CameraVideoCapturer.CameraSwitchHandler) {
        val videoCapturerAndroid: VideoCapturer? = KenanteMediaStreamManager.GetManager().videoCapturerAndroid
        if (videoCapturerAndroid != null) {
            val cameraVideoCapturer: CameraVideoCapturer? = videoCapturerAndroid as CameraVideoCapturer
            cameraVideoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(p0: Boolean) {
                    KenanteSendCallbacks.getInstance().handler.post{
                        observer.onCameraSwitchDone(p0)
                    }
                }

                override fun onCameraSwitchError(p0: String?) {
                    KenanteSendCallbacks.getInstance().handler.post{
                        observer.onCameraSwitchError(p0)
                    }
                }
            })
        }
    }

    internal fun stopCamera() {
        val videoCapturerAndroid: VideoCapturer? = KenanteMediaStreamManager.GetManager().videoCapturerAndroid
        videoCapturerAndroid?.stopCapture()
        videoCapturerAndroid?.dispose()
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