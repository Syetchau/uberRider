package com.example.kotlinuberrider.Callback

import com.example.kotlinuberrider.Model.TripPlan

interface TripDetailListener {
    fun onTripDetailLoadSuccess(tripPlan: TripPlan)
}