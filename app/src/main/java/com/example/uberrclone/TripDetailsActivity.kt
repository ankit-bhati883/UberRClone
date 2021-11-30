package com.example.uberrclone

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.example.uberrclone.callback.FirebaseFailedListener
import com.example.uberrclone.callback.ITripDetailListener
import com.example.uberrclone.comon.common
import com.example.uberrclone.model.EventBus.*
import com.example.uberrclone.model.TripPlanModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.android.synthetic.main.activity_request_driver.*
import kotlinx.android.synthetic.main.activity_trip_details.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.StringBuilder
import kotlin.random.Random

class TripDetailsActivity : AppCompatActivity(), ITripDetailListener, FirebaseFailedListener {
    lateinit var tripDetailListener: ITripDetailListener
    lateinit var firebaseFailedListener: FirebaseFailedListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_details)

        init()
    }

    private fun init() {
        tripDetailListener=this
        firebaseFailedListener=this
        btn_back.setOnClickListener { finish() }
    }

    override fun onTripDetailLoadSuccess(tripPlanModel: TripPlanModel) {
//Set data
        txt_date.text=tripPlanModel.timeText
        txt_price.text=StringBuilder("$").append(tripPlanModel.totalFee)
        txt_origin.text=tripPlanModel.originString
        txt_destination.text=tripPlanModel.destinationString
        txt_base_fare.text=StringBuilder("$").append(common.BASE_FARE)
        txt_distance.text=tripPlanModel.distanceText
        txt_time.text=tripPlanModel.durationText
        //Show layout
        layout_detail.visibility=View.VISIBLE
        progress_ring.visibility=View.GONE
    }

    override fun onFirebaseFailed(message: String) {
        Snackbar.make(main_layout,message,Snackbar.LENGTH_LONG).show()
    }
    override fun onStart() {
        super.onStart()
        if(!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
    }

    override fun onStop() {

        if(EventBus.getDefault().hasSubscriberForEvent(LoadTripDetailEvent::class.java))
            EventBus.getDefault().removeStickyEvent(LoadTripDetailEvent::class.java)

        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onLoadTripDetailEvent(event: DriverCompleteTripEvent){
        FirebaseDatabase.getInstance()
            .getReference(common.TRIP)
            .child(event.tripId)
            .addListenerForSingleValueEvent(object:ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    if(snapshot.exists()){
                        val model=snapshot.getValue(TripPlanModel::class.java)
                        tripDetailListener.onTripDetailLoadSuccess(model!!)
                    }
                    else
                        firebaseFailedListener.onFirebaseFailed("Cannot find Trip key")
                }

                override fun onCancelled(error: DatabaseError) {
                    firebaseFailedListener.onFirebaseFailed(error.message)
                }

            })
    }
}