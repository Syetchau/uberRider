package com.example.kotlinuberrider

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.kotlinuberrider.Common.Common
import com.example.kotlinuberrider.Model.EventBus.SelectedPlaceEvent
import com.example.kotlinuberrider.Remote.GoogleApi
import com.example.kotlinuberrider.Remote.RetrofitClient

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.example.kotlinuberrider.databinding.ActivityRequestDriverBinding
import com.example.kotlinuberrider.databinding.LayoutConfirmPickupBinding
import com.example.kotlinuberrider.databinding.LayoutConfirmUberBinding
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.google.maps.android.ui.IconGenerator
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import org.w3c.dom.Text
import java.io.IOException

class RequestDriverActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityRequestDriverBinding
    private lateinit var confirmUberBinding: LayoutConfirmUberBinding
    private lateinit var confirmPickupBinding: LayoutConfirmPickupBinding
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

    override fun onStart() {
        if (!EventBus.getDefault().isRegistered(true)){
            EventBus.getDefault().register(this)
        }
        super.onStart()
    }

    override fun onStop() {
        compositeDisposable.clear()
        if (EventBus.getDefault().hasSubscriberForEvent(SelectedPlaceEvent::class.java)){
            EventBus.getDefault().removeStickyEvent(SelectedPlaceEvent::class.java)
        }
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRequestDriverBinding.inflate(layoutInflater)
        confirmUberBinding = binding.layoutConfirmUber
        confirmPickupBinding = binding.layoutConfirmPickup
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
//        Log.d("event", event.toString())
        selectedPlaceEvent = event
    }

    private fun initData() {
        googleApi = RetrofitClient.instance!!.create(GoogleApi::class.java)

        confirmUberBinding.btnConfirmUber.setOnClickListener {
            confirmPickupBinding.cvConfirmPickup.visibility = View.VISIBLE
            confirmUberBinding.cvConfirmUber.visibility = View.GONE

            setDataPickup()
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
                    val startAddress = legsObject.getString("start_address")
                    val endAddress = legsObject.getString("end_address")

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
}