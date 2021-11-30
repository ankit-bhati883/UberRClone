package com.example.uberrclone.callback

import com.example.uberrclone.model.TripPlanModel

interface ITripDetailListener {
    fun onTripDetailLoadSuccess(tripPlanModel: TripPlanModel)
}