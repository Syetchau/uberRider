package com.example.kotlinuberrider.Utils

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import com.example.kotlinuberrider.Common.Common
import com.example.kotlinuberrider.Model.DriverGeo
import com.example.kotlinuberrider.Model.EventBus.SelectedPlaceEvent
import com.example.kotlinuberrider.Model.FCMSendData
import com.example.kotlinuberrider.Model.Token
import com.example.kotlinuberrider.R
import com.example.kotlinuberrider.Remote.FCMService
import com.example.kotlinuberrider.Remote.RetrofitFCMClient
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.selects.select
import java.lang.StringBuilder

object UserUtils {
    fun updateUser(view: View, data: Map<String, Any>) {
        FirebaseDatabase
            .getInstance()
            .getReference(Common.RIDER_INFO_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .updateChildren(data)
            .addOnFailureListener { e->
                Snackbar.make(view, e.message!!, Snackbar.LENGTH_SHORT).show()
            }
            .addOnSuccessListener{
                Snackbar.make(view, "Update success!", Snackbar.LENGTH_SHORT).show()
            }
    }

    fun updateToken(context: Context, token: String) {
        val tokenModel = Token()
        tokenModel.token = token

        FirebaseDatabase
            .getInstance()
            .getReference(Common.TOKEN_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .setValue(tokenModel)
            .addOnFailureListener { e->
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            }
            .addOnSuccessListener {  }
    }

    fun sendRequestToDriver(context: Context,
                            rlRequestDriver: RelativeLayout,
                            foundDriver: DriverGeo?,
                            selectedPlaceEvent: SelectedPlaceEvent) {
        val compositeDisposable = CompositeDisposable()
        val fcmService = RetrofitFCMClient.instance!!.create(FCMService::class.java)
        FirebaseDatabase    //Get token
            .getInstance()
            .getReference(Common.TOKEN_REFERENCE)
            .child(foundDriver!!.key!!)
            .addListenerForSingleValueEvent(object: ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()){
                        val tokenModel = snapshot.getValue(Token::class.java)
                        val notificationData: MutableMap<String, String> = HashMap()
                        val pickupLocationString = StringBuilder()
                            .append(selectedPlaceEvent.origin.latitude)
                            .append(",")
                            .append(selectedPlaceEvent.origin.longitude)
                            .toString()
                        val destinationLocationString = StringBuilder()
                            .append(selectedPlaceEvent.destination.latitude)
                            .append(",")
                            .append(selectedPlaceEvent.destination.longitude)
                            .toString()

                        notificationData[Common.NOTIFICATION_TITLE] = Common.REQUEST_DRIVER_TITLE
                        notificationData[Common.NOTIFICATION_BODY] = "This message represent for Request Driver action"
                        notificationData[Common.RIDER_KEY] = FirebaseAuth.getInstance().currentUser!!.uid

                        notificationData[Common.PICKUP_LOCATION] = pickupLocationString
                        notificationData[Common.PICKUP_LOCATION_STRING] = selectedPlaceEvent.originAddress

                        notificationData[Common.DESTINATION_LOCATION] = selectedPlaceEvent.destinationAddress
                        notificationData[Common.DESTINATION_LOCATION_STRING] = selectedPlaceEvent.destinationString

                        //add new information
                        notificationData[Common.RIDER_DISTANCE_TEXT] = selectedPlaceEvent.distanceText
                        notificationData[Common.RIDER_DISTANCE_VALUE] = selectedPlaceEvent.distanceValue.toString()
                        notificationData[Common.RIDER_DURATION_TEXT] = selectedPlaceEvent.durationText
                        notificationData[Common.RIDER_DURATION_VALUE] = selectedPlaceEvent.durationValue.toString()
                        notificationData[Common.RIDER_TOTAL_FEE] = selectedPlaceEvent.totalFee.toString()

                        val fcmData = FCMSendData(tokenModel!!.token, notificationData)
                        compositeDisposable.add(fcmService.sendNotification(fcmData)!!
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({response ->
                                if (response!!.success == 0){
                                    compositeDisposable.clear()
                                    Snackbar.make(rlRequestDriver, context.getString(R.string.send_req_driver_failed),
                                        Snackbar.LENGTH_LONG).show()
                                }
                            }, {t: Throwable? ->
                                compositeDisposable.clear()
                                Snackbar.make(rlRequestDriver,t!!.message!!, Snackbar.LENGTH_LONG).show()
                            }))
                    } else{
                        Snackbar.make(rlRequestDriver, context.getString(R.string.token_not_found),
                            Snackbar.LENGTH_LONG).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(rlRequestDriver, error.message, Snackbar.LENGTH_LONG).show()
                }
            })
    }
}