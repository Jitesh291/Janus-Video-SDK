package com.kenante.video.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.util.Log
import org.webrtc.ThreadUtils
import java.util.*
import com.kenante.video.core.KenanteAppRTCBluetoothManager.State

class KenanteAppRTCAudioManager private constructor(context: Context) {
    private var wiredHeadsetStateListener: OnWiredHeadsetStateListener? = null
    private var bluetoothAudioDeviceStateListener: BluetoothAudioDeviceStateListener? = null
    private var onAudioStateChangeListener: OnAudioManagerStateListener? = null
    private var manageHeadsetByDefault = true
    private var manageBluetoothByDefault = true
    private var manageSpeakerPhoneByProximity = false
    private val apprtcContext: Context
    val androidAudioManager: AudioManager
    private var audioManagerEvents: AudioManagerEvents? = null
    private var amState: AudioManagerState
    private var savedAudioMode: Int
    private var savedIsSpeakerPhoneOn: Boolean
    private var savedIsMicrophoneMute: Boolean
    private var hasWiredHeadset: Boolean
    private var defaultAudioDevice: AudioDevice
    private var selectedAudioDevice: AudioDevice? = null
    private var userSelectedAudioDevice: AudioDevice? = null
    private var proximitySensor: KenanteAppRTCProximitySensor? = null
    private val bluetoothManager: KenanteAppRTCBluetoothManager
    private var audioDevices: MutableSet<AudioDevice?>? = null
    private val wiredHeadsetReceiver: BroadcastReceiver
    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null

    var onProximitySensorChangedState = Runnable {
        if (manageSpeakerPhoneByProximity) {
            if (audioDevices?.size == 2 && audioDevices?.contains(AudioDevice.EARPIECE)!! && audioDevices?.contains(AudioDevice.SPEAKER_PHONE)!!) {
                if (proximitySensor!!.sensorReportsNearState()) {
                    userSelectedAudioDevice = AudioDevice.EARPIECE
                    setAudioDeviceInternal(userSelectedAudioDevice)
                } else {
                    userSelectedAudioDevice = AudioDevice.SPEAKER_PHONE
                    setAudioDeviceInternal(userSelectedAudioDevice)
                }
                if (audioManagerEvents != null) {
                    audioManagerEvents!!.onAudioDeviceChanged(selectedAudioDevice, audioDevices)
                }
            }
        }
    }

    /*private fun onProximitySensorChangedState() {
        if (manageSpeakerPhoneByProximity) {
            if (audioDevices.size == 2 && audioDevices.contains(AudioDevice.EARPIECE) && audioDevices.contains(AudioDevice.SPEAKER_PHONE)) {
                if (proximitySensor!!.sensorReportsNearState()) {
                    userSelectedAudioDevice = AudioDevice.EARPIECE
                    setAudioDeviceInternal(userSelectedAudioDevice)
                } else {
                    userSelectedAudioDevice = AudioDevice.SPEAKER_PHONE
                    setAudioDeviceInternal(userSelectedAudioDevice)
                }
                if (audioManagerEvents != null) {
                    audioManagerEvents!!.onAudioDeviceChanged(selectedAudioDevice, audioDevices)
                }
            }
        }
    }*/

    private constructor(context: Context, onAudioManagerStateListener: OnAudioManagerStateListener) : this(context) {
        onAudioStateChangeListener = onAudioManagerStateListener
    }

    @Deprecated("")
    fun init() {
        start(object : AudioManagerEvents {
            override fun onAudioDeviceChanged(var1: AudioDevice?, var2: Set<AudioDevice?>?) {
                onAudioManagerChangedState(var1!!)
            }
        })
    }

    fun start(audioManagerEvents: AudioManagerEvents?) {
        Log.d(TAG, "start")
        ThreadUtils.checkIsOnMainThread()
        if (amState == AudioManagerState.RUNNING) {
            Log.e(TAG, "AudioManager is already active")
        } else {
            Log.d(TAG, "AudioManager starts...")
            this.audioManagerEvents = audioManagerEvents
            amState = AudioManagerState.RUNNING
            savedAudioMode = androidAudioManager.mode
            savedIsSpeakerPhoneOn = androidAudioManager.isSpeakerphoneOn
            savedIsMicrophoneMute = androidAudioManager.isMicrophoneMute
            hasWiredHeadset = hasWiredHeadset()
            audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange: Int ->
                val typeOfChange: String
                typeOfChange = when (focusChange) {
                    -3 -> "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK"
                    -2 -> "AUDIOFOCUS_LOSS_TRANSIENT"
                    -1 -> "AUDIOFOCUS_LOSS"
                    0 -> "AUDIOFOCUS_INVALID"
                    1 -> "AUDIOFOCUS_GAIN"
                    2 -> "AUDIOFOCUS_GAIN_TRANSIENT"
                    3 -> "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"
                    4 -> "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE"
                    else -> "AUDIOFOCUS_INVALID"
                }
                Log.d(TAG, "onAudioFocusChange: $typeOfChange")
            }
            val result = androidAudioManager.requestAudioFocus(audioFocusChangeListener, 0, 2)
            if (result == 1) {
                Log.d(TAG, "Audio focus request granted for VOICE_CALL streams")
            } else {
                Log.e(TAG, "Audio focus request failed")
            }
            androidAudioManager.mode = 3
            setMicrophoneMute(false)
            userSelectedAudioDevice = AudioDevice.NONE
            selectedAudioDevice = AudioDevice.NONE
            audioDevices?.clear()
            bluetoothManager.start()
            proximitySensor?.start()
            updateAudioDeviceState()
            registerReceiver(wiredHeadsetReceiver, IntentFilter("android.intent.action.HEADSET_PLUG"))
            Log.d(TAG, "AudioManager started")
        }
    }

    @Deprecated("")
    fun setOnAudioManagerStateListener(onAudioManagerStateListener: OnAudioManagerStateListener?) {
        onAudioStateChangeListener = onAudioManagerStateListener
    }

    @Deprecated("")
    private fun onAudioManagerChangedState(audioDevice: AudioDevice) {
        Log.d(TAG, "onAudioManagerChangedState: devices=" + audioDevices + ", selected=" + selectedAudioDevice)
        if (onAudioStateChangeListener != null) {
            onAudioStateChangeListener!!.onAudioChangedState(audioDevice)
        }
    }

    fun setManageHeadsetByDefault(manageHeadsetByDefault: Boolean) {
        this.manageHeadsetByDefault = manageHeadsetByDefault
    }

    fun setOnWiredHeadsetStateListener(wiredHeadsetStateListener: OnWiredHeadsetStateListener?) {
        this.wiredHeadsetStateListener = wiredHeadsetStateListener
    }

    private fun notifyWiredHeadsetListener(plugged: Boolean, hasMicrophone: Boolean) {
        if (wiredHeadsetStateListener != null) {
            wiredHeadsetStateListener!!.onWiredHeadsetStateChanged(plugged, hasMicrophone)
        }
    }

    fun setManageSpeakerPhoneByProximity(manageSpeakerPhoneByProximity: Boolean) {
        this.manageSpeakerPhoneByProximity = manageSpeakerPhoneByProximity
    }

    fun setManageBluetoothByDefault(manageBluetoothByDefault: Boolean) {
        this.manageBluetoothByDefault = manageBluetoothByDefault
    }

    fun setBluetoothAudioDeviceStateListener(bluetoothAudioDeviceStateListener: BluetoothAudioDeviceStateListener?) {
        this.bluetoothAudioDeviceStateListener = bluetoothAudioDeviceStateListener
    }

    private fun notifyBluetoothAudioDeviceStateListener(connected: Boolean) {
        if (bluetoothAudioDeviceStateListener != null) {
            bluetoothAudioDeviceStateListener!!.onStateChanged(connected)
        }
    }

    @Deprecated("")
    fun close() {
        stop()
    }

    fun stop() {
        Log.d(TAG, "stop")
        ThreadUtils.checkIsOnMainThread()
        if (amState != AudioManagerState.RUNNING) {
            Log.e(TAG, "Trying to stop AudioManager in incorrect state: " + amState)
        } else {
            amState = AudioManagerState.UNINITIALIZED
            unregisterReceiver(wiredHeadsetReceiver)
            bluetoothManager.stop()
            setSpeakerphoneOn(savedIsSpeakerPhoneOn)
            setMicrophoneMute(savedIsMicrophoneMute)
            androidAudioManager.mode = savedAudioMode
            androidAudioManager.abandonAudioFocus(audioFocusChangeListener)
            audioFocusChangeListener = null
            Log.d(TAG, "Abandoned audio focus for VOICE_CALL streams")
            if (proximitySensor != null) {
                proximitySensor!!.stop()
                proximitySensor = null
            }
            audioManagerEvents = null
            Log.d(TAG, "AudioManager stopped")
        }
    }

    @Deprecated("")
    fun setAudioDevice(device: AudioDevice) {
        if (!audioDevices!!.contains(device)) {
            Log.e(TAG, "Device doesn't nave $device")
        } else if (device != selectedAudioDevice) {
            selectAudioDevice(device)
        }
    }

    fun getDefaultAudioDevice(): AudioDevice {
        return defaultAudioDevice
    }

    private fun setAudioDeviceInternal(device: AudioDevice?) {
        Log.d(TAG, "setAudioDeviceInternal(device=$device)")
        if (!audioDevices?.contains(device)!!) {
            Log.e(TAG, "Invalid audio device selection")
        } else {
            when (device) {
                AudioDevice.SPEAKER_PHONE -> setSpeakerphoneOn(true)
                AudioDevice.EARPIECE -> setSpeakerphoneOn(false)
                AudioDevice.WIRED_HEADSET -> setSpeakerphoneOn(false)
                AudioDevice.BLUETOOTH -> setSpeakerphoneOn(false)
                else -> Log.e(TAG, "Invalid audio device selection")
            }
            if (AudioDevice.EARPIECE == device && hasWiredHeadset) {
                selectedAudioDevice = AudioDevice.WIRED_HEADSET
            } else {
                selectedAudioDevice = device
            }
        }
    }

    fun setDefaultAudioDevice(defaultDevice: AudioDevice?) {
        ThreadUtils.checkIsOnMainThread()
        when (defaultDevice) {
            AudioDevice.SPEAKER_PHONE -> defaultAudioDevice = defaultDevice
            AudioDevice.EARPIECE -> if (hasEarpiece()) {
                defaultAudioDevice = defaultDevice
            } else {
                defaultAudioDevice = AudioDevice.SPEAKER_PHONE
            }
            else -> Log.e(TAG, "Invalid default audio device selection")
        }
        Log.d(TAG, "setDefaultAudioDevice(device=" + defaultAudioDevice + ")")
        updateAudioDeviceState()
    }

    fun selectAudioDevice(device: AudioDevice) {
        ThreadUtils.checkIsOnMainThread()
        if (!audioDevices!!.contains(device)) {
            Log.e(TAG, "Can not select " + device + " from available " + audioDevices)
        } else {
            userSelectedAudioDevice = device
            updateAudioDeviceState()
        }
    }

    fun getAudioDevices(): MutableSet<Any?> {
        ThreadUtils.checkIsOnMainThread()
        return Collections.unmodifiableSet<Any?>(HashSet<Any?>(audioDevices))
    }

    fun getSelectedAudioDevice(): AudioDevice? {
        ThreadUtils.checkIsOnMainThread()
        return selectedAudioDevice
    }

    private fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        apprtcContext.registerReceiver(receiver, filter)
    }

    private fun unregisterReceiver(receiver: BroadcastReceiver) {
        apprtcContext.unregisterReceiver(receiver)
    }

    private fun setSpeakerphoneOn(on: Boolean) {
        val wasOn = androidAudioManager.isSpeakerphoneOn
        if (wasOn != on) {
            androidAudioManager.isSpeakerphoneOn = on
        }
    }

    private fun setMicrophoneMute(on: Boolean) {
        val wasMuted = androidAudioManager.isMicrophoneMute
        if (wasMuted != on) {
            androidAudioManager.isMicrophoneMute = on
        }
    }

    private fun hasEarpiece(): Boolean {
        return apprtcContext.packageManager.hasSystemFeature("android.hardware.telephony")
    }

    @Deprecated("")
    private fun hasWiredHeadset(): Boolean {
        return if (Build.VERSION.SDK_INT < 23) {
            androidAudioManager.isWiredHeadsetOn
        } else {
            val devices = androidAudioManager.getDevices(3)
            val var3 = devices.size
            for (var4 in 0 until var3) {
                val device = devices[var4]
                val type = device.type
                if (type == 3) {
                    Log.d(TAG, "hasWiredHeadset: found wired headset")
                    return true
                }
                if (type == 11) {
                    Log.d(TAG, "hasWiredHeadset: found USB audio device")
                    return true
                }
            }
            false
        }
    }

    fun updateAudioDeviceState() {
        ThreadUtils.checkIsOnMainThread()
        Log.d(TAG, "--- updateAudioDeviceState: wired headset=" + hasWiredHeadset + ", BT state=" + bluetoothManager.state)
        Log.d(TAG, "Device status: available=" + audioDevices + ", selected=" + selectedAudioDevice + ", user selected=" + userSelectedAudioDevice)
        if (bluetoothManager.state === State.HEADSET_AVAILABLE || bluetoothManager.state === State.HEADSET_UNAVAILABLE || bluetoothManager.state === State.SCO_DISCONNECTING) {
            bluetoothManager.updateDevice()
        }
        val newAudioDevices: MutableSet<AudioDevice?> = mutableSetOf()
        if (bluetoothManager.state === State.SCO_CONNECTED || bluetoothManager.state === State.SCO_CONNECTING || bluetoothManager.state === State.HEADSET_AVAILABLE) {
            newAudioDevices.add(AudioDevice.BLUETOOTH)
            if (audioDevices!!.isNotEmpty() && !audioDevices!!.contains(AudioDevice.BLUETOOTH)) {
                if (manageBluetoothByDefault) {
                    userSelectedAudioDevice = AudioDevice.BLUETOOTH
                }
                notifyBluetoothAudioDeviceStateListener(true)
            }
        }
        if (hasWiredHeadset) {
            newAudioDevices.add(AudioDevice.WIRED_HEADSET)
        }
        newAudioDevices.add(AudioDevice.SPEAKER_PHONE)
        if (hasEarpiece()) {
            newAudioDevices.add(AudioDevice.EARPIECE)
        }
        var audioDeviceSetUpdated = audioDevices != newAudioDevices
        audioDevices = newAudioDevices
        if (bluetoothManager.state === State.HEADSET_UNAVAILABLE && userSelectedAudioDevice == AudioDevice.BLUETOOTH) {
            userSelectedAudioDevice = AudioDevice.NONE
        }
        if (!hasWiredHeadset && userSelectedAudioDevice == AudioDevice.WIRED_HEADSET) {
            userSelectedAudioDevice = AudioDevice.NONE
        }
        val needBluetoothAudioStart = bluetoothManager.state === State.HEADSET_AVAILABLE && (userSelectedAudioDevice == AudioDevice.NONE || userSelectedAudioDevice == AudioDevice.BLUETOOTH)
        val needBluetoothAudioStop = (bluetoothManager.state === State.SCO_CONNECTED || bluetoothManager.state === State.SCO_CONNECTING) && userSelectedAudioDevice != AudioDevice.NONE && userSelectedAudioDevice != AudioDevice.BLUETOOTH
        if (bluetoothManager.state === State.HEADSET_AVAILABLE || bluetoothManager.state === State.SCO_CONNECTING || bluetoothManager.state === State.SCO_CONNECTED) {
            Log.d(TAG, "Need BT audio: start=" + needBluetoothAudioStart + ", stop=" + needBluetoothAudioStop + ", BT state=" + bluetoothManager.state)
        }
        if (needBluetoothAudioStop) {
            bluetoothManager.stopScoAudio()
            bluetoothManager.updateDevice()
        }
        if (needBluetoothAudioStart && !needBluetoothAudioStop && !bluetoothManager.startScoAudio()) {
            audioDevices!!.remove(AudioDevice.BLUETOOTH)
            notifyBluetoothAudioDeviceStateListener(false)
            audioDeviceSetUpdated = true
        }
        val newAudioDevice: AudioDevice?
        newAudioDevice = if (userSelectedAudioDevice != AudioDevice.NONE) {
            userSelectedAudioDevice
        } else {
            defaultAudioDevice
        }
        if (newAudioDevice != selectedAudioDevice || audioDeviceSetUpdated) {
            setAudioDeviceInternal(newAudioDevice)
            Log.d(TAG, "New device status: available=" + audioDevices + ", selected=" + selectedAudioDevice)
            if (audioManagerEvents != null) {
                audioManagerEvents!!.onAudioDeviceChanged(selectedAudioDevice, audioDevices)
            }
        }
        Log.d(TAG, "--- updateAudioDeviceState done")
    }

    companion object {
        private val TAG = KenanteAppRTCAudioManager::class.java.simpleName
        fun create(context: Context): KenanteAppRTCAudioManager {
            return KenanteAppRTCAudioManager(context)
        }

        @Deprecated("")
        fun create(context: Context, onAudioManagerStateListener: OnAudioManagerStateListener): KenanteAppRTCAudioManager {
            return KenanteAppRTCAudioManager(context, onAudioManagerStateListener)
        }
    }

    interface BluetoothAudioDeviceStateListener {
        fun onStateChanged(var1: Boolean)
    }

    interface AudioManagerEvents {
        fun onAudioDeviceChanged(var1: AudioDevice?, var2: Set<AudioDevice?>?)
    }

    @Deprecated("")
    interface OnAudioManagerStateListener {
        fun onAudioChangedState(var1: AudioDevice?)
    }

    interface OnWiredHeadsetStateListener {
        fun onWiredHeadsetStateChanged(var1: Boolean, var2: Boolean)
    }

    private inner class WiredHeadsetReceiver() : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra("state", 0)
            val microphone = intent.getIntExtra("microphone", 0)
            val name = intent.getStringExtra("name")
            //Log.d(TAG, "WiredHeadsetReceiver.onReceive" + AppRTCUtils.getThreadInfo().toString() + ": a=" + intent.action.toString() + ", s=" + (if (state == 0) "unplugged" else "plugged").toString() + ", m=" + (if (microphone == 1) "mic" else "no mic").toString() + ", n=" + name.toString() + ", sb=" + this.isInitialStickyBroadcast)
            hasWiredHeadset = state == 1
            notifyWiredHeadsetListener(state == 1, microphone == 1)
            if (manageHeadsetByDefault) {
                if (hasWiredHeadset) {
                    userSelectedAudioDevice = AudioDevice.WIRED_HEADSET
                }
                updateAudioDeviceState()
            }
        }
        /*companion object {
            private const val STATE_UNPLUGGED = 0
            private const val STATE_PLUGGED = 1
            private const val HAS_NO_MIC = 0
            private const val HAS_MIC = 1
        }*/
    }

    enum class AudioManagerState {
        UNINITIALIZED, PREINITIALIZED, RUNNING
    }

    enum class AudioDevice {
        SPEAKER_PHONE, WIRED_HEADSET, EARPIECE, BLUETOOTH, NONE
    }

    init {
        savedAudioMode = -2
        savedIsSpeakerPhoneOn = false
        savedIsMicrophoneMute = false
        hasWiredHeadset = false
        defaultAudioDevice = AudioDevice.SPEAKER_PHONE
        proximitySensor = null
        audioDevices =  mutableSetOf()
        ThreadUtils.checkIsOnMainThread()
        apprtcContext = context.applicationContext
        androidAudioManager = context.getSystemService("audio") as AudioManager
        bluetoothManager = KenanteAppRTCBluetoothManager.create(context, this)
        wiredHeadsetReceiver = WiredHeadsetReceiver()
        amState = AudioManagerState.UNINITIALIZED
        proximitySensor = KenanteAppRTCProximitySensor.create(context, onProximitySensorChangedState)
        Log.d(TAG, "defaultAudioDevice: " + defaultAudioDevice)
        //AppRTCUtils.logDeviceInfo(TAG)
    }
}