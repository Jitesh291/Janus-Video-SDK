package com.kenante.video.interfaces

interface UserCallEventListener {

    fun onUserAvailable(userId: Int)
    fun onUserConnectedToCall(userId: Int)
    fun onUserDisconnectedFromCall(userId: Int)
    fun onUserConnectionClosed(userId: Int)

}