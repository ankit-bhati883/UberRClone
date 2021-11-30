package com.example.uberrclone.comon

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
import androidx.core.app.NotificationCompat
import com.example.uberrclone.R
import com.example.uberrclone.model.AnimationModel
import com.example.uberrclone.model.DriverGeoModel
import com.example.uberrclone.model.RiderInfoData
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.ui.IconGenerator
import org.w3c.dom.Text
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet


object common {
    fun buildWelcomeMessange():String{
        if(currentUser!=null){
            return StringBuilder("Welcome,")
                .append(" ")
                .append(currentUser!!.first_name)
                .append(" ")
                .append(currentUser!!.last_name)
                .toString()}
        else return StringBuilder("Welcome,").toString()
    }

    val BASE_FARE: Double=2.0
    const val RIDER_TOTAL_FEE: String="TotalFeeRider"
    const val RIDER_DURATION_VALUE: String="DurationRiderValue"
    const val RIDER_DURATION_TEXT: String="DurationRider"
    const val RIDER_DISTANCE_VALUE: String="DistanceRiderValue"
    const val RIDER_DISTANCE_TEXT: String="RiderDistance"
    const val RIDER_REQUEST_COMPLETE_TRIP: String="RequestCompleteTripToRider"
    const val REQUEST_DRIVER_DECLINE_AND_REMOVE_TRIP: String="DeclineAndRemoveTrip"
    const val TRIP: String="Trips"
    const val TRIP_KEY: String="TripKey"
    const val REQUEST_DRIVER_ACCEPT: String="Accept"
    const val DESTINATION_LOCATION: String="DestinationLocation"
    const val DESTINATION_LOCATION_STRING: String="DestinationLocationString"
    const val PICKUP_LOCATION_STRING: String="PickupLocationString"
    val REQUEST_DRIVER_DECLINE: String?="Decline"
    const val RIDER_KEY: String="RiderKey"
    const val PICKUP_LOCATION: String="PickupLocation"
    const val REQUEST_DRIVER_TITLE: String="RequestDriver"
    val driversSubscribe: MutableMap<String, AnimationModel> = HashMap<String,AnimationModel>()
    val markerList:MutableMap<String,Marker> = HashMap<String,Marker>()
    const val DRIVER_INFO_REFERENCE: String="DriverInfo"
    val driversFound:MutableMap<String,DriverGeoModel> = HashMap<String,DriverGeoModel>()
    const val TOKEN_REFERENCE: String="Token"
    var currentUser: RiderInfoData? = null
    const val RIDERS_INFO_REFERENCE: String="Riders"
    const val DRIVERS_LOCATION_REFERENCE: String="DriversLocation"

    const val NOTI_BODY="body"
    const val NOTI_TITLE="title"

    fun showNotification(context: Context, id:Int, title:String?, body:String?, intent: Intent?){
        var pendingIntent: PendingIntent?=null

        if(intent!=null)
            pendingIntent= PendingIntent.getActivities(context,id, arrayOf(intent), PendingIntent.FLAG_UPDATE_CURRENT)
        val NOTIFICATION_CHANNEL_ID="Ankit_uber_clone"
        val notificationManager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            val notificationChannel= NotificationChannel(NOTIFICATION_CHANNEL_ID,"Uber Clone",
                NotificationManager.IMPORTANCE_HIGH)
            notificationChannel.description="Uber Clone"
            notificationChannel.enableLights(true)
            notificationChannel.lightColor= Color.RED
            notificationChannel.vibrationPattern=longArrayOf(0,1000,500,1000)
            notificationChannel.enableVibration(true)

            notificationManager.createNotificationChannel(notificationChannel)
        }
        val builder= NotificationCompat.Builder(context,NOTIFICATION_CHANNEL_ID)
        builder.setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_VIBRATE)
            .setSmallIcon(R.drawable.ic_baseline_directions_car_24)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_baseline_directions_car_24))
        if(pendingIntent!=null)
            builder.setContentIntent(pendingIntent!!)
        val notification=builder.build()
        notificationManager.notify(id,notification)
    }

    fun buildName(firstName: String?, lastName: String?): String?
    {
      return  java.lang.StringBuilder(firstName!!).append(" ").append(lastName).toString()
    }

    //GET BEARING
    fun getBearing(begin: LatLng, end: LatLng): Float {
        //You can copy this function by link at description
        val lat = Math.abs(begin.latitude - end.latitude)
        val lng = Math.abs(begin.longitude - end.longitude)
        if (begin.latitude < end.latitude && begin.longitude < end.longitude) return Math.toDegrees(
            Math.atan(lng / lat)
        )
            .toFloat() else if (begin.latitude >= end.latitude && begin.longitude < end.longitude) return (90 - Math.toDegrees(
            Math.atan(lng / lat)
        ) + 90).toFloat() else if (begin.latitude >= end.latitude && begin.longitude >= end.longitude) return (Math.toDegrees(
            Math.atan(lng / lat)
        ) + 180).toFloat() else if (begin.latitude < end.latitude && begin.longitude >= end.longitude) return (90 - Math.toDegrees(
            Math.atan(lng / lat)
        ) + 270).toFloat()
        return (-1).toFloat()
    }

    //DECODE POLY
    fun decodePoly(encoded: String): ArrayList<LatLng?>? {
        val poly= ArrayList<LatLng?>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
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

    fun setWelcomeMessage(txtWelcome: TextView) {
        val hour=Calendar.getInstance().get(Calendar.HOUR)
        if(hour>=1&&hour<=12)
            txtWelcome.setText(java.lang.StringBuilder("Good morning."))
        else if(hour>12&&hour<=17)
            txtWelcome.setText(java.lang.StringBuilder("Good afternoon."))
        else
            txtWelcome.setText(java.lang.StringBuilder("Good evening."))
    }

    fun formatDuration(duration: String): CharSequence? {
        if(duration.contains("mins"))
             return duration.substring(0,duration.length-1) //Remove letter "s"
        else
            return duration
    }

    fun formatAddress(startAddress: String): CharSequence? {
        val firstIndexComma=startAddress.indexOf(",")
        return startAddress.substring(0,firstIndexComma)
    }

    fun valueAnimate(duration: Int, listener: ValueAnimator.AnimatorUpdateListener ): ValueAnimator{
       val va:ValueAnimator=ValueAnimator.ofFloat(0F,100F)
        va.duration= duration.toLong()
        va.addUpdateListener(listener)
        va.repeatCount = ValueAnimator.INFINITE
        va.repeatMode=ValueAnimator.RESTART
        va.start()
        return va

    }

    fun createIconWithDuration(context: Context, duration: String): Bitmap? {
      val view=LayoutInflater.from(context).inflate(R.layout.pickup_info_with_duration_windows,null)
      val txt_time=view.findViewById<View>(R.id.txt_duration) as TextView
      txt_time.setText(getNumberFromText(duration!!))
      val generator=IconGenerator(context)
      generator.setContentView(view)
      generator.setBackground(ColorDrawable(Color.TRANSPARENT))
      return generator.makeIcon()
    }

    private fun getNumberFromText(s: String):String {
      return s.substring(0,s.indexOf(" "))
    }

    fun calculateFeeBaseOnMetters(metters: Int): Double {
        return if(metters<=1000)//less than or equal 1km
            BASE_FARE
        else
            (BASE_FARE/1000)*metters
    }


}