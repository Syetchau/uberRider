package com.example.kotlinuberrider.Service

import android.util.Log
import com.example.kotlinuberrider.Common.Common
import com.example.kotlinuberrider.Utils.UserUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class MyFirebaseMessagingService: FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if(FirebaseAuth.getInstance().currentUser != null) {
            UserUtils.updateToken(this, token)
        }
    }

    override fun onMessageReceived(remoteMsg: RemoteMessage) {
        super.onMessageReceived(remoteMsg)
        val data = remoteMsg.data
        if (data != null) {
            Common.showNotification(this, Random.nextInt(),
                data[Common.NOTIFICATION_TITLE],
                data[Common.NOTIFICATION_BODY],
                null)
        }
    }
}