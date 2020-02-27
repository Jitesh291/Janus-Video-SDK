package com.kenante.video.helper

import com.kenante.video.core.KenanteSession
import com.kenante.video.core.KenanteWsSendMessages
import java.util.*

class KHelper{

    companion object{

        fun GenerateTransactionId() : String{
            val charPool : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
            val randomString = (1..12)
                .map {kotlin.random.Random.nextInt(0, charPool.size) }
                .map(charPool::get)
                .joinToString("")
            return randomString
        }

        fun startKeepAlive() {
            Timer().scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    KenanteWsSendMessages.sendKeepAlive(KenanteSession.sessionId)
                }
            }, 0, Constants.KEEP_ALIVE_DURATION)
        }

    }

}