package com.example.kotlinuberrider.Callback

import com.example.kotlinuberrider.Model.DriverGeo

interface FirebaseDriverInfoListener {
    fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeo?)
}