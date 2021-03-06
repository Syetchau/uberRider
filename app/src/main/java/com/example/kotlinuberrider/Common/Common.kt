package com.example.kotlinuberrider.Common

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.NotificationCompat
import com.example.kotlinuberrider.Model.Animation
import com.example.kotlinuberrider.Model.DriverGeo
import com.example.kotlinuberrider.Model.RiderInfo
import com.example.kotlinuberrider.R
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.maps.android.ui.IconGenerator
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.abs
import kotlin.math.atan

object Common {
    val driversSubscribe: MutableMap<String, Animation> = HashMap()
    val markerList: HashMap<String, Marker> = HashMap()
    var currentRider: RiderInfo?= null
    val driversFound: MutableMap<String, DriverGeo> = HashMap()

    const val RIDER_INFO_REFERENCE: String = "RiderInfo"
    const val DRIVER_INFO_REFERENCE: String = "DriverInfo"
    const val DRIVERS_LOCATION_REFERENCE: String = "DriversLocation"
    const val TOKEN_REFERENCE: String = "Token"
    const val NOTIFICATION_TITLE: String = "title"
    const val NOTIFICATION_BODY: String = "body"
    const val PICKUP_LOCATION: String = "PickupLocation"
    const val DESTINATION_LOCATION: String = "DestinationLocation"
    const val RIDER_KEY: String = "RiderKey"
    const val PICKUP_LOCATION_STRING = "PickupLocationString"
    const val DESTINATION_LOCATION_STRING = "DestinationLocationString"
    const val REQUEST_DRIVER_TITLE: String = "RequestDriver"
    const val REQUEST_DRIVER_DECLINE: String = "Decline"
    const val REQUEST_DRIVER_ACCEPT: String = "Accept"
    const val TRIP_KEY: String = "TripKey"
    const val TRIP: String = "Trips"
    const val REQUEST_DRIVER_DECLINE_AND_REMOVE_TRIP: String = "DeclineAndRemoveTrip"
    const val RIDER_REQUEST_COMPLETE_TRIP: String = "RequestCompleteTripToRider"
    const val RIDER_DISTANCE_TEXT: String = "DistanceRider"
    const val RIDER_DISTANCE_VALUE: String = "DistanceValueRider"
    const val RIDER_DURATION_TEXT: String = "DurationRider"
    const val RIDER_DURATION_VALUE: String = "DurationValueRider"
    const val RIDER_TOTAL_FEE: String = "TotalFeeRider"
    const val BASE_FARE: Double = 2.0  //baseFare

    fun buildWelcomeMessage(): String {
        return StringBuilder("Welcome, ")
            .append(currentRider!!.firstName)
            .append("")
            .append(currentRider!!.lastName)
            .toString()
    }

    fun showNotification(context: Context, id: Int, title: String?, body: String?, intent: Intent?) {
        var pendingIntent: PendingIntent?= null
        val notificationChannelId = "kotlin_uber_driver"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (intent != null) {
            pendingIntent = PendingIntent.getActivity(
                context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                notificationChannelId, "Uber Remake", NotificationManager.IMPORTANCE_HIGH)
            notificationChannel.description = "Uber Remake"
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.RED
            notificationChannel.vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            notificationChannel.enableVibration(true)
            notificationManager.createNotificationChannel(notificationChannel)
        }
        val builder = NotificationCompat.Builder(context, notificationChannelId)
        builder
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_VIBRATE)
            .setSmallIcon(R.drawable.ic_baseline_directions_car)
            .setLargeIcon(
                BitmapFactory.decodeResource(context.resources,
                R.drawable.ic_baseline_directions_car))

        if(pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }
        val notification = builder.build()
        notificationManager.notify(id, notification)
    }

    fun buildName(firstName: String?, lastName: String?): String? {
        return StringBuilder(firstName!!).append(" ").append(lastName).toString()
    }

    fun decodePoly(encoded: String): ArrayList<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            val p = LatLng(
                lat.toDouble() / 1E5,
                lng.toDouble() / 1E5
            )
            poly.add(p)
        }
        return poly
    }

    fun getBearing(begin: LatLng, end: LatLng): Float {
        val lat = abs(begin.latitude - end.latitude)
        val lng = abs(begin.longitude - end.longitude)
        if (begin.latitude < end.latitude && begin.longitude < end.longitude)
            return Math.toDegrees(atan(lng / lat)).toFloat()
        else if (begin.latitude >= end.latitude && begin.longitude < end.longitude)
            return (90 - Math.toDegrees(atan(lng / lat)) + 90).toFloat()
        else if (begin.latitude >= end.latitude && begin.longitude >= end.longitude)
            return (Math.toDegrees(atan(lng / lat)) + 180).toFloat()
        else if (begin.latitude < end.latitude && begin.longitude >= end.longitude)
            return (90 - Math.toDegrees(atan(lng / lat)) + 270).toFloat()
        return (-1).toFloat()
    }

    fun setWelcomeMessage(tvWelcome: AppCompatTextView) {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 1..12 -> {
                tvWelcome.text = StringBuilder("Good Morning.")
            }
            in 13..17 -> {
                tvWelcome.text = StringBuilder("Good Afternoon.")
            }
            else -> {
                tvWelcome.text = StringBuilder("Good Evening.")
            }
        }
    }

    fun formatDuration(duration: String): CharSequence? {
        return if (duration.contains("mins")) {
            duration.substring(0, duration.length-1)
        } else {
            duration
        }
    }

    fun formatAddress(startAddress: String): CharSequence? {
        val firstIndexComma = startAddress.indexOf(",")
        return startAddress.substring(0,firstIndexComma)
    }

    fun valueAnimate(duration: Int, listener: ValueAnimator.AnimatorUpdateListener): ValueAnimator {
        val valueAnimator = ValueAnimator.ofFloat(0f, 100f)
        valueAnimator.duration = duration.toLong()
        valueAnimator.addUpdateListener(listener)
        valueAnimator.repeatCount = ValueAnimator.INFINITE
        valueAnimator.repeatMode = ValueAnimator.RESTART
        valueAnimator.start()
        return valueAnimator
    }

    fun createIconWithDuration(context: Context, duration: String): Bitmap? {
        val view = LayoutInflater.from(context).inflate(R.layout.pickup_info_with_duration, null)
        val tvTime = view.findViewById<View>(R.id.tv_duration) as TextView
        tvTime.text = getNumberFromText(duration)
        val iconGenerator = IconGenerator(context)
        iconGenerator.setContentView(view)
        iconGenerator.setBackground(ColorDrawable(Color.TRANSPARENT))
        return iconGenerator.makeIcon()
    }

    private fun getNumberFromText(duration: String): String {
        return duration.substring(0, duration.indexOf(" "))
    }

    fun calculateFeeBasedOnMetres(metres: Int): Double {
        return if (metres <= 1000) {
            BASE_FARE
        } else {
            (BASE_FARE/1000)* metres
        }
    }
}