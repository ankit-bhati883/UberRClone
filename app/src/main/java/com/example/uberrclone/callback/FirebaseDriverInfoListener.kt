package com.example.uberrclone.callback

import com.example.uberrclone.model.DriverGeoModel

interface FirebaseDriverInfoListener {
    fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?)
}