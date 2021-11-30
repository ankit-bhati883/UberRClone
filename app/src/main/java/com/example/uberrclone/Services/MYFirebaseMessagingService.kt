package com.example.uberrclone.Services

import com.example.uberrclone.Utils.UserUtils
import com.example.uberrclone.comon.common
import com.example.uberrclone.model.EventBus.DeclineRequestAndRemoveTripFromDriver
import com.example.uberrclone.model.EventBus.DeclineRequestFromDriver
import com.example.uberrclone.model.EventBus.DriverAcceptTripEvent
import com.example.uberrclone.model.EventBus.DriverCompleteTripEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.greenrobot.eventbus.EventBus
import kotlin.random.Random

class MYFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (FirebaseAuth.getInstance().currentUser != null) {
            UserUtils.updatetoken(this, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        if (data != null) {
            if (data[common.NOTI_TITLE] != null) {
                if (data[common.NOTI_TITLE].equals(common.REQUEST_DRIVER_DECLINE)) {
                 EventBus.getDefault().postSticky(DeclineRequestFromDriver())

                }else if (data[common.NOTI_TITLE].equals(common.REQUEST_DRIVER_ACCEPT)) {
                    EventBus.getDefault().postSticky(DriverAcceptTripEvent(data[common.TRIP_KEY]!!))

                }
                else if (data[common.NOTI_TITLE].equals(common.REQUEST_DRIVER_DECLINE_AND_REMOVE_TRIP)) {
                    EventBus.getDefault().postSticky(DeclineRequestAndRemoveTripFromDriver())

                }
                else if (data[common.NOTI_TITLE].equals(common.RIDER_REQUEST_COMPLETE_TRIP)) {
                    val tripkey=data[common.TRIP_KEY]
                    EventBus.getDefault().postSticky(DriverCompleteTripEvent(tripkey!!))

                }
                else
                    common.showNotification(
                        this, Random.nextInt(),
                        data[common.NOTI_TITLE],
                        data[common.NOTI_BODY], null
                    )
            }
        }
    }}