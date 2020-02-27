package com.kenante.video.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import org.webrtc.ThreadUtils

internal class KenanteAppRTCProximitySensor private constructor(context: Context, sensorStateListener: Runnable) : SensorEventListener {
    private val threadChecker = ThreadUtils.ThreadChecker()
    private val onSensorStateListener: Runnable?
    private val sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private var lastStateReportIsNear = false
    fun start(): Boolean {
        threadChecker.checkIsOnValidThread()
        //Log.d(TAG, "start" + AppRTCUtils.getThreadInfo())
        return if (!initDefaultSensor()) {
            false
        } else {
            sensorManager.registerListener(this, proximitySensor, 3)
            true
        }
    }

    fun stop() {
        threadChecker.checkIsOnValidThread()
        //Log.d(TAG, "stop" + AppRTCUtils.getThreadInfo())
        if (proximitySensor != null) {
            sensorManager.unregisterListener(this, proximitySensor)
        }
    }

    fun sensorReportsNearState(): Boolean {
        threadChecker.checkIsOnValidThread()
        return lastStateReportIsNear
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        threadChecker.checkIsOnValidThread()
        if (sensor.type != 8) {
            Log.e(TAG, "Accuracy changed for unexpected sensor")
        } else {
            if (accuracy == 0) {
                Log.e(TAG, "The values returned by this sensor cannot be trusted")
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        threadChecker.checkIsOnValidThread()
        if (event.sensor.type != 8) {
            Log.e(TAG, "Sensor changed for unexpected sensor")
        } else {
            val distanceInCentimeters = event.values[0]
            if (distanceInCentimeters < proximitySensor!!.maximumRange) {
                Log.d(TAG, "Proximity sensor => NEAR state")
                lastStateReportIsNear = true
            } else {
                Log.d(TAG, "Proximity sensor => FAR state")
                lastStateReportIsNear = false
            }
            onSensorStateListener?.run()
            //Log.d(TAG, "onSensorChanged" + AppRTCUtils.getThreadInfo().toString() + ": accuracy=" + event.accuracy.toString() + ", timestamp=" + event.timestamp.toString() + ", distance=" + event.values[0])
        }
    }

    private fun initDefaultSensor(): Boolean {
        return if (proximitySensor != null) {
            true
        } else {
            proximitySensor = sensorManager.getDefaultSensor(8)
            if (proximitySensor == null) {
                false
            } else {
                logProximitySensorInfo()
                true
            }
        }
    }

    private fun logProximitySensorInfo() {
        if (proximitySensor != null) {
            val info = StringBuilder("Proximity sensor: ")
            info.append("name=").append(proximitySensor!!.name)
            info.append(", vendor: ").append(proximitySensor!!.vendor)
            info.append(", power: ").append(proximitySensor!!.power)
            info.append(", resolution: ").append(proximitySensor!!.resolution)
            info.append(", max range: ").append(proximitySensor!!.maximumRange)
            info.append(", min delay: ").append(proximitySensor!!.minDelay)
            if (Build.VERSION.SDK_INT >= 20) {
                info.append(", type: ").append(proximitySensor!!.stringType)
            }
            if (Build.VERSION.SDK_INT >= 21) {
                info.append(", max delay: ").append(proximitySensor!!.maxDelay)
                info.append(", reporting mode: ").append(proximitySensor!!.reportingMode)
                info.append(", isWakeUpSensor: ").append(proximitySensor!!.isWakeUpSensor)
            }
            Log.d(TAG, info.toString())
        }
    }

    companion object {
        private val TAG = KenanteAppRTCProximitySensor::class.java.simpleName
        fun create(context: Context, sensorStateListener: Runnable): KenanteAppRTCProximitySensor {
            return KenanteAppRTCProximitySensor(context, sensorStateListener)
        }
    }

    init {
        //Log.d(TAG, AppRTCUtils.getThreadInfo())
        onSensorStateListener = sensorStateListener
        sensorManager = context.getSystemService("sensor") as SensorManager
    }
}