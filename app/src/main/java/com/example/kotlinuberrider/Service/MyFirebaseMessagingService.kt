package com.example.kotlinuberrider.Service

import com.example.kotlinuberrider.Common.Common
import com.example.kotlinuberrider.Model.EventBus.DeclineRequestAndRemoveTripFromDriverEvent
import com.example.kotlinuberrider.Model.EventBus.DeclineRequestFromDriverEvent
import com.example.kotlinuberrider.Model.EventBus.DriverAcceptTripEvent
import com.example.kotlinuberrider.Model.EventBus.DriverCompleteTripEvent
import com.example.kotlinuberrider.Utils.UserUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.greenrobot.eventbus.EventBus
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
            if (data[Common.NOTIFICATION_TITLE] != null) {
                when {
                    data[Common.NOTIFICATION_TITLE].equals(Common.REQUEST_DRIVER_DECLINE) -> {
                        EventBus.getDefault().postSticky(DeclineRequestFromDriverEvent())
                    }
                    data[Common.NOTIFICATION_TITLE].equals(Common.REQUEST_DRIVER_ACCEPT) -> {
                        EventBus.getDefault().postSticky(DriverAcceptTripEvent(data[Common.TRIP_KEY]!!))
                    }
                    data[Common.NOTIFICATION_TITLE].equals(Common.REQUEST_DRIVER_DECLINE_AND_REMOVE_TRIP) -> {
                        EventBus.getDefault().postSticky(DeclineRequestAndRemoveTripFromDriverEvent())
                    }
                    data[Common.NOTIFICATION_TITLE].equals(Common.RIDER_REQUEST_COMPLETE_TRIP) -> {
                        val tripKey = data[Common.TRIP_KEY]
                        EventBus.getDefault().postSticky(DriverCompleteTripEvent(tripKey!!))
                    }
                    else -> {
                        Common.showNotification(
                            this, Random.nextInt(),
                            data[Common.NOTIFICATION_TITLE],
                            data[Common.NOTIFICATION_BODY],
                            null
                        )
                    }
                }
            }
        }
    }
}