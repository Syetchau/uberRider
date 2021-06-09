package com.example.kotlinuberrider

import android.animation.ValueAnimator
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.kotlinuberrider.Common.Common
import com.example.kotlinuberrider.Model.DriverGeo
import com.example.kotlinuberrider.Model.EventBus.*
import com.example.kotlinuberrider.Model.TripPlan
import com.example.kotlinuberrider.Remote.GoogleApi
import com.example.kotlinuberrider.Remote.RetrofitClient
import com.example.kotlinuberrider.Utils.UserUtils
import com.example.kotlinuberrider.databinding.*

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.ui.IconGenerator
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import java.io.IOException
import kotlin.random.Random

class RequestDriverActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityRequestDriverBinding
    private lateinit var confirmUberBinding: LayoutConfirmUberBinding
    private lateinit var confirmPickupBinding: LayoutConfirmPickupBinding
    private lateinit var findingDriverBinding: LayoutFindingDriverBinding
    private lateinit var driverInfoBinding: LayoutDriverInfoBinding
    private var selectedPlaceEvent: SelectedPlaceEvent?= null
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var tvDuration: TextView
    private lateinit var tvOriginAddress: TextView

    //routes
    private val compositeDisposable = CompositeDisposable()
    private lateinit var googleApi: GoogleApi
    private var blackPolyline: Polyline?= null
    private var greyPolyline: Polyline?= null
    private var polylineOptions: PolylineOptions?= null
    private var blackPolylineOptions: PolylineOptions?= null
    private var polylineList: ArrayList<LatLng>?= null
    private var originMarker: Marker?= null
    private var destinationMarker: Marker?= null

    //animation effect
    private var lastUserCircle: Circle?= null
    private val duration = 1000
    private var lastPulseAnimator: ValueAnimator?= null

    //spinning animation
    private var spinningAnimator: ValueAnimator?= null
    private val desiredNumberOfSpins = 5
    private val desiredSecondsPerOnePull360Spin = 40

    private var lastDriverCall: DriverGeo?= null

    //driverOldPos
    private var driverOldPosition: String = ""
    private var handler: Handler?= null
    private var v = 0f
    private var lat = 0.0
    private var lng = 0.0
    private var index = 0
    private var next = 0
    private var start: LatLng?= null
    private var end: LatLng?= null

    override fun onStart() {
        if (!EventBus.getDefault().isRegistered(this)){
            EventBus.getDefault().register(this)
        }
        super.onStart()
    }

    override fun onStop() {
        compositeDisposable.clear()
        if (EventBus.getDefault().hasSubscriberForEvent(SelectedPlaceEvent::class.java)){
            EventBus.getDefault().removeStickyEvent(SelectedPlaceEvent::class.java)
        }
        if (EventBus.getDefault().hasSubscriberForEvent(DeclineRequestFromDriverEvent::class.java)){
            EventBus.getDefault().removeStickyEvent(DeclineRequestFromDriverEvent::class.java)
        }
        if (EventBus.getDefault().hasSubscriberForEvent(DriverAcceptTripEvent::class.java)){
            EventBus.getDefault().removeStickyEvent(DriverAcceptTripEvent::class.java)
        }
        if (EventBus.getDefault().hasSubscriberForEvent(DeclineRequestAndRemoveTripFromDriverEvent::class.java)){
            EventBus.getDefault().removeStickyEvent(DeclineRequestAndRemoveTripFromDriverEvent::class.java)
        }
        if (EventBus.getDefault().hasSubscriberForEvent(DriverCompleteTripEvent::class.java)){
            EventBus.getDefault().removeStickyEvent(DriverCompleteTripEvent::class.java)
        }
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    override fun onDestroy() {
        if (spinningAnimator != null){
            spinningAnimator!!.end()
        }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRequestDriverBinding.inflate(layoutInflater)
        confirmUberBinding = binding.layoutConfirmUber
        confirmPickupBinding = binding.layoutConfirmPickup
        findingDriverBinding = binding.layoutFindingDriver
        driverInfoBinding = binding.layoutDriverInfo

        setContentView(binding.root)
        initData()

       mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

//        if (ActivityCompat.checkSelfPermission(
//                this,
//                Manifest.permission.ACCESS_FINE_LOCATION
//            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
//                this,
//                Manifest.permission.ACCESS_COARSE_LOCATION
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            return
//        }
//
//        mMap.isMyLocationEnabled = true
//        mMap.uiSettings.isMyLocationButtonEnabled = true
//        mMap.setOnMyLocationClickListener {
//            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
//                selectedPlaceEvent!!.origin, 18f))
//        }
//        Log.d("selectedPlaceEvent", selectedPlaceEvent.toString())
        drawPath(selectedPlaceEvent!!)

        //layout button
//        val view = mapFragment.requireView()
//            .findViewById<View>("1".toInt()).parent as View
//        val locationBtn = view.findViewById<View>("2".toInt())
//        val params = locationBtn.layoutParams as RelativeLayout.LayoutParams
//        params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
//        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
//        params.bottomMargin = 250

//        mMap.uiSettings.isZoomControlsEnabled = true
        try {
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.uber_maps_style))
            if (!success) {
                Snackbar.make(mapFragment.requireView(), "Load map style failed",
                    Snackbar.LENGTH_SHORT).show()
            }
        } catch (e: Resources.NotFoundException) {
            Snackbar.make(mapFragment.requireView(), e.message!!, Snackbar.LENGTH_SHORT).show()
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onSelectedPlaceEvent(event: SelectedPlaceEvent) {
        selectedPlaceEvent = event
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDeclineReceivedEvent(event: DeclineRequestFromDriverEvent) {
        if (lastDriverCall != null) {
            Common.driversFound[lastDriverCall!!.key]!!.isDecline = true
            findNearbyDriver(selectedPlaceEvent!!)
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDeclineAndRemoveTripReceivedEvent(event: DeclineRequestAndRemoveTripFromDriverEvent) {
        if (lastDriverCall != null) {
            if (Common.driversFound[lastDriverCall!!.key] != null) {
                Common.driversFound[lastDriverCall!!.key]!!.isDecline = true
            }
            finish()
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDriverAcceptTripEvent(event: DriverAcceptTripEvent) {
        FirebaseDatabase.getInstance()
            .getReference(Common.TRIP)
            .child(event.tripId)
            .addListenerForSingleValueEvent(object: ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()){
                        val tripPlan = snapshot.getValue(TripPlan::class.java)
                        mMap.clear()
                        binding.viewFillMap.visibility = View.GONE
                        if (spinningAnimator != null) {
                            spinningAnimator!!.end()
                        }
                        val cameraPosition = CameraPosition.Builder()
                            .target(mMap.cameraPosition.target)
                            .tilt(0f)
                            .zoom(mMap.cameraPosition.zoom)
                            .build()
                        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))

                        //get routes
                        val driverLocation = StringBuilder()
                            .append(tripPlan!!.currentLat)
                            .append(",")
                            .append(tripPlan.currentLng)
                            .toString()

                        compositeDisposable.add(
                            googleApi.getDirection("driving",
                            "less_driving",
                            tripPlan.origin,
                            driverLocation,
                            getString(R.string.google_api_key))
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe{
                                    var blackPolylineOptions: PolylineOptions?= null
                                    var polylineList: List<LatLng?>?= null
                                    var blackPolyline: Polyline?= null
                                    try {
                                        val jsonObject = JSONObject(it)
                                        val jsonArray = jsonObject.getJSONArray("routes")
                                        for (i in 0 until jsonArray.length()) {
                                            val route = jsonArray.getJSONObject(i)
                                            val poly = route.getJSONObject("overview_polyline")
                                            val polyline = poly.getString("points")
                                            polylineList = Common.decodePoly(polyline)
                                        }

                                        blackPolylineOptions = PolylineOptions()
                                        blackPolylineOptions.color(Color.BLACK)
                                        blackPolylineOptions.width(5f)
                                        blackPolylineOptions.startCap(SquareCap())
                                        blackPolylineOptions.jointType(JointType.ROUND)
                                        blackPolylineOptions.addAll(polylineList!!)
                                        blackPolyline = mMap.addPolyline(blackPolylineOptions)

                                        val latLngBound = LatLngBounds.Builder()
                                            .include(selectedPlaceEvent!!.origin)
                                            .include(selectedPlaceEvent!!.destination)
                                            .build()
                                        // add car icon for origin
                                        val objects = jsonArray.getJSONObject(0)
                                        val legs = objects.getJSONArray("legs")
                                        val legsObject = legs.getJSONObject(0)
                                        val time = legsObject.getJSONObject("duration")
                                        val duration = time.getString("text")

                                        val origin = LatLng(
                                            tripPlan.origin!!.split(",")[0].toDouble(),
                                            tripPlan.origin!!.split(",")[1].toDouble()
                                        )
                                        val destination = LatLng(
                                            tripPlan.currentLat, tripPlan.currentLng
                                        )
                                        val latLngBounds = LatLngBounds.Builder()
                                            .include(origin)
                                            .include(destination)
                                            .build()

                                        addPickupMarkerWithDuration(duration, origin)
                                        addDriverMarker(destination)

                                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBound, 160))
                                        mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.cameraPosition.zoom-1))

                                        initDriverForMoving(event.tripId, tripPlan)

                                        //load driver avatar
                                        Glide.with(this@RequestDriverActivity)
                                            .load(tripPlan.driverInfo!!.avatar)
                                            .into(driverInfoBinding.circularIvDriver)
                                        driverInfoBinding.tvDriverName.text = tripPlan.driverInfo!!.firstName
                                        confirmPickupBinding.cvConfirmPickup.visibility = View.GONE
                                        confirmUberBinding.cvConfirmUber.visibility = View.GONE
                                        driverInfoBinding.cvDriverInfo.visibility = View.VISIBLE

                                    } catch (e: IOException) {
                                        Toast.makeText(this@RequestDriverActivity,
                                            e.message!!, Toast.LENGTH_SHORT).show()
                                    }
                                }
                        )
                    } else {
                        Snackbar.make(binding.rlRequestDriver, getString(R.string.trips_not_found),
                            Snackbar.LENGTH_LONG).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                   Snackbar.make(binding.rlRequestDriver, error.message, Snackbar.LENGTH_LONG).show()
                }
            })
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDriverCompleteTripEvent(event: DriverCompleteTripEvent) {
        Common.showNotification(
            this, Random.nextInt(),
            "Thank you",
            "Your trip " + event.tripId + "has been completed",
            null
        )
        finish()
    }

    private fun initData() {
        googleApi = RetrofitClient.instance!!.create(GoogleApi::class.java)

        confirmUberBinding.btnConfirmUber.setOnClickListener {
            confirmPickupBinding.cvConfirmPickup.visibility = View.VISIBLE
            confirmUberBinding.cvConfirmUber.visibility = View.GONE

            setDataPickup()
        }

        confirmPickupBinding.btnConfirmPickup.setOnClickListener {
            if (mMap == null) {
                return@setOnClickListener
            }
            if(selectedPlaceEvent == null) {
                return@setOnClickListener
            }
            mMap.clear()
            //rotate camera
            val cameraPos = CameraPosition.Builder()
                .target(selectedPlaceEvent!!.origin)
                .tilt(45f)
                .zoom(16f)
                .build()
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))
            startMarkerWithPulseAnimation()
        }
    }

    private fun drawPath(selectedPlaceEvent: SelectedPlaceEvent) {
        compositeDisposable.add(googleApi.getDirection(
            "driving",
            "less_driving",
            selectedPlaceEvent.originString,
            selectedPlaceEvent.destinationString,
            getString(R.string.google_api_key))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { it ->
                //Log.d("API_RETURN", it)
                try {
                    val jsonObject = JSONObject(it)
                    val jsonArray = jsonObject.getJSONArray("routes")
                    for (i in 0 until jsonArray.length()) {
                        val route = jsonArray.getJSONObject(i)
                        val poly = route.getJSONObject("overview_polyline")
                        val polyline = poly.getString("points")
                        polylineList = Common.decodePoly(polyline)
                    }
                    polylineOptions = PolylineOptions()
                    polylineOptions!!.color(Color.GRAY)
                    polylineOptions!!.width(12f)
                    polylineOptions!!.startCap(SquareCap())
                    polylineOptions!!.jointType(JointType.ROUND)
                    polylineOptions!!.addAll(polylineList!!)
                    greyPolyline = mMap.addPolyline(polylineOptions!!)

                    blackPolylineOptions = PolylineOptions()
                    blackPolylineOptions!!.color(Color.BLACK)
                    blackPolylineOptions!!.width(5f)
                    blackPolylineOptions!!.startCap(SquareCap())
                    blackPolylineOptions!!.jointType(JointType.ROUND)
                    blackPolylineOptions!!.addAll(polylineList!!)
                    blackPolyline = mMap.addPolyline(blackPolylineOptions!!)

                    //Animator
                    val valueAnimator = ValueAnimator.ofInt(0,100)
                    valueAnimator.duration = 1100
                    valueAnimator.repeatCount = ValueAnimator.INFINITE
                    valueAnimator.interpolator = LinearInterpolator()
                    valueAnimator.addUpdateListener {
                        val points = greyPolyline!!.points
                        val percentValue = it.animatedValue.toString().toInt()
                        val size = points.size
                        val newPoints = (size * (percentValue/100f)).toInt()
                        val p = points.subList(0, newPoints)
                        blackPolyline!!.points = p
                    }
                    valueAnimator.start()
                    val latLngBound = LatLngBounds.Builder()
                        .include(selectedPlaceEvent.origin)
                        .include(selectedPlaceEvent.destination)
                        .build()
                    // add car icon for origin
                    val objects = jsonArray.getJSONObject(0)
                    val legs = objects.getJSONArray("legs")
                    val legsObject = legs.getJSONObject(0)
                    val time = legsObject.getJSONObject("duration")
                    val duration = time.getString("text")
                    val distance = legsObject.getJSONObject("distance")
                    val distanceText = distance.getString("text")
                    val startAddress = legsObject.getString("start_address")
                    val endAddress = legsObject.getString("end_address")

                    //set value
                    confirmUberBinding.tvDistanceShow.text = distanceText
                    confirmUberBinding.tvTimeShow.text = duration

                    addOriginMarker(duration, startAddress)
                    addDestinationMarker(endAddress)

                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBound, 160))
                    mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.cameraPosition.zoom-1))
                } catch (e: IOException) {
                    Toast.makeText(this, e.message!!, Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun addOriginMarker(duration: String, startAddress: String) {
        val view = layoutInflater.inflate(R.layout.origin_info_window, null)
        tvDuration = view.findViewById<View>(R.id.tv_time) as TextView
        tvOriginAddress = view.findViewById<View>(R.id.tv_origin_address) as TextView

        tvDuration.text = Common.formatDuration(duration)
        tvOriginAddress.text = Common.formatAddress(startAddress)
        val iconGenerator = IconGenerator(this)
        iconGenerator.setContentView(view)
        iconGenerator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = iconGenerator.makeIcon()
        originMarker = mMap.addMarker(MarkerOptions().icon(
            BitmapDescriptorFactory.fromBitmap(icon)).position(selectedPlaceEvent!!.origin))
    }

    private fun addDestinationMarker(endAddress: String) {
        val view = layoutInflater.inflate(R.layout.destination_info_window, null)
        val tvDestinationAddress = view.findViewById<View>(R.id.tv_destination_address) as TextView

        tvDestinationAddress.text = Common.formatAddress(endAddress)

        val iconGenerator = IconGenerator(this)
        iconGenerator.setContentView(view)
        iconGenerator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = iconGenerator.makeIcon()
        destinationMarker = mMap.addMarker(MarkerOptions().icon(
            BitmapDescriptorFactory.fromBitmap(icon)).position(selectedPlaceEvent!!.destination))
    }

    private fun addPickupMarkerWithDuration(duration: String, origin: LatLng) {
        val icon = Common.createIconWithDuration(this@RequestDriverActivity, duration)!!
        originMarker = mMap.addMarker(MarkerOptions().icon(
            BitmapDescriptorFactory.fromBitmap(icon)).position(origin)
        )
    }

    private fun addDriverMarker(destination: LatLng) {
        destinationMarker = mMap.addMarker(MarkerOptions()
            .position(destination)
            .flat(true)
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
        )
    }

    private fun initDriverForMoving(tripId: String, tripPlan: TripPlan) {
        driverOldPosition = StringBuilder()
            .append(tripPlan.currentLat)
            .append(",")
            .append(tripPlan.currentLng)
            .toString()

        FirebaseDatabase.getInstance()
            .getReference(Common.TRIP)
            .child(tripId)
            .addValueEventListener(object: ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    val newData = snapshot.getValue(TripPlan::class.java)
                    if (newData != null) {
                        val driverNewPosition = StringBuilder()
                            .append(newData.currentLat)
                            .append(",")
                            .append(newData.currentLng)
                            .toString()

                        //if not equal
                        if (driverOldPosition != driverNewPosition) {
                            moveMarkerAnimation(
                                destinationMarker!!,
                                driverOldPosition,
                                driverNewPosition
                            )
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(binding.rlRequestDriver, error.message,Snackbar.LENGTH_LONG).show()
                }
            })
    }


    private fun moveMarkerAnimation(marker: Marker, from: String, to: String) {
        compositeDisposable.add(
            googleApi.getDirection("driving",
                "less_driving",
                from,
                to,
                getString(R.string.google_api_key))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe{
                    try {
                        val jsonObject = JSONObject(it)
                        val jsonArray = jsonObject.getJSONArray("routes")
                        for (i in 0 until jsonArray.length()) {
                            val route = jsonArray.getJSONObject(i)
                            val poly = route.getJSONObject("overview_polyline")
                            val polyline = poly.getString("points")
                            polylineList = Common.decodePoly(polyline)
                        }

                        blackPolylineOptions = PolylineOptions()
                        blackPolylineOptions!!.color(Color.BLACK)
                        blackPolylineOptions!!.width(5f)
                        blackPolylineOptions!!.startCap(SquareCap())
                        blackPolylineOptions!!.jointType(JointType.ROUND)
                        blackPolylineOptions!!.addAll(polylineList!!)
                        blackPolyline = mMap.addPolyline(blackPolylineOptions!!)

                        val latLngBound = LatLngBounds.Builder()
                            .include(selectedPlaceEvent!!.origin)
                            .include(selectedPlaceEvent!!.destination)
                            .build()
                        // add car icon for origin
                        val objects = jsonArray.getJSONObject(0)
                        val legs = objects.getJSONArray("legs")
                        val legsObject = legs.getJSONObject(0)
                        val time = legsObject.getJSONObject("duration")
                        val duration = time.getString("text")

                        val bitmap = Common.createIconWithDuration(this@RequestDriverActivity, duration)
                        originMarker!!.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap!!))

                        //moving
                        val runnable = object:Runnable{
                            override fun run() {
                                if (index < polylineList!!.size - 2) {
                                    index++
                                    next = index + 1
                                    start = polylineList!![index]
                                    end = polylineList!![next]
                                }
                                val valueAnimator = ValueAnimator.ofInt(0,1)
                                valueAnimator.duration = 1500
                                valueAnimator.interpolator = LinearInterpolator()
                                valueAnimator.addUpdateListener { it ->
                                    v = it.animatedFraction
                                    lat = v*end!!.latitude+(1-v)*start!!.latitude
                                    lng = v*end!!.longitude+(1-v)*end!!.longitude
                                    val newPosition = LatLng(lat, lng)
                                    marker.position = newPosition
                                    marker.setAnchor(0.5f, 0.5f)
                                    marker.rotation = Common.getBearing(start!!, newPosition)
                                    mMap.moveCamera(CameraUpdateFactory.newLatLng(newPosition))
                                }
                                valueAnimator.start()
                                if (index < polylineList!!.size - 2) {
                                    handler!!.postDelayed(this, 1500)
                                }
                            }
                        }
                        handler = Handler()
                        index = -1
                        next = 1
                        handler!!.postDelayed(runnable, 1500)
                        driverOldPosition = to

                    } catch (e: IOException) {
                        Toast.makeText(this@RequestDriverActivity,
                            e.message!!, Toast.LENGTH_SHORT).show()
                    }
                }
        )
    }


    private fun setDataPickup() {
        confirmPickupBinding.tvAddressPickup.text = if(tvOriginAddress != null)
            tvOriginAddress.text else "None"
        mMap.clear()
        addPickupMarker()
    }

    private fun addPickupMarker() {
        val view = layoutInflater.inflate(R.layout.pickup_info_window, null)
        val iconGenerator = IconGenerator(this)
        iconGenerator.setContentView(view)
        iconGenerator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = iconGenerator.makeIcon()
        originMarker = mMap.addMarker(MarkerOptions()
            .icon(BitmapDescriptorFactory.fromBitmap(icon))
            .position(selectedPlaceEvent!!.origin))
    }

    private fun startMarkerWithPulseAnimation() {
        binding.layoutConfirmPickup.cvConfirmPickup.visibility = View.GONE
        binding.viewFillMap.visibility = View.VISIBLE
        binding.layoutFindingDriver.cvFindingDriver.visibility = View.VISIBLE

        originMarker = mMap.addMarker(MarkerOptions().icon(BitmapDescriptorFactory.defaultMarker())
            .position(selectedPlaceEvent!!.origin))

        addPulsatingEffect(selectedPlaceEvent!!)
    }

    private fun addPulsatingEffect(selectedPlaceEvent: SelectedPlaceEvent) {
        if(lastPulseAnimator != null) {
            lastPulseAnimator!!.cancel()
        }
        if (lastUserCircle != null) {
            lastUserCircle!!.center = selectedPlaceEvent.origin
        }
        lastPulseAnimator = Common.valueAnimate(duration, object:ValueAnimator.AnimatorUpdateListener{
            override fun onAnimationUpdate(animation: ValueAnimator?) {
                if (lastUserCircle != null) {
                    lastUserCircle!!.radius = animation!!.animatedValue.toString().toDouble()
                } else{
                    lastUserCircle = mMap.addCircle(CircleOptions()
                        .center(selectedPlaceEvent.origin)
                        .radius(animation!!.animatedValue.toString().toDouble())
                        .strokeColor(Color.WHITE)
                        .fillColor(ContextCompat.getColor(this@RequestDriverActivity,
                            R.color.map_darker))
                    )
                }
            }
        })
        //rotate camera
        startMapCameraSpinningAnimation(selectedPlaceEvent)
    }

    private fun startMapCameraSpinningAnimation(selectedPlaceEvent: SelectedPlaceEvent) {
        if (spinningAnimator != null) {
            spinningAnimator!!.cancel()
        }
        spinningAnimator = ValueAnimator.ofFloat(0f, (desiredNumberOfSpins * 360).toFloat())
        spinningAnimator!!.duration = (desiredSecondsPerOnePull360Spin * 1000).toLong()
        spinningAnimator!!.interpolator = LinearInterpolator()
        spinningAnimator!!.startDelay = 100
        spinningAnimator!!.addUpdateListener {
            val newBearingValue = it.animatedValue as Float
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(selectedPlaceEvent.origin)
                    .zoom(16f)
                    .tilt(45f)
                    .bearing(newBearingValue)
                    .build()
            ))
        }
        spinningAnimator!!.start()
        findNearbyDriver(selectedPlaceEvent)
    }

    private fun findNearbyDriver(selectedPlaceEvent: SelectedPlaceEvent) {
        if (Common.driversFound.size > 0) {
            var min = 0f
            //default found driver is first driver
            var foundDriver: DriverGeo?= null
            val currentRiderLocation = Location("")
            currentRiderLocation.latitude = selectedPlaceEvent.origin.latitude
            currentRiderLocation.longitude = selectedPlaceEvent.origin.longitude

            for (key in Common.driversFound.keys){
                val driverLocation = Location("")
                driverLocation.latitude = Common.driversFound[key]!!.geoLocation!!.latitude
                driverLocation.longitude = Common.driversFound[key]!!.geoLocation!!.longitude

                //init min value && found driver if first driver in list
                if (min == 0f) {
                    min = driverLocation.distanceTo(currentRiderLocation)
                    if (!Common.driversFound[key]!!.isDecline!!){
                        foundDriver = Common.driversFound[key]
                        break //exit loop as we found driver
                    } else {
                        continue //if decline already,skip n continue
                    }
                } else if (driverLocation.distanceTo(currentRiderLocation) < min){
                    min = driverLocation.distanceTo(currentRiderLocation)
                    if (!Common.driversFound[key]!!.isDecline!!){
                        foundDriver = Common.driversFound[key]
                        break //exit loop as we found driver
                    } else {
                        continue //if decline already,skip n continue
                    }
                }
            }
            if (foundDriver != null) {
                UserUtils.sendRequestToDriver(this, binding.rlRequestDriver, foundDriver, selectedPlaceEvent!!)
                lastDriverCall = foundDriver
            } else {
                Toast.makeText(this, getString(R.string.no_driver_accept),
                    Toast.LENGTH_SHORT).show()
                lastDriverCall = null
                finish()
            }
        } else {
            Snackbar.make(binding.rlRequestDriver, getString(R.string.drivers_not_found)
                ,Snackbar.LENGTH_LONG).show()
            lastDriverCall = null
            finish()
        }
    }
}