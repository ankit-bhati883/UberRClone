package com.example.uberrclone

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.uberrclone.Remote.IGoogleAPI
import com.example.uberrclone.Remote.RetrofitClient
import com.example.uberrclone.Utils.UserUtils
import com.example.uberrclone.comon.common
import com.example.uberrclone.databinding.ActivityRequestDriverBinding
import com.example.uberrclone.model.DriverGeoModel
import com.example.uberrclone.model.EventBus.*
import com.example.uberrclone.model.TripPlanModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.ui.IconGenerator
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_request_driver.*
import kotlinx.android.synthetic.main.layout_confirm_picup.*
import kotlinx.android.synthetic.main.layout_confirm_uber.*
import kotlinx.android.synthetic.main.layout_driver_info.*
import kotlinx.android.synthetic.main.layout_finding_your_driver.*
import kotlinx.android.synthetic.main.origin_info_windows.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import java.lang.StringBuilder
import kotlin.random.Random


class RequestDriverActivity : AppCompatActivity(), OnMapReadyCallback {

    private var driverOldPosition: String=" "
    private var handler:Handler?=null
    private var v=0f
    private var lat=0.0
    private var lng=0.0
    private var index=0
    private var next=0
    private var start:LatLng?=null
    private var end:LatLng?=null

    private lateinit var mMap: GoogleMap

    private lateinit var mapFragment:SupportMapFragment

    private var selectedPlaceEvent:SelectedPlaceEvent?=null

    private  var txt_origin:TextView?=null

    //Effect
      var lastusercircle:Circle?=null
     val duration  = 1000
      var lastPulseAnimator:ValueAnimator?=null

    //slowly camera spinning
      var animator: ValueAnimator?=null
    private val DESIRED_NUM_OF_SPINS :Long =5
    private val DESIRED_SECONDS_PER_ONE_FULL_360_SPIN:Long =40



    //Routes
    private val compositeDisposable=CompositeDisposable()
    private lateinit var iGoogleAPI: IGoogleAPI
    private var blackPolyline2:Polyline?=null
    private var greyPolyline:Polyline?=null
    private var polylineOptions:PolylineOptions?=null
    private var blackPolylineOptions:PolylineOptions?=null
    private var polylineList:ArrayList<LatLng?>?=null
    private var originMarker:Marker?=null
    private var destinationMarker:Marker?=null

    private var lastDriverCall: DriverGeoModel?=null

    override fun onStart() {
        super.onStart()
        if(!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
    }

    override fun onStop() {
        compositeDisposable.clear()
        if(EventBus.getDefault().hasSubscriberForEvent(SelectedPlaceEvent::class.java))
            EventBus.getDefault().removeStickyEvent(SelectedPlaceEvent::class.java)

        if(EventBus.getDefault().hasSubscriberForEvent(DeclineRequestFromDriver::class.java))
            EventBus.getDefault().removeStickyEvent(DeclineRequestFromDriver::class.java)

        if(EventBus.getDefault().hasSubscriberForEvent(DriverAcceptTripEvent::class.java))
            EventBus.getDefault().removeStickyEvent(DriverAcceptTripEvent::class.java)

        if(EventBus.getDefault().hasSubscriberForEvent(DeclineRequestAndRemoveTripFromDriver::class.java))
            EventBus.getDefault().removeStickyEvent(DeclineRequestAndRemoveTripFromDriver::class.java)
        if(EventBus.getDefault().hasSubscriberForEvent(DriverCompleteTripEvent::class.java))
            EventBus.getDefault().removeStickyEvent(DriverCompleteTripEvent::class.java)

        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDriverCompleteTripEvent(event:DriverCompleteTripEvent){
        common.showNotification(
            this, Random.nextInt(),
            "Thank you",
            "Your trip"+ event.tripId+"has been complete", null
        )
        startActivity(Intent(this,TripDetailsActivity::class.java))
        EventBus.getDefault().postSticky(LoadTripDetailEvent(event.tripId))
        finish()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDriverAcceptTripEvent(event: DriverAcceptTripEvent)
    {
        Log.d("Ankit","onDriverAcceptTripEvent called")
        FirebaseDatabase.getInstance().getReference(common.TRIP)
            .child(event.tripid)
            .addListenerForSingleValueEvent(object :ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    if(snapshot.exists()){
                        Log.d("Ankit","snapshot exits()")
                      val tripPlanModel = snapshot.getValue(TripPlanModel::class.java)
                      mMap.clear()
                      fill_maps.visibility=View.GONE
                      if(animator!=null) animator!!.end()
                      val cameraPos=CameraPosition.Builder().target(mMap.cameraPosition.target)
                          .tilt(0f).zoom(mMap.cameraPosition.zoom).build()
                      mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))

                        //Get route
                        val driverLocation= StringBuilder()
                            .append(tripPlanModel!!.currentLat)
                            .append(",")
                            .append(tripPlanModel!!.currentLng)
                            .toString()

                        Log.d("Ankit","to called compositeDisposable")
                        compositeDisposable.add(iGoogleAPI.getDirections("driving",
                        "less_driving",
                        tripPlanModel!!.origin,driverLocation,
                        getString(R.string.google_api_key)
                        )!!
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe{returnResult->
                                Log.d("Ankit","in subscribe")
                                var blackPolylineOptions:PolylineOptions?=null
                                var polylineList:List<LatLng?>?=null
                                var blackPolyline:Polyline?=null
                                try{

                                    Log.d("Ankit","try 160")
                                    val jsonObject= JSONObject(returnResult)
                                    val jsonArray=jsonObject.getJSONArray("routes")
                                    for(i in 0 until jsonArray.length())
                                    {
                                        val route = jsonArray.getJSONObject(i)
                                        val poly =route.getJSONObject("overview_polyline")
                                        val polyline=poly.getString("points")
                                        polylineList = common.decodePoly(polyline)
                                    }


                                    blackPolylineOptions= PolylineOptions()
                                    blackPolylineOptions.color(Color.BLACK)
                                    blackPolylineOptions.width(5f)
                                    blackPolylineOptions.startCap(SquareCap())
                                    blackPolylineOptions.jointType(JointType.ROUND)
                                    blackPolylineOptions.addAll(polylineList!!)
                                    blackPolyline=mMap.addPolyline(blackPolylineOptions)

                                    //Add car icon for origin
                                    val objects =jsonArray.getJSONObject(0)
                                    val legs= objects.getJSONArray("legs")
                                    val legsObject=legs.getJSONObject(0)

                                    val time=legsObject.getJSONObject("duration")
                                    val duration=time.getString("text")


                                    val origin=LatLng(tripPlanModel!!.origin!!.split(",").get(0).toDouble()
                                    ,tripPlanModel!!.origin!!.split(",").get(1).toDouble())
                                    val destination=LatLng(tripPlanModel.currentLat,tripPlanModel.currentLng)

                                    val latLngBound=LatLngBounds.Builder().include(origin)
                                        .include(destination)
                                        .build()

                                    addPickupMarkerDuration(duration,origin)

                                    addDriverMarker(destination)


                                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBound,160))
                                    mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.cameraPosition!!.zoom-1))

                                    initDriverForMoving(event.tripid,tripPlanModel)

                                    confirm_uber_layout.visibility=View.GONE
                                    confirm_pickup_layout.visibility=View.GONE
                                    driver_info_layout.visibility=View.VISIBLE

                                    //Load driver avatar
                                    if(tripPlanModel!!.driverInfoData!!.avatar!=""){
                                    Glide.with(this@RequestDriverActivity)
                                        .load(tripPlanModel!!.driverInfoData!!.avatar)
                                        .into(img_driver)}



                                }
                                catch (e:java.lang.Exception){

                                    Log.d("RequestDriverActivity","252 "+e.message)
                                }
                            }
                        )


                    }
                    else
                        Snackbar.make(main_layout,"260 cannot found Trip event key" +event.tripid ,Snackbar.LENGTH_LONG).show()
                }

                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(main_layout,error.message+"264",Snackbar.LENGTH_LONG).show()
                }

            })
    }

    private fun initDriverForMoving(tripid: String, tripPlanModel: TripPlanModel) {

        driverOldPosition=StringBuilder().append(tripPlanModel.currentLat)
            .append(",").append(tripPlanModel.currentLng).toString()

        FirebaseDatabase.getInstance().getReference(common.TRIP)
            .child(tripid)
            .addValueEventListener(object :ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {

                    val newData = snapshot.getValue(TripPlanModel::class.java)
                    if (newData != null){
                    val driverNewPosition = StringBuilder().append(newData!!.currentLat)
                        .append(",").append(newData!!.currentLng).toString()
                    if (!driverOldPosition.equals(driverNewPosition)){

                    moveMarkerAnimation(destinationMarker!!, driverOldPosition, driverNewPosition)}
                }
                }
                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(main_layout,error.message,Snackbar.LENGTH_LONG).show()
                }

            })
    }

    private fun moveMarkerAnimation(marker: Marker, from: String, to: String) {

        compositeDisposable.add(iGoogleAPI.getDirections("driving",
            "less_driving",
            from,to,
            getString(R.string.google_api_key)
        )!!
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe{returnResult->
                try{
                    Log.d("Ankit","A305")
                    val jsonObject= JSONObject(returnResult)
                    val jsonArray=jsonObject.getJSONArray("routes")
                    for(i in 0 until jsonArray.length())
                    {
                        val route = jsonArray.getJSONObject(i)
                        val poly =route.getJSONObject("overview_polyline")
                        val polyline=poly.getString("points")
                        polylineList= common.decodePoly(polyline)
                    }


                    blackPolylineOptions= PolylineOptions()
                    blackPolylineOptions!!.color(Color.BLACK)
                    blackPolylineOptions!!.width(5f)
                    blackPolylineOptions!!.startCap(SquareCap())
                    blackPolylineOptions!!.jointType(JointType.ROUND)
                    blackPolylineOptions!!.addAll(polylineList!!)
                    blackPolyline2=mMap.addPolyline(blackPolylineOptions!!)

                    //Add car icon for origin
                    val objects =jsonArray.getJSONObject(0)
                    val legs= objects.getJSONArray("legs")
                    val legsObject=legs.getJSONObject(0)

                    val time=legsObject.getJSONObject("duration")
                    val duration=time.getString("text")

                    val bitmap=common.createIconWithDuration(this@RequestDriverActivity,duration)
                    originMarker!!.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap!!))

//Moving
                    val runnable=object:Runnable{
                        override fun run() {

                            if(index<polylineList!!.size-2){
                                index++
                                next=index+1
                                start=polylineList!![index]
                                if(start==null) Log.d("RequestDriverActivity","start------ $start")
                                end=polylineList!![next]
                                if(end==null) Log.d("RequestDriverActivity","end------ $end")
                            }

                            val valueAnimator=ValueAnimator.ofInt(0,1)

                            valueAnimator.duration=1500

                            valueAnimator.interpolator=LinearInterpolator()

                            valueAnimator.addUpdateListener {valueAnimatorNew->



                                v=valueAnimatorNew.animatedFraction

                                lat=v*end!!.latitude+(1-v)*start!!.latitude

                                lng=v*end!!.longitude+(1-v)*end!!.longitude

                                val newPos=LatLng(lat,lng)

                                marker.position=newPos

                                marker.setAnchor(0.5f,0.5f)

                                marker.rotation=common.getBearing(start!!,newPos)

                                mMap.moveCamera(CameraUpdateFactory.newLatLng(newPos))
                            }

                            valueAnimator.start()

                            if(index<polylineList!!.size-2){

                                handler!!.postDelayed(this,1500)
                                }

                        }

                    }
                    handler= Handler()
                    index=-1
                    next=1
                    handler!!.postDelayed(runnable,1500)
                    driverOldPosition=to//set new driver position




                }
                catch (e:java.lang.Exception){
                    Log.d("RequestDriverActivity"," "+e.message)
                }
            }
        )
    }

    private fun addDriverMarker(destination: LatLng) {

        destinationMarker=mMap.addMarker(MarkerOptions().position(destination).flat(true)
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.car)))

    }

    private fun addPickupMarkerDuration(duration: String, origin: LatLng) {

        val icon=common.createIconWithDuration(this@RequestDriverActivity,duration)!!
        originMarker=mMap.addMarker(MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon)).position(origin))

    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
fun onDeclineReceived(event: DeclineRequestFromDriver){
    if(lastDriverCall!=null){
        common.driversFound.get(lastDriverCall!!.key)!!.isDecline=true
        findNearbyDriver(selectedPlaceEvent!!)
    }
}

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDeclineAndRemoveTripReceive(event: DeclineRequestAndRemoveTripFromDriver){
        if(lastDriverCall!=null){
            if(common.driversFound.get(lastDriverCall!!.key)!=null)
                common.driversFound.get(lastDriverCall!!.key)!!.isDecline=true
            finish()
        }
    }
    @Subscribe(sticky = true,threadMode = ThreadMode.MAIN)
    fun onSelectPlaceEvent(event: SelectedPlaceEvent){
        selectedPlaceEvent=event
    }

    private lateinit var binding: ActivityRequestDriverBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRequestDriverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        init()
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
         mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    fun init(){
        iGoogleAPI=RetrofitClient.instance!!.create(IGoogleAPI::class.java)

        //Event


        btn_confirm_uber.setOnClickListener {
            confirm_pickup_layout.visibility=View.VISIBLE
            confirm_uber_layout.visibility=View.GONE

            setDataPickUp()
        }
        btn_confirm_pickup.setOnClickListener {

            if (mMap == null) return@setOnClickListener
            if(selectedPlaceEvent == null) return@setOnClickListener


            //clear map
                mMap.clear()
            //Tilt
            val cameraPosition = CameraPosition.Builder()
                .target(selectedPlaceEvent!!.origin)
                .tilt(45f)
                .zoom(16f)
                .build()

            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            // start animation

            addMarkerWithPulseAnimation()

        }



    }

    private fun addMarkerWithPulseAnimation() {

        confirm_pickup_layout.visibility=View.GONE
        fill_maps.visibility=View.VISIBLE
        finding_your_ride_layout.visibility=View.VISIBLE
        originMarker=mMap.addMarker(MarkerOptions()
            .icon(BitmapDescriptorFactory.defaultMarker())
            .position(selectedPlaceEvent!!.origin))

        addPulsatingEffect(selectedPlaceEvent!!)
    }

    private fun addPulsatingEffect(selectedPlaceEvent: SelectedPlaceEvent) {

        if(lastPulseAnimator!=null){

            lastPulseAnimator!!.cancel()}
        if(lastusercircle!=null){

            lastusercircle!!.center=selectedPlaceEvent.origin
        }

        lastPulseAnimator=common.valueAnimate( duration,object:ValueAnimator.AnimatorUpdateListener{
            override fun onAnimationUpdate(p0: ValueAnimator?) {
                if (lastusercircle != null) lastusercircle!!.radius =
                    p0!!.animatedValue.toString().toDouble() else {
                    lastusercircle = mMap.addCircle(
                        CircleOptions()
                            .center(selectedPlaceEvent.origin)
                            .radius(p0!!.animatedValue.toString().toDouble())
                            .strokeColor(Color.WHITE)
                            .fillColor(
                                ContextCompat.getColor(
                                    this@RequestDriverActivity,
                                    R.color.map_drker
                                )
                            )
                    )
                }
            }
            })
        //Start rotating camera
        startMapCameraSpinningAnimation(selectedPlaceEvent)
        }
//





    private fun startMapCameraSpinningAnimation(selectedPlaceEvent: SelectedPlaceEvent?) {

        if(animator!=null) animator!!.cancel()

        animator=ValueAnimator.ofFloat(0F,(DESIRED_NUM_OF_SPINS *360F).toFloat())

        animator!!.duration=(DESIRED_SECONDS_PER_ONE_FULL_360_SPIN*DESIRED_NUM_OF_SPINS*1000L).toLong()

        animator!!.interpolator=LinearInterpolator()

            animator!!.startDelay=100

        animator!!.addUpdateListener{valueAnimator ->
            val newBearingValue= valueAnimator.animatedValue as Float

            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
                .target(selectedPlaceEvent!!.origin)
                .zoom(16f)
                .tilt(45f)
                .bearing(newBearingValue)
                .build()
            ))
        }
        animator!!.start()
        findNearbyDriver(selectedPlaceEvent)
    }

    private fun findNearbyDriver(selectedPlaceEvent: SelectedPlaceEvent?) {

        if(common.driversFound.size>0){
            var min=0f
            var foundDriver:DriverGeoModel?=null
            Log.d("RequestDriverActivity"," $foundDriver")
            val currentRiderLocation=Location("")
            currentRiderLocation.longitude=selectedPlaceEvent!!.origin.longitude
            currentRiderLocation.latitude=selectedPlaceEvent!!.origin.latitude

            for(key in common.driversFound.keys){
                val driverLocation=Location("")
                driverLocation.longitude=common.driversFound[key]!!.geoLocation!!.longitude
                driverLocation.latitude=common.driversFound[key]!!.geoLocation!!.latitude

                //First,init min value and found driver if first driver in list
                if(min==0f){
                    min=driverLocation.distanceTo(currentRiderLocation)
                    if(!common.driversFound[key]!!.isDecline)
                    {
                        foundDriver=common.driversFound[key]
                        break //Exit loop becuz we already found driver
                    }
                    else continue //if already decline before , just skip and continue

                }
                else if(driverLocation.distanceTo(currentRiderLocation)<min){
                    min=driverLocation.distanceTo(currentRiderLocation)
                    if(!common.driversFound[key]!!.isDecline)
                    {
                        foundDriver=common.driversFound[key]
                        break //Exit loop becuz we already found driver
                    }
                    else continue //if already decline before , just skip and continue

                }
            }


           if(foundDriver!=null){
               UserUtils.sendRequestToDriver(this@RequestDriverActivity,
                   main_layout,
                   foundDriver,
                   selectedPlaceEvent!!)
               lastDriverCall=foundDriver
           }else{
               Toast.makeText(this,"There are no driver accept your request ",Toast.LENGTH_LONG).show()
               lastDriverCall=null
               finish()
           }
        }else{
            Snackbar.make(main_layout,"Drivers Not Found",Snackbar.LENGTH_LONG).show()
            lastDriverCall=null
            finish()
        }
    }

    override fun onDestroy() {
        if (animator!=null) animator!!.end()
        super.onDestroy()
    }

    private fun setDataPickUp() {
        txt_address_pickup.text=if(txt_origin!=null) txt_origin!!.text else "None"

        mMap.clear()
        addPickupMarker()
    }

    private fun addPickupMarker() {

        val view = LayoutInflater.from(applicationContext).inflate(R.layout.pickup_info_windows,null)


        val generator=IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = generator.makeIcon()

        originMarker=mMap.addMarker(MarkerOptions()
            .icon(BitmapDescriptorFactory.fromBitmap(icon))
            .position(selectedPlaceEvent!!.origin))


    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap



        drawPath(selectedPlaceEvent)

        try {

            val success=googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this,
            R.raw.uber_maps_style))

            if(!success)
                Snackbar.make(mapFragment.requireView(),"Load map style failed",Snackbar.LENGTH_LONG).show()
                Log.d("RequestDriverActivity","Load map style failed")

        }catch (e:Exception){
            Snackbar.make(mapFragment.requireView(),e.message!!,Snackbar.LENGTH_LONG).show()
            Log.d("RequestDriverActivity"," " +e.message)
        }
    }

    private fun drawPath(selectedPlaceEvent: SelectedPlaceEvent?) {
        //Request API

        compositeDisposable.add(iGoogleAPI.getDirections("driving",
            "less_driving",
            selectedPlaceEvent!!.originString,selectedPlaceEvent!!.destinationString,

                getString(R.string.google_api_key)

        )
        !!.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe{returnResult->
                Log.d("API_RETURN",returnResult)

                try{
//val jsonObject=JSONObject(returnResult)
                    val jsonObject= JSONObject(returnResult)
                    val jsonArray=jsonObject.getJSONArray("routes")
                    for(i in 0 until jsonArray.length())
                    {
                        val route = jsonArray.getJSONObject(i)
                        val poly =route.getJSONObject("overview_polyline")
                        val polyline=poly.getString("points")
                        polylineList= common.decodePoly(polyline)
                    }

                    polylineOptions= PolylineOptions()
                    polylineOptions!!.color(Color.GRAY)
                    polylineOptions!!.width(12f)
                    polylineOptions!!.startCap(SquareCap())
                    polylineOptions!!.jointType(JointType.ROUND)
                    polylineOptions!!.addAll(polylineList!!)
                    greyPolyline=mMap.addPolyline(polylineOptions!!)

                    blackPolylineOptions= PolylineOptions()
                    blackPolylineOptions!!.color(Color.BLACK)
                    blackPolylineOptions!!.width(5f)
                    blackPolylineOptions!!.startCap(SquareCap())
                    blackPolylineOptions!!.jointType(JointType.ROUND)
                    blackPolylineOptions!!.addAll(polylineList!!)
                    blackPolyline2=mMap.addPolyline(blackPolylineOptions!!)

                    //Animator
                    val valueAnimator= ValueAnimator.ofInt(0,100)
                    valueAnimator.duration=1100
                    valueAnimator.repeatCount=ValueAnimator.INFINITE
                    valueAnimator.interpolator=LinearInterpolator()
                    valueAnimator.addUpdateListener { value->
                        val points= greyPolyline!!.points
                        val percentValue=value.animatedValue.toString().toInt()
                        val size=points.size
                        val newPoints=(size * (percentValue/100.0f)).toInt()
                        val p=points.subList(0,newPoints)
                        blackPolyline2!!.points=(p)

                    }
                    valueAnimator.start()
                    val latLngBound=LatLngBounds.Builder().include(selectedPlaceEvent!!.origin)
                        .include(selectedPlaceEvent.destination)
                        .build()
                    //Add car icon for origin
                    val objects =jsonArray.getJSONObject(0)
                    val legs= objects.getJSONArray("legs")
                    val legsObject=legs.getJSONObject(0)

                    val time=legsObject.getJSONObject("duration")
                    val duration=time.getString("text")
                    val durationValue=time.getInt("value")
                    val distance=legsObject.getJSONObject("distance")
                    val distanceText=distance.getString("text")
                    val distanceValue=distance.getInt("value")

                    val start_address=legsObject.getString("start_address")
                    val end_address=legsObject.getString("end_address")

                    val startLocation=legsObject.getJSONObject("start_location")
                    val endLocation=legsObject.getJSONObject("end_location")

                    //set value
                    txt_distance.text=distanceText


                    //Update value
                    selectedPlaceEvent.originaddress=start_address
                    selectedPlaceEvent.origin=LatLng(startLocation.getDouble("lat"),
                    startLocation.getDouble("lng"))
                    selectedPlaceEvent.destinationAddress=end_address
                    selectedPlaceEvent.destination=LatLng(endLocation.getDouble("lat"),
                        endLocation.getDouble("lng"))
                    selectedPlaceEvent.durationValue=durationValue
                    selectedPlaceEvent.distanceValue=distanceValue
                    selectedPlaceEvent.durationText=duration
                    selectedPlaceEvent.distanceText=distanceText
                    //Calculate fee
                    val fee=common.calculateFeeBaseOnMetters(distanceValue)
                    selectedPlaceEvent.totalFare=fee
                    txt_fare.text=StringBuilder("$").append(fee)



                    addOriginMarker(duration,start_address)

                    addDestinationMarker(end_address)

                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBound,160))
                    mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.cameraPosition!!.zoom-1))






                }
                catch (e:java.lang.Exception){
//                    Snackbar.make(applicationContext,e.message!!,Snackbar.LENGTH_LONG).show()
                    Log.d("RequestDriverActivity"," "+e.message)
                }
            })
    }

    private fun addDestinationMarker(endAddress: String) {

        val view=layoutInflater.inflate(R.layout.destination_info_windows,null)

        val txt_destination=view.findViewById<View>(R.id.txt_destination) as TextView
        txt_destination.text=common.formatAddress(endAddress)

        val generator=IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = generator.makeIcon()

        destinationMarker=mMap.addMarker(MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon))
            .position(selectedPlaceEvent!!.destination))

    }

    private fun addOriginMarker(duration: String, startAddress: String) {
        val view=layoutInflater.inflate(R.layout.origin_info_windows,null)
        val txt_time=view.findViewById<View>(R.id.txt_time) as TextView
         txt_origin=view.findViewById<View>(R.id.txt_origin) as TextView

        txt_time.text=common.formatDuration(duration)
        txt_origin!!.text=common.formatAddress(startAddress)

        val generator=IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = generator.makeIcon()

        originMarker=mMap.addMarker(MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon))
            .position(selectedPlaceEvent!!.origin))
    }
}