package com.example.kotlinuberrider.Model

import com.firebase.geofire.GeoLocation

class DriverGeo {
    var key: String?= null
    var geoLocation: GeoLocation?= null
    var driverInfo: DriverInfo?= null

    constructor(key: String?, geoLocation: GeoLocation?) {
        this.key = key
        this.geoLocation = geoLocation
    }
}