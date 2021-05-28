package com.example.kotlinuberrider.Model

import com.firebase.geofire.GeoLocation

class DriverGeo {
    var key: String?= null
    var geoLocation: GeoLocation?= null
    var driverInfo: DriverInfo?= null
    var isDecline: Boolean?= false

    constructor(key: String?, geoLocation: GeoLocation?) {
        this.key = key
        this.geoLocation = geoLocation
    }
}