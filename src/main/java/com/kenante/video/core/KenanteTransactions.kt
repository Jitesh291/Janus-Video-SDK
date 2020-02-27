package com.kenante.video.core

import com.example.kenante_janus.enums.KenanteMessageType

object KenanteTransactions {

    private var transactions = HashMap<KenanteMessageType, String>()
    private var subscribers = HashMap<String, Int>()

    fun setCreateTransaction(t_id: String) {
        transactions[KenanteMessageType.create] = t_id
    }

    fun getCreateTransaction(): String {
        return if (transactions[KenanteMessageType.create] != null)
            transactions[KenanteMessageType.create]!!
        else
            ""
    }

    fun setPublisherAttachTransaction(t_id: String) {
        transactions[KenanteMessageType.attach] = t_id
    }

    fun getPublisherAttachTransaction(): String {
        return if (transactions[KenanteMessageType.attach] != null)
            transactions[KenanteMessageType.attach]!!
        else
            ""
    }

    fun addSubscriberAttachTransaction(t_id: String, userId: Int) {
        if (!subscribers.contains(t_id))
            subscribers[t_id] = userId
    }

    fun isSubscriberAttachTransaction(t_id: String): Boolean {
        return if (subscribers.containsKey(t_id))
            true
        else
            return false
    }

    fun getSubscriberId(t_id: String) : Int?{
        return if(subscribers.containsKey(t_id))
            subscribers[t_id]
        else
            return null
    }

    fun setPublsherJoinRoomTransaction(t_id: String){
        transactions[KenanteMessageType.join] = t_id
    }

}