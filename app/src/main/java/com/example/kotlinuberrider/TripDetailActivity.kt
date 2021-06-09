package com.example.kotlinuberrider

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.example.kotlinuberrider.Callback.FirebaseFailedListener
import com.example.kotlinuberrider.Callback.TripDetailListener
import com.example.kotlinuberrider.Common.Common
import com.example.kotlinuberrider.Model.EventBus.*
import com.example.kotlinuberrider.Model.TripPlan
import com.example.kotlinuberrider.databinding.ActivityRequestDriverBinding
import com.example.kotlinuberrider.databinding.ActivityTripDetailBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.random.Random

class TripDetailActivity : AppCompatActivity(), TripDetailListener, FirebaseFailedListener {

    private lateinit var tripDetailBinding: ActivityTripDetailBinding
    private lateinit var tripDetailListener: TripDetailListener
    private lateinit var firebaseFailedListener: FirebaseFailedListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tripDetailBinding = ActivityTripDetailBinding.inflate(layoutInflater)

        setContentView(tripDetailBinding.root)

        initData()
    }

    override fun onStart() {
        if (!EventBus.getDefault().isRegistered(this)){
            EventBus.getDefault().register(this)
        }
        super.onStart()
    }

    override fun onStop() {
        if (EventBus.getDefault().hasSubscriberForEvent(LoadTripDetailEvent::class.java)){
            EventBus.getDefault().removeStickyEvent(LoadTripDetailEvent::class.java)
        }
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    override fun onTripDetailLoadSuccess(tripPlan: TripPlan) {
        //setData
        tripDetailBinding.tvDate.text = tripPlan.timeText
        tripDetailBinding.tvPrice.text = StringBuilder("$").append(tripPlan.totalFee)
        tripDetailBinding.tvOrigin.text = tripPlan.originString
        tripDetailBinding.tvDestination.text = tripPlan.destinationString
        tripDetailBinding.tvBaseFareTrip.text = StringBuilder("$").append(Common.BASE_FARE)
        tripDetailBinding.tvDistanceTrip.text = tripPlan.distanceText
        tripDetailBinding.tvDurationTrip.text = tripPlan.durationText

        //showLayout
        tripDetailBinding.llTripDetail.visibility = View.VISIBLE
        tripDetailBinding.progressRing.visibility = View.GONE
    }

    override fun onFirebaseFailed(message: String?) {
        Snackbar.make(tripDetailBinding.llTripLayout, message!!, Snackbar.LENGTH_LONG).show()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onLoadTripDetailEvent(event: LoadTripDetailEvent) {
        FirebaseDatabase.getInstance()
            .getReference(Common.TRIP)
            .child(event.tripKey)
            .addListenerForSingleValueEvent(object: ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val tripPlan = snapshot.getValue(TripPlan::class.java)
                        tripDetailListener.onTripDetailLoadSuccess(tripPlan!!)
                    } else {
                        firebaseFailedListener.onFirebaseFailed("Cannot find trip key")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    firebaseFailedListener.onFirebaseFailed(error.message)
                }
            })
    }

    private fun initData() {
        tripDetailListener = this
        firebaseFailedListener = this

        tripDetailBinding.ivBack.setOnClickListener {
            finish()
        }
    }
}