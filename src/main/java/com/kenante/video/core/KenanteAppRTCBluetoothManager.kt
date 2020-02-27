package com.kenante.video.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import org.webrtc.ThreadUtils

internal class KenanteAppRTCBluetoothManager private constructor(context: Context, audioManager: KenanteAppRTCAudioManager) {
    private val apprtcContext: Context
    private val apprtcAudioManager: KenanteAppRTCAudioManager
    private val audioManager: AudioManager
    private val handler: Handler
    var scoConnectionAttempts = 0
    private var bluetoothState: State
    private val bluetoothServiceListener: BluetoothProfile.ServiceListener
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothHeadset: BluetoothHeadset? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private val bluetoothHeadsetReceiver: BroadcastReceiver
    private val bluetoothTimeoutRunnable = Runnable { bluetoothTimeout() }

    val state: State
        get() {
            ThreadUtils.checkIsOnMainThread()
            return bluetoothState
        }

    @SuppressLint("MissingPermission")
    fun start() {
        ThreadUtils.checkIsOnMainThread()
        Log.d(TAG, "start")
        if (!hasPermission(apprtcContext, "android.permission.BLUETOOTH")) {
            Log.w(TAG, "Process (pid=" + Process.myPid() + ") lacks BLUETOOTH permission")
        } else if (bluetoothState != State.UNINITIALIZED) {
            Log.w(TAG, "Invalid BT state")
        } else {
            bluetoothHeadset = null
            bluetoothDevice = null
            scoConnectionAttempts = 0
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                Log.w(TAG, "Device does not support Bluetooth")
            } else if (!audioManager.isBluetoothScoAvailableOffCall) {
                Log.e(TAG, "Bluetooth SCO audio is not available off call")
            } else {
                logBluetoothAdapterInfo(bluetoothAdapter)
                if (!getBluetoothProfileProxy(apprtcContext, bluetoothServiceListener, 1)) {
                    Log.e(TAG, "BluetoothAdapter.getProfileProxy(HEADSET) failed")
                } else {
                    val bluetoothHeadsetFilter = IntentFilter()
                    bluetoothHeadsetFilter.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
                    bluetoothHeadsetFilter.addAction("android.bluetooth.headset.profile.action.AUDIO_STATE_CHANGED")
                    registerReceiver(bluetoothHeadsetReceiver, bluetoothHeadsetFilter)
                    Log.d(TAG, "HEADSET profile state: " + stateToString(bluetoothAdapter!!.getProfileConnectionState(1)))
                    Log.d(TAG, "Bluetooth proxy for headset profile has started")
                    bluetoothState = State.HEADSET_UNAVAILABLE
                    Log.d(TAG, "start done: BT state=" + bluetoothState)
                }
            }
        }
    }

    fun stop() {
        ThreadUtils.checkIsOnMainThread()
        Log.d(TAG, "stop: BT state=" + bluetoothState)
        if (bluetoothAdapter != null) {
            stopScoAudio()
            if (bluetoothState != State.UNINITIALIZED) {
                unregisterReceiver(bluetoothHeadsetReceiver)
                cancelTimer()
                if (bluetoothHeadset != null) {
                    bluetoothAdapter!!.closeProfileProxy(1, bluetoothHeadset)
                    bluetoothHeadset = null
                }
                bluetoothAdapter = null
                bluetoothDevice = null
                bluetoothState = State.UNINITIALIZED
                Log.d(TAG, "stop done: BT state=" + bluetoothState)
            }
        }
    }

    fun startScoAudio(): Boolean {
        ThreadUtils.checkIsOnMainThread()
        Log.d(TAG, "startSco: BT state=" + bluetoothState + ", attempts: " + scoConnectionAttempts + ", SCO is on: " + isScoOn)
        return if (scoConnectionAttempts >= 2) {
            Log.e(TAG, "BT SCO connection fails - no more attempts")
            false
        } else if (bluetoothState != State.HEADSET_AVAILABLE) {
            Log.e(TAG, "BT SCO connection fails - no headset available")
            false
        } else {
            Log.d(TAG, "Starting Bluetooth SCO and waits for ACTION_AUDIO_STATE_CHANGED...")
            bluetoothState = State.SCO_CONNECTING
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            ++scoConnectionAttempts
            startTimer()
            Log.d(TAG, "startScoAudio done: BT state=" + bluetoothState + ", SCO is on: " + isScoOn)
            true
        }
    }

    fun stopScoAudio() {
        ThreadUtils.checkIsOnMainThread()
        Log.d(TAG, "stopScoAudio: BT state=" + bluetoothState + ", SCO is on: " + isScoOn)
        if (bluetoothState == State.SCO_CONNECTING || bluetoothState == State.SCO_CONNECTED) {
            cancelTimer()
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            bluetoothState = State.SCO_DISCONNECTING
            Log.d(TAG, "stopScoAudio done: BT state=" + bluetoothState + ", SCO is on: " + isScoOn)
        }
    }

    @SuppressLint("MissingPermission")
    fun updateDevice() {
        if (bluetoothState != State.UNINITIALIZED && bluetoothHeadset != null) {
            Log.d(TAG, "updateDevice")
            val devices = bluetoothHeadset!!.connectedDevices
            if (devices.isEmpty()) {
                bluetoothDevice = null
                bluetoothState = State.HEADSET_UNAVAILABLE
                Log.d(TAG, "No connected bluetooth headset")
            } else {
                bluetoothDevice = devices[0] as BluetoothDevice
                bluetoothState = State.HEADSET_AVAILABLE
                Log.d(TAG, "Connected bluetooth headset: name=" + bluetoothDevice!!.name + ", state=" + stateToString(bluetoothHeadset!!.getConnectionState(bluetoothDevice)) + ", SCO audio=" + bluetoothHeadset!!.isAudioConnected(bluetoothDevice))
            }
            Log.d(TAG, "updateDevice done: BT state=" + bluetoothState)
        }
    }

    private fun getAudioManager(context: Context): AudioManager {
        return context.getSystemService("audio") as AudioManager
    }

    private fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        apprtcContext.registerReceiver(receiver, filter)
    }

    private fun unregisterReceiver(receiver: BroadcastReceiver) {
        apprtcContext.unregisterReceiver(receiver)
    }

    private fun getBluetoothProfileProxy(context: Context, listener: BluetoothProfile.ServiceListener, profile: Int): Boolean {
        return bluetoothAdapter!!.getProfileProxy(context, listener, profile)
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        return apprtcContext.checkPermission(permission, Process.myPid(), Process.myUid()) == 0
    }

    @SuppressLint("MissingPermission")
    private fun logBluetoothAdapterInfo(localAdapter: BluetoothAdapter?) {
        Log.d(TAG, "BluetoothAdapter: enabled=" + localAdapter!!.isEnabled + ", state=" + stateToString(localAdapter.state) + ", name=" + localAdapter.name + ", address=" + localAdapter.address)
        val pairedDevices = localAdapter.bondedDevices
        if (!pairedDevices.isEmpty()) {
            Log.d(TAG, "paired devices:")
            val var3: Iterator<*> = pairedDevices.iterator()
            while (var3.hasNext()) {
                val device = var3.next() as BluetoothDevice
                Log.d(TAG, " name=" + device.name + ", address=" + device.address)
            }
        }
    }

    private fun updateAudioDeviceState() {
        ThreadUtils.checkIsOnMainThread()
        Log.d(TAG, "updateAudioDeviceState")
        apprtcAudioManager.updateAudioDeviceState()
    }

    private fun startTimer() {
        ThreadUtils.checkIsOnMainThread()
        Log.d(TAG, "startTimer")
        handler.postDelayed(bluetoothTimeoutRunnable, 4000L)
    }

    private fun cancelTimer() {
        ThreadUtils.checkIsOnMainThread()
        Log.d(TAG, "cancelTimer")
        handler.removeCallbacks(bluetoothTimeoutRunnable)
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothTimeout() {
        ThreadUtils.checkIsOnMainThread()
        if (bluetoothState != State.UNINITIALIZED && bluetoothHeadset != null) {
            Log.d(TAG, "bluetoothTimeout: BT state=" + bluetoothState + ", attempts: " + scoConnectionAttempts + ", SCO is on: " + isScoOn)
            if (bluetoothState == State.SCO_CONNECTING) {
                var scoConnected = false
                val devices = bluetoothHeadset!!.connectedDevices
                if (devices.size > 0) {
                    bluetoothDevice = devices[0] as BluetoothDevice
                    if (bluetoothHeadset!!.isAudioConnected(bluetoothDevice)) {
                        Log.d(TAG, "SCO connected with " + bluetoothDevice!!.name)
                        scoConnected = true
                    } else {
                        Log.d(TAG, "SCO is not connected with " + bluetoothDevice!!.name)
                    }
                }
                if (scoConnected) {
                    bluetoothState = State.SCO_CONNECTED
                    scoConnectionAttempts = 0
                } else {
                    Log.w(TAG, "BT failed to connect after timeout")
                    stopScoAudio()
                }
                updateAudioDeviceState()
                Log.d(TAG, "bluetoothTimeout done: BT state=" + bluetoothState)
            }
        }
    }

    private val isScoOn: Boolean
        private get() = audioManager.isBluetoothScoOn

    private fun stateToString(state: Int): String {
        return when (state) {
            0 -> "DISCONNECTED"
            1 -> "CONNECTING"
            2 -> "CONNECTED"
            3 -> "DISCONNECTING"
            4, 5, 6, 7, 8, 9 -> "INVALID"
            10 -> "OFF"
            11 -> "TURNING_ON"
            12 -> "ON"
            13 -> "TURNING_OFF"
            else -> "INVALID"
        }
    }

    companion object {
        private val TAG = KenanteAppRTCBluetoothManager::class.java.simpleName
        private const val BLUETOOTH_SCO_TIMEOUT_MS = 4000
        private const val MAX_SCO_CONNECTION_ATTEMPTS = 2
        fun create(context: Context, audioManager: KenanteAppRTCAudioManager): KenanteAppRTCBluetoothManager {
            //Log.d(TAG, "create" + AppRTCUtils.getThreadInfo())
            return KenanteAppRTCBluetoothManager(context, audioManager)
        }
    }

    private inner class BluetoothHeadsetBroadcastReceiver() : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (bluetoothState != State.UNINITIALIZED) {
                val action = intent.action
                val state: Int
                if (action == "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED") {
                    state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", 0)
                    Log.d(TAG, "BluetoothHeadsetBroadcastReceiver.onReceive: a=ACTION_CONNECTION_STATE_CHANGED, s=" + stateToString(state) + ", sb=" + this.isInitialStickyBroadcast + ", BT state: " + bluetoothState)
                    if (state == 2) {
                        scoConnectionAttempts = 0
                        updateAudioDeviceState()
                    } else if (state != 1 && state != 3 && state == 0) {
                        stopScoAudio()
                        updateAudioDeviceState()
                    }
                } else if (action == "android.bluetooth.headset.profile.action.AUDIO_STATE_CHANGED") {
                    state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", 10)
                    Log.d(TAG, "BluetoothHeadsetBroadcastReceiver.onReceive: a=ACTION_AUDIO_STATE_CHANGED, s=" + stateToString(state) + ", sb=" + this.isInitialStickyBroadcast + ", BT state: " + bluetoothState)
                    if (state == 12) {
                        cancelTimer()
                        if (bluetoothState == State.SCO_CONNECTING) {
                            Log.d(TAG, "+++ Bluetooth audio SCO is now connected")
                            bluetoothState = State.SCO_CONNECTED
                            scoConnectionAttempts = 0
                            updateAudioDeviceState()
                        } else {
                            Log.w(TAG, "Unexpected state BluetoothHeadset.STATE_AUDIO_CONNECTED")
                        }
                    } else if (state == 11) {
                        Log.d(TAG, "+++ Bluetooth audio SCO is now connecting...")
                    } else if (state == 10) {
                        Log.d(TAG, "+++ Bluetooth audio SCO is now disconnected")
                        if (this.isInitialStickyBroadcast) {
                            Log.d(TAG, "Ignore STATE_AUDIO_DISCONNECTED initial sticky broadcast.")
                            return
                        }
                        updateAudioDeviceState()
                    }
                }
                Log.d(TAG, "onReceive done: BT state=" + bluetoothState)
            }
        }
    }

    private inner class BluetoothServiceListener() : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == 1 && bluetoothState != State.UNINITIALIZED) {
                Log.d(TAG, "BluetoothServiceListener.onServiceConnected: BT state=" + bluetoothState)
                bluetoothHeadset = proxy as BluetoothHeadset
                updateAudioDeviceState()
                Log.d(TAG, "onServiceConnected done: BT state=" + bluetoothState)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == 1 && bluetoothState != State.UNINITIALIZED) {
                Log.d(TAG, "BluetoothServiceListener.onServiceDisconnected: BT state=" + bluetoothState)
                stopScoAudio()
                bluetoothHeadset = null
                bluetoothDevice = null
                bluetoothState = State.HEADSET_UNAVAILABLE
                updateAudioDeviceState()
                Log.d(TAG, "onServiceDisconnected done: BT state=" + bluetoothState)
            }
        }
    }

    enum class State {
        UNINITIALIZED, ERROR, HEADSET_UNAVAILABLE, HEADSET_AVAILABLE, SCO_DISCONNECTING, SCO_CONNECTING, SCO_CONNECTED
    }

    init {
        ThreadUtils.checkIsOnMainThread()
        apprtcContext = context
        apprtcAudioManager = audioManager
        this.audioManager = getAudioManager(context)
        bluetoothState = State.UNINITIALIZED
        bluetoothServiceListener = BluetoothServiceListener()
        bluetoothHeadsetReceiver = BluetoothHeadsetBroadcastReceiver()
        handler = Handler(Looper.getMainLooper())
    }
}