package com.example.kotlinuberrider.ui.home

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.kotlinuberrider.Callback.FirebaseDriverInfoListener
import com.example.kotlinuberrider.Callback.FirebaseFailedListener
import com.example.kotlinuberrider.Common.Common
import com.example.kotlinuberrider.Model.Animation
import com.example.kotlinuberrider.Model.DriverGeo
import com.example.kotlinuberrider.Model.DriverInfo
import com.example.kotlinuberrider.Model.EventBus.SelectedPlaceEvent
import com.example.kotlinuberrider.Model.GeoQuery
import com.example.kotlinuberrider.R
import com.example.kotlinuberrider.Remote.GoogleApi
import com.example.kotlinuberrider.Remote.RetrofitClient
import com.example.kotlinuberrider.RequestDriverActivity
import com.example.kotlinuberrider.Utils.LocationUtils
import com.example.kotlinuberrider.databinding.FragmentHomeBinding
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.json.JSONObject
import java.io.IOException
import java.util.*

class HomeFragment : Fragment(), OnMapReadyCallback, FirebaseDriverInfoListener {

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment: SupportMapFragment
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var slidingUpPanelLayout: SlidingUpPanelLayout
    private lateinit var autoCompleteSupportFragment: AutocompleteSupportFragment
    private lateinit var tvWelcome: AppCompatTextView

    //location
    private var locationRequest: LocationRequest?= null
    private var locationCallback: LocationCallback?= null
    private var fusedLocationProviderClient: FusedLocationProviderClient?= null

    //load driver
    private var distance = 1.0
    private var limitRange = 10.0
    private var previousLocation: Location?= null
    private var currentLocation: Location?= null
    private var firstTime = true

    //listener
    private lateinit var firebaseDriverInfoListener: FirebaseDriverInfoListener
    private lateinit var firebaseFailedListener: FirebaseFailedListener

    //city
    private var cityName = ""
    private val compositeDisposable = CompositeDisposable()
    private lateinit var googleApi: GoogleApi

    private var isNextLaunch: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        initViews(root)
        initData()
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        fusedLocationProviderClient!!.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun onStop() {
        compositeDisposable.clear()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if(isNextLaunch) {
            loadAvailableDrivers()
        } else {
            isNextLaunch = true
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        Dexter.withContext(requireContext())
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object: PermissionListener{
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                    if (ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Snackbar.make(requireView(), getString(R.string.permission_required),
                            Snackbar.LENGTH_SHORT).show()
                        return
                    }
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    mMap.setOnMapClickListener {
                        fusedLocationProviderClient!!.lastLocation
                            .addOnFailureListener { e ->
                                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                            }
                            .addOnSuccessListener { location ->
                                val userLatLng = LatLng(location.latitude, location.longitude)
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 18f))
                            }
                    }
                    val view = mapFragment.requireView()
                        .findViewById<View>("1".toInt()).parent as View
                    val locationBtn = view.findViewById<View>("2".toInt())
                    val params = locationBtn.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                    params.bottomMargin = 250

                    //update location
                    buildLocationRequest()
                    buildLocationCallback()
                    updateLocation()
                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Toast.makeText(context, "Permission " + p0!!.permissionName+
                            " needed for run app", Toast.LENGTH_SHORT).show()
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {

                }
            }).check()

        //enable zoom
        mMap.uiSettings.isZoomControlsEnabled = true
        try {
            val success = googleMap.setMapStyle(
                MapStyleOptions.
            loadRawResourceStyle(requireContext(), R.raw.uber_maps_style))
            if (!success) {
                Log.e("ERROR GET MAP STYLE", "parsing error")
            }
        } catch (e: Resources.NotFoundException) {
            Log.e("ERROR", e.message!!)
        }
    }

    override fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeo?) {
        //if already has marker with key, don't show again
        if(!Common.markerList.containsKey(driverGeoModel!!.key)){
            Common.markerList[driverGeoModel.key!!] =
                mMap.addMarker(MarkerOptions().position(LatLng(
                driverGeoModel.geoLocation!!.latitude, driverGeoModel.geoLocation!!.longitude))
                .flat(true)
                .title(Common.buildName(driverGeoModel.driverInfo!!.firstName, driverGeoModel.driverInfo!!.lastName))
                .snippet(driverGeoModel.driverInfo!!.phoneNumber)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
            )

            if(!TextUtils.isEmpty(cityName)){
                val driverLocation = FirebaseDatabase.getInstance()
                    .getReference(Common.DRIVERS_LOCATION_REFERENCE)
                    .child(cityName)
                    .child(driverGeoModel.key!!)
                driverLocation.addValueEventListener(object: ValueEventListener{
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.hasChildren()){
                            if (Common.markerList[driverGeoModel.key!!] != null){
                                //remove marker from map
                                val marker = Common.markerList[driverGeoModel.key]
                                marker!!.remove()
                                Common.markerList.remove(driverGeoModel.key)
                                Common.driversSubscribe.remove(driverGeoModel.key)
                                if (Common.driversFound != null &&
                                    Common.driversFound[driverGeoModel.key] != null) {
                                        Common.driversFound.remove(driverGeoModel.key)
                                        driverLocation.removeEventListener(this)
                                }
                            }
                        } else{
                            if (Common.markerList[driverGeoModel.key!!] != null) {
                                val geoQuery = snapshot.getValue(GeoQuery::class.java)
                                val animationModel = Animation(false, geoQuery!!)
                                if(Common.driversSubscribe[driverGeoModel.key!!] != null){
                                    val marker = Common.markerList[driverGeoModel.key!!]
                                    val oldPosition = Common.driversSubscribe[driverGeoModel.key!!]
                                    val from = StringBuilder()
                                        .append(oldPosition!!.geoQuery.l!![0])
                                        .append(",")
                                        .append(oldPosition.geoQuery.l!![1])
                                        .toString()

                                    val to = StringBuilder()
                                        .append(animationModel.geoQuery.l!![0])
                                        .append(",")
                                        .append(animationModel.geoQuery.l!![1])
                                        .toString()

                                    moveAnimationMarker(
                                        driverGeoModel.key!!, animationModel, marker, from, to)
                                } else{
                                    // first location init
                                    Common.driversSubscribe[driverGeoModel.key!!] = animationModel
                                }
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Snackbar.make(requireView(), error.message, Snackbar.LENGTH_SHORT).show()
                    }
                })
            }
        }
    }

    private fun initViews(root: View) {
        slidingUpPanelLayout = root.findViewById(R.id.fragment_home) as SlidingUpPanelLayout
        tvWelcome = root.findViewById(R.id.tv_welcome) as AppCompatTextView
        Common.setWelcomeMessage(tvWelcome)
    }

    private fun initData() {
        // Initialize Places.
        Places.initialize(requireContext(), getString(R.string.google_maps_key))
        autoCompleteSupportFragment = childFragmentManager
            .findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment
        autoCompleteSupportFragment.setPlaceFields(
            listOf(
                Place.Field.ID,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG,
                Place.Field.NAME))
        autoCompleteSupportFragment.setOnPlaceSelectedListener(object: PlaceSelectionListener{
            override fun onPlaceSelected(place: Place) {
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Snackbar.make(requireView(), getString(R.string.permission_required),
                        Snackbar.LENGTH_SHORT).show()
                    return
                }
                fusedLocationProviderClient!!.lastLocation
                    .addOnSuccessListener {
                        val origin = LatLng(it.latitude, it.longitude)
                        val destination = LatLng(place.latLng!!.latitude, place.latLng!!.longitude)
                        startActivity(Intent(requireContext(), RequestDriverActivity::class.java))
                        EventBus.getDefault().postSticky(
                            SelectedPlaceEvent(origin, destination, place.address!!))
                    }
            }

            override fun onError(status: Status) {
                Snackbar.make(requireView(), ""+status.statusMessage!!, Snackbar.LENGTH_SHORT).show()
            }
        })

        googleApi = RetrofitClient.instance!!.create(GoogleApi::class.java)
        firebaseDriverInfoListener = this

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Snackbar.make(mapFragment.requireView(), getString(R.string.permission_required),
                Snackbar.LENGTH_SHORT).show()
            return
        }

        buildLocationRequest()
        buildLocationCallback()
        updateLocation()
        loadAvailableDrivers()
    }

    private fun buildLocationRequest() {
        if (locationRequest == null) {
            locationRequest = LocationRequest()
            locationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            locationRequest!!.fastestInterval = 3000
            locationRequest!!.interval = 5000
            locationRequest!!.smallestDisplacement = 10f
        }
    }

    private fun buildLocationCallback() {
        if (locationCallback == null) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)

                    val pos = LatLng(
                        locationResult.lastLocation.latitude,
                        locationResult.lastLocation.longitude
                    )
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 18f))

                    // if user has change location, calculate & load driver again
                    if (firstTime) {
                        previousLocation = locationResult.lastLocation
                        currentLocation = locationResult.lastLocation

                        firstTime = false
                    } else {
                        previousLocation = currentLocation
                        currentLocation = locationResult.lastLocation
                    }

                    setRestrictPlacesInCountry(locationResult.lastLocation)

                    if (previousLocation!!.distanceTo(currentLocation) / 1000 <= limitRange) {
                        loadAvailableDrivers()
                    }
                }
            }
        }
    }

    private fun updateLocation() {
        if (fusedLocationProviderClient == null) {
            fusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(requireContext())
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
//            Snackbar.make(requireView(), getString(R.string.permission_required),
//                Snackbar.LENGTH_SHORT).show()
                return
            }
            fusedLocationProviderClient!!.requestLocationUpdates(
                locationRequest, locationCallback, Looper.myLooper()
            )
        }
    }

    private fun loadAvailableDrivers() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Snackbar.make(requireView(), getString(R.string.permission_required),
                Snackbar.LENGTH_SHORT).show()
        }
        fusedLocationProviderClient!!.lastLocation
            .addOnFailureListener { e ->
                Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_SHORT).show()
            }
            .addOnSuccessListener { location ->

                cityName = LocationUtils.getAddressFromLocation(requireContext(), location)
                if (!TextUtils.isEmpty(cityName)) {
                        val driversLocationRef = FirebaseDatabase.getInstance()
                            .getReference(Common.DRIVERS_LOCATION_REFERENCE)
                            .child(cityName)
                        val geofire = GeoFire(driversLocationRef)
                        val geoQuery = geofire.queryAtLocation(
                            GeoLocation(location.latitude, location.longitude), distance
                        )
                        geoQuery.removeAllListeners()
                        geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
                            override fun onKeyEntered(key: String?, location: GeoLocation?) {
                                //Common.driversFound.add(DriverGeo(key, location))
                                if(!Common.driversFound.containsKey(key)){
                                    Common.driversFound[key!!] = DriverGeo(key, location)
                                }
                            }

                            override fun onKeyExited(key: String?) {

                            }

                            override fun onKeyMoved(key: String?, location: GeoLocation?) {

                            }

                            override fun onGeoQueryReady() {
                                if (distance <= limitRange) {
                                    distance++
                                    loadAvailableDrivers()
                                } else {
                                    distance = 0.0
                                    addDriverMaker()
                                }
                            }

                            override fun onGeoQueryError(error: DatabaseError?) {
                                Snackbar.make(
                                    requireView(), error!!.message,
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                        })

                        driversLocationRef.addChildEventListener(object : ChildEventListener {
                            override fun onChildAdded(
                                snapshot: DataSnapshot,
                                previousChild: String?
                            ) {
                                //got new drivers
                                val geoQueryModel = snapshot.getValue(GeoQuery::class.java)
                                val geoLocation =
                                    GeoLocation(geoQueryModel!!.l!![0], geoQueryModel.l!![1])
                                val driverGeo = DriverGeo(snapshot.key, geoLocation)
                                val newDriverLocation = Location("")
                                newDriverLocation.latitude = geoLocation.latitude
                                newDriverLocation.longitude = geoLocation.longitude
                                val newDistance =
                                    location.distanceTo(newDriverLocation) / 1000 // in km
                                if (newDistance <= limitRange) {
                                    findDriverByKey(driverGeo)
                                }
                            }

                            override fun onChildChanged(
                                snapshot: DataSnapshot,
                                previousChild: String?
                            ) {

                            }

                            override fun onChildRemoved(snapshot: DataSnapshot) {

                            }

                            override fun onChildMoved(
                                snapshot: DataSnapshot,
                                previousChild: String?
                            ) {

                            }

                            override fun onCancelled(error: DatabaseError) {
                                Snackbar.make(
                                    requireView(), error.message,
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                        })
                } else {
                    Snackbar.make(requireView(), getString(R.string.city_name_not_found),
                        Snackbar.LENGTH_LONG).show()
                }
            }
    }

    private fun addDriverMaker() {
        if(Common.driversFound.size > 0) {
            Observable.fromIterable(Common.driversFound.keys)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ key: String? ->
                    findDriverByKey(Common.driversFound[key!!])
                },{
                    Snackbar.make(requireView(), it!!.message!!,
                        Snackbar.LENGTH_SHORT).show()
                })
        } else {
            Snackbar.make(requireView(), getString(R.string.drivers_not_found),
                Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun findDriverByKey(driverGeo: DriverGeo?) {
        FirebaseDatabase.getInstance()
            .getReference(Common.DRIVER_INFO_REFERENCE)
            .child(driverGeo!!.key!!)
            .addListenerForSingleValueEvent(object: ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.hasChildren()){
                        driverGeo.driverInfo = (snapshot.getValue(DriverInfo::class.java))
                        //Log.d("driverGeoKey", driverGeo.key!!)
                        Common.driversFound[driverGeo.key!!]!!.driverInfo =
                            (snapshot.getValue(DriverInfo::class.java))
                        firebaseDriverInfoListener.onDriverInfoLoadSuccess(driverGeo)
                    } else{
                        firebaseFailedListener.onFirebaseFailed(getString(R.string.key_not_found)+
                                driverGeo.key)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    firebaseFailedListener.onFirebaseFailed(error.message)
                }
            })
    }

    private fun moveAnimationMarker(key: String, newData: Animation, marker: Marker?,
                                    from: String, to: String) {
        if(!newData.isRun){
            //call Api
            compositeDisposable.add(googleApi.getDirection(
                "driving",
                "less_driving",
                from,
                to,
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
                            //polylineList = Common.decodePoly(polyline)
                            newData.polylineList = Common.decodePoly(polyline)
                        }
                        // moving
                        newData.index = -1
                        newData.next = 1

                        val runnable = object: Runnable{
                            override fun run() {
                                if(newData.polylineList != null && newData.polylineList!!.size > 1){
                                    if (newData.index < newData.polylineList!!.size - 2) {
                                            newData.index ++
                                            newData.next = newData.index + 1
                                            newData.start = newData.polylineList!![newData.index]
                                            newData.end = newData.polylineList!![newData.next]
                                    }
                                    val valueAnimator = ValueAnimator.ofInt(0,1)
                                    valueAnimator.duration = 3000
                                    valueAnimator.interpolator = LinearInterpolator()
                                    valueAnimator.addUpdateListener {
                                        newData.v = it.animatedFraction
                                        newData.lat = newData.v * newData.end!!.latitude +
                                                (1-newData.v) * newData.start!!.latitude
                                        newData.lng = newData.v * newData.end!!.longitude +
                                                (1-newData.v) * newData.start!!.longitude
                                        val newPos = LatLng(newData.lat, newData.lng)
                                        marker!!.position = newPos
                                        marker.setAnchor(0.5f, 0.5f)
                                        marker.rotation = Common.getBearing(newData.start!!, newPos)
                                    }
                                    valueAnimator.start()
                                    if (newData.index < newData.polylineList!!.size - 2) {
                                        newData.handler.postDelayed(this, 1500)
                                    } else if (newData.index < newData.polylineList!!.size - 1) {
                                        newData.isRun = false
                                        Common.driversSubscribe[key] = newData
                                    }
                                }
                            }
                        }
                        newData.handler.postDelayed(runnable, 1500)
                    } catch (e: IOException) {
                        Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_SHORT).show()
                    }
                })
        }
    }

    private fun setRestrictPlacesInCountry(location: Location) {
        try {
            val geoCoder = Geocoder(requireContext(), Locale.getDefault())
            val addressList = geoCoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addressList.size > 0) {
                autoCompleteSupportFragment.setCountry(addressList[0].countryCode)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}