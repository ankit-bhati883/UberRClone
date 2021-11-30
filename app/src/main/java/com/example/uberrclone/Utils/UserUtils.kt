package com.example.uberrclone.Utils

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.RelativeLayout
import com.example.uberrclone.Remote.IFCMService
import com.example.uberrclone.Remote.RetroFitFCMClient
import com.example.uberrclone.comon.common
import com.example.uberrclone.model.DriverGeoModel
import com.example.uberrclone.model.EventBus.SelectedPlaceEvent
import com.example.uberrclone.model.FCMSendData
import com.example.uberrclone.model.Tokenmodel
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import retrofit2.create
import java.lang.StringBuilder

object UserUtils {
    fun updateuser(
        view: View?,
        updateData:Map<String,Any>
    ){
        Log.d("UserUtils", updateData.toString())
        FirebaseDatabase.getInstance().getReference(common.RIDERS_INFO_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .updateChildren(updateData)
            .addOnFailureListener { e->
                Snackbar.make(view!!,e.message!!, Snackbar.LENGTH_LONG).show()
            }.addOnSuccessListener {
                Snackbar.make(view!!,"update info successfully", Snackbar.LENGTH_LONG).show()
            }
    }
    fun updatetoken(
        context: Context,
        token:String
    ){
        val tokenmodel= Tokenmodel()
        tokenmodel.token=token

        FirebaseDatabase.getInstance()
            .getReference(common.TOKEN_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .setValue(tokenmodel)
            .addOnFailureListener { e-> Log.e("UserUtils token update",""+e.message) }
            .addOnSuccessListener {  }
    }

    fun sendRequestToDriver(context: Context, mainLayout: RelativeLayout?, foundDriver: DriverGeoModel?, selectedPlaceEvent: SelectedPlaceEvent) {

        val compositeDisposable=CompositeDisposable()
        val ifcmService=RetroFitFCMClient.instance!!.create(IFCMService::class.java)
        Log.d("UserUtils","61     ${foundDriver!!.key}")
        //get token
        FirebaseDatabase.getInstance()
            .getReference(common.TOKEN_REFERENCE)
            .child(foundDriver!!.key!!)
            .addListenerForSingleValueEvent(object :ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d("UserUtils","68")
                    if(snapshot.exists()){
                        Log.d("UserUtils","70")
                        val tokenmodel=snapshot.getValue(Tokenmodel::class.java)
                        Log.d("UserUtils","72 ${tokenmodel!!.token}")
                        val notificationData:MutableMap<String,String> =HashMap()
                        notificationData[common.NOTI_TITLE] = common.REQUEST_DRIVER_TITLE
                        notificationData[common.NOTI_BODY] = "This message repersent for Request Driver action"
                        notificationData[common.RIDER_KEY] = FirebaseAuth.getInstance().currentUser!!.uid

                        notificationData[common.PICKUP_LOCATION_STRING] = selectedPlaceEvent.originaddress
                        notificationData[common.PICKUP_LOCATION] = StringBuilder()
                            .append(selectedPlaceEvent.origin.latitude)
                            .append(",")
                            .append(selectedPlaceEvent.origin.longitude)
                            .toString()


                        notificationData[common.DESTINATION_LOCATION_STRING] = selectedPlaceEvent.destinationAddress
                        notificationData[common.DESTINATION_LOCATION] = StringBuilder()
                            .append(selectedPlaceEvent.destination.latitude)
                            .append(",")
                            .append(selectedPlaceEvent.destination.longitude)
                            .toString()

                        //New Information
                        notificationData[common.RIDER_DISTANCE_TEXT] = selectedPlaceEvent.distanceText!!
                        notificationData[common.RIDER_DISTANCE_VALUE] = selectedPlaceEvent.distanceValue.toString()
                        notificationData[common.RIDER_DURATION_TEXT] = selectedPlaceEvent.durationText!!
                        notificationData[common.RIDER_DURATION_VALUE] = selectedPlaceEvent.durationValue.toString()
                        notificationData[common.RIDER_TOTAL_FEE] = selectedPlaceEvent.totalFare.toString()


                        val fcmSendData=FCMSendData(tokenmodel!!.token,notificationData)
                        Log.d("UserUtils","82")
                        compositeDisposable.add(ifcmService.sendNotification(fcmSendData)!!
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({fcmResponse->
                                if(fcmResponse!!.success==0)
                                {
                                    compositeDisposable.clear()
                                    Log.d("UserUtils","90")
                                    Snackbar.make(mainLayout!!,"Request Driver Failed",Snackbar.LENGTH_LONG).show()
                                }
                            },{t:Throwable?->
                                compositeDisposable.clear()
                                Log.d("UserUtils","95")
                                Snackbar.make(mainLayout!!,t!!.message!!,Snackbar.LENGTH_LONG).show()
                                Log.d("UserUtils","97  ${t.message}")
                            }))
                    }else{
                        Snackbar.make(mainLayout!!,"Token Not Found",Snackbar.LENGTH_LONG).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.d("UserUtils","103")
                    Snackbar.make(mainLayout!!,error.message,Snackbar.LENGTH_LONG).show()
                }

            })


    }
}