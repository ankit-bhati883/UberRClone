package com.example.uberrclone.ui.home

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.uberrclone.R
import com.example.uberrclone.Remote.IGoogleAPI
import com.example.uberrclone.Remote.RetrofitClient
import com.example.uberrclone.RequestDriverActivity
import com.example.uberrclone.Utils.LocationUtils
import com.example.uberrclone.callback.FirebaseDriverInfoListener
import com.example.uberrclone.callback.FirebaseFailedListener
import com.example.uberrclone.comon.common
import com.example.uberrclone.databinding.FragmentHomeBinding
import com.example.uberrclone.model.AnimationModel
import com.example.uberrclone.model.DriverGeoModel
import com.example.uberrclone.model.DriverInfoModel
import com.example.uberrclone.model.EventBus.SelectedPlaceEvent
import com.example.uberrclone.model.GeoQueryModel
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.json.JSONObject
import java.io.IOException
import java.util.*

class HomeFragment : Fragment(), OnMapReadyCallback, FirebaseDriverInfoListener {

    private var isNextLaunch: Boolean=false
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment: SupportMapFragment

    private lateinit var slidingPanelLayout: SlidingUpPanelLayout
    private lateinit var txt_welcome: TextView
    private lateinit var autocompletesupportfragment: AutocompleteSupportFragment


    private var locationRequest: com.google.android.gms.location.LocationRequest? = null
    private var locationCallback: LocationCallback? = null
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null

    //Load Driver
    var distance = 1.0
    val LIMIT_RANGE = 10.0
    var previousLocation: Location? = null
    var currentLocation: Location? = null

    var firstTime = true
    lateinit var iFirebaseDriverInfoListener: FirebaseDriverInfoListener
    lateinit var iFirebaseFailedListener: FirebaseFailedListener

    var cityName = ""
    lateinit var iGoogleAPI: IGoogleAPI


    override fun onStop() {
        compositeDisposable.clear()
        super.onStop()
    }

    val compositeDisposable = CompositeDisposable()

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root


        mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        initview(root)
        init()
        return root
    }

    private fun initview(root: View?) {
        slidingPanelLayout = root!!.findViewById(R.id.activity_main) as SlidingUpPanelLayout
        txt_welcome = root!!.findViewById(R.id.txt_welcome) as TextView

        common.setWelcomeMessage(txt_welcome)

    }

    override fun onResume() {
        super.onResume()
        if(isNextLaunch)
            loadAvailableDrivers()
        else
            isNextLaunch=true
    }

    private fun init() {
        Log.d("HomeFragment", "init called")
        Places.initialize(requireContext(), getString(R.string.google_maps_key))
        autocompletesupportfragment =
            childFragmentManager.findFragmentById(R.id.autoComplete_fragment) as AutocompleteSupportFragment
        autocompletesupportfragment.setPlaceFields(
            Arrays.asList(
                Place.Field.ID,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG,
                Place.Field.NAME
            )
        )
        autocompletesupportfragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onError(p0: Status) {
                Snackbar.make(requireView(), p0.statusMessage, Snackbar.LENGTH_LONG).show()
                Log.d("HomeFragment", " " + p0.statusMessage)
            }

            override fun onPlaceSelected(p0: Place) {
                Snackbar.make(requireView(), " " + p0.latLng, Snackbar.LENGTH_LONG).show()
                Log.d("HomeFragment", " " + p0.latLng)
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Snackbar.make(mapFragment.requireView(),"Require Permission",Snackbar.LENGTH_LONG).show()
                    Log.d("HomeFragment", "Permission Required")
                    return
                }
                fusedLocationProviderClient!!
                    .lastLocation.addOnSuccessListener { location ->
                        val origin = LatLng(location.latitude, location.longitude)
                        val destination = LatLng(p0.latLng.latitude, p0.latLng.longitude)

                        startActivity(Intent(requireContext(), RequestDriverActivity::class.java))
                        EventBus.getDefault().postSticky(SelectedPlaceEvent(origin, destination,"",p0.address))
                    }
            }

        })
        Log.d("HomeFragment", "init called")
        iGoogleAPI = RetrofitClient.instance!!.create(IGoogleAPI::class.java)

        iFirebaseDriverInfoListener = this

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("HomeFragment", "Permission Required")
            return
        }

        buildLocationRequest()

        buildLocationCallback()

        updateLocation()

        Log.d("HomeFragment", "call for loadAvailableDrivers")
        loadAvailableDrivers()
        Log.d("HomeFragment", "init finished")
    }

    private fun updateLocation() {
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireContext())


        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            Log.d("HomeFragment", "permission_require")

            return
        }

        fusedLocationProviderClient!!.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )
    }

    private fun buildLocationCallback() {
        if (locationCallback == null) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    Log.d("HomeFragment", "onLocationResult called")
                    super.onLocationResult(locationResult)


                    val newPos = LatLng(
                        locationResult.lastLocation.latitude,
                        locationResult.lastLocation.longitude
                    )
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 10f))

                    //if user has change Location,calculate  and load driver again
                    if (firstTime) {
                        previousLocation = locationResult.lastLocation
                        currentLocation = locationResult.lastLocation


                        firstTime = false
                    } else {
                        previousLocation = currentLocation
                        currentLocation = locationResult.lastLocation

                    }
                    setRestrictPlacesInCoutry(locationResult.lastLocation)
                    if (previousLocation!!.distanceTo(currentLocation) / 1000 <= LIMIT_RANGE)
                        Log.d("Homefragment", "call for loadAvailableDrivers")
                    loadAvailableDrivers()

                }
            }
        }
    }

    private fun buildLocationRequest() {
        if (locationRequest == null) {
            locationRequest = com.google.android.gms.location.LocationRequest()
            locationRequest!!.setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY)
            locationRequest!!.setFastestInterval(3000)
            locationRequest!!.interval = 5000
            locationRequest!!.setSmallestDisplacement(10f)
        }
    }

    private fun setRestrictPlacesInCoutry(lastLocation: Location?) {
        try {
            val geoCoder = Geocoder(requireContext(), Locale.getDefault())
            var addressList: List<Address> =
                geoCoder.getFromLocation(lastLocation!!.latitude, lastLocation.longitude, 1)
            if (addressList.size > 0)
                autocompletesupportfragment.setCountries(addressList[0].countryCode)
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    private fun loadAvailableDrivers() {
        Log.d("HomeFragment", " loadAvailableDrivers called")
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("HomeFragment", "Permission check")
            return
        }
        fusedLocationProviderClient!!.lastLocation
            .addOnFailureListener {
                Log.e("HomeFragment", it.message!!)
            }
            .addOnSuccessListener { location ->


                cityName=LocationUtils.getAddressFromLocation(requireContext(),location)


                Log.d("HomeFragment","$cityName   cityname1")
                if(cityName=="New DelhiIN" ) cityName="DelhiIN"
                Log.d("HomeFragment","$cityName   cityname2")
                    //Query
                    if (!TextUtils.isEmpty(cityName)) {
                        val driver_location_ref = FirebaseDatabase.getInstance()
                            .getReference(common.DRIVERS_LOCATION_REFERENCE)
                            .child(cityName)
                        val gf = GeoFire(driver_location_ref)
                        val geoQuery = gf.queryAtLocation(
                            GeoLocation(location.latitude, location.longitude),
                            distance
                        )
                        geoQuery.removeAllListeners()

                        geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
                            override fun onKeyEntered(key: String?, location: GeoLocation?) {
                                Log.d("HomeFragment", " loadAvailableDrivers key entered")
//                            common.driversFound.add(DriverGeoModel(key!!,location!!))
                                if (!common.driversFound.containsKey(key)){
                                    Log.d("HomeFragment", " loadAvailableDrivers key entered in driversfound")
                                    common.driversFound[key!!] = DriverGeoModel(key!!, location)}
                            }

                            override fun onKeyExited(key: String?) {

                            }

                            override fun onKeyMoved(key: String?, location: GeoLocation?) {

                            }

                            override fun onGeoQueryReady() {
                                if (distance <= LIMIT_RANGE) {
                                    distance++
                                    Log.d("HomeFragment", "loadAvailableDrivers onGeoQueryReady $distance")
                                    loadAvailableDrivers()
                                } else {
                                    distance = 0.0
                                    Log.d("HomeFragment", "call for addDriverMarker in onGeoQueryReady")
                                    addDriverMarker()
                                }
                            }

                            override fun onGeoQueryError(error: DatabaseError?) {
                                Log.e("HomeFragment", "${error!!.message}")
                            }

                        })
                        driver_location_ref.addChildEventListener((object : ChildEventListener {
                            override fun onChildAdded(
                                snapshot: DataSnapshot,
                                previousChildName: String?
                            ) {
                                Log.d("HomeFragment", "onchild Added")
                                val geoQueryModel = snapshot.getValue(GeoQueryModel::class.java)
                                val geoLocation =
                                    GeoLocation(geoQueryModel!!.l!![0], geoQueryModel!!.l!![1])
                                val driverGeoModel = DriverGeoModel(snapshot.key, geoLocation)
                                val newDriverLocation = Location("")
                                newDriverLocation.latitude = geoLocation.latitude
                                newDriverLocation.longitude = geoLocation.longitude
                                val newDistance =
                                    location.distanceTo(newDriverLocation) / 1000 //in km
                                if (newDistance <= LIMIT_RANGE)
                                    Log.d("HomeFragment", "call for findDriverByKey")
                                    findDriverByKey(driverGeoModel)
                            }

                            override fun onChildChanged(
                                snapshot: DataSnapshot,
                                previousChildName: String?
                            ) {

                            }

                            override fun onChildRemoved(snapshot: DataSnapshot) {

                            }

                            override fun onChildMoved(
                                snapshot: DataSnapshot,
                                previousChildName: String?
                            ) {

                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e("HomeFragment", error.message)
                            }

                        }))
                    } else
                        Log.e("HomeFragment", "City Name Not Found")




            }
    }

    private fun addDriverMarker() {
        Log.d("HomeFragment", "addDriverMarker called")
        if (common.driversFound.size > 0) {

            io.reactivex.Observable.fromIterable(common.driversFound.keys)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { key: String? ->
                        Log.d("HomeFragment", "call for findDriverByKey in addDriverMarker")
                        findDriverByKey(common.driversFound[key])
                    },
                    { t: Throwable? ->
                        Log.d("HomeFragment", t!!.message!!)
                    }
                )



        } else {
            Log.d("HomeFragment", "Drivers not found")
        }
    }

    private fun findDriverByKey(driverGeoModel: DriverGeoModel?) {
        Log.d("HomeFragment", "findDriverByKey called")
        FirebaseDatabase.getInstance()
            .getReference(common.DRIVER_INFO_REFERENCE)
            .child(driverGeoModel!!.key!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.hasChildren()) {
                        driverGeoModel.driverInfoModel =
                            snapshot.getValue(DriverInfoModel::class.java)
                        common.driversFound[driverGeoModel.key!!]!!.driverInfoModel =
                            snapshot.getValue(DriverInfoModel::class.java)
                        Log.d("HomeFragment", "call for onDriverInfoLoadSuccess")
                        iFirebaseDriverInfoListener.onDriverInfoLoadSuccess(driverGeoModel)
                    } else {
                        iFirebaseFailedListener.onFirebaseFailed("key driver not found ${driverGeoModel.key}")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    iFirebaseFailedListener.onFirebaseFailed(error.message)
                }

            })
    }

    override fun onDestroyView() {
        fusedLocationProviderClient!!.removeLocationUpdates(locationCallback)
        super.onDestroyView()
        _binding = null
    }

    override fun onMapReady(p0: GoogleMap) {
        mMap = p0

        Dexter.withContext(requireContext())
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {

                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {

                    if (ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {

                        return
                    }
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    mMap.setOnMyLocationButtonClickListener {
                        fusedLocationProviderClient!!.lastLocation
                            .addOnFailureListener { e ->
                                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                            }
                            .addOnSuccessListener { location ->
                                val userLatLng = LatLng(location.latitude, location.longitude)
                                mMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        userLatLng,
                                        18f
                                    )
                                )
                            }
                        true
                    }
                    //layout button
                    val view = mapFragment.requireView()
                        .findViewById<View>("1".toInt())!!
                        .parent as View
                    val locationButtion = view.findViewById<View>("2".toInt())
                    val params = locationButtion.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                    params.bottomMargin = 250// move to see zoom control


                    //Update Location
                    buildLocationRequest()

                    buildLocationCallback()

                    updateLocation()
                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {

                }
                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {
                    Toast.makeText(context, "Permission $p0 denied", Toast.LENGTH_LONG).show()
                }

            }).check()


        mMap.uiSettings.isZoomControlsEnabled = true


        try {
            val success = p0.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(),
                    R.raw.uber_maps_style
                )
            )
            if (!success) {
                Log.e("EDMT error", "Load map style failed")
            }
        } catch (e: Resources.NotFoundException) {
            Log.e("EDMT error", e.message.toString())
        }
    }

    override fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?) {
        // if already have marker with
        Log.d("HomeFragment", "onDriverInfoLoadSuccess called")
        if (!common.markerList.containsKey(driverGeoModel!!.key))

            common.markerList.put(
            driverGeoModel!!.key!!,
            mMap.addMarker(
                MarkerOptions()
                    .position(
                        LatLng(
                            driverGeoModel!!.geoLocation!!.latitude,
                            driverGeoModel!!.geoLocation!!.longitude
                        )
                    )
                    .flat(true)
                    .title(
                        common.buildName(
                            driverGeoModel.driverInfoModel!!.first_name,
                            driverGeoModel.driverInfoModel!!.last_name
                        )
                    )
                    .snippet(driverGeoModel.driverInfoModel!!.phone_number)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
            )!!
        )

        if (!TextUtils.isEmpty(cityName)) {
            val driverLocation = FirebaseDatabase.getInstance()
                .getReference(common.DRIVERS_LOCATION_REFERENCE)
                .child(cityName)
                .child(driverGeoModel!!.key!!)
            driverLocation.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.hasChildren()) {
                        if (common.markerList.get(driverGeoModel!!.key!!) != null) {
                            val marker = common.markerList.get(driverGeoModel!!.key!!)
                            marker!!.remove()// remove marker from the map
                            common.markerList.remove(driverGeoModel!!.key!!)// Remove marker information
                            common.driversSubscribe.remove(driverGeoModel.key!!)//Remove driver information

                            if(common.driversFound!=null && common.driversFound[driverGeoModel!!.key!!]!=null)
                                common.driversFound.remove(driverGeoModel!!.key!!)

                            driverLocation.removeEventListener(this)
                        }
                    } else {
                        if (common.markerList.get(driverGeoModel!!.key!!) != null) {
                            val geoQueryModel = snapshot!!.getValue(GeoQueryModel::class.java)
                            val animationModel = AnimationModel(false, geoQueryModel!!)
                            if (common.driversSubscribe.get(driverGeoModel!!.key!!) != null) {
                                val marker = common.markerList.get(driverGeoModel!!.key!!)
                                val oldPosition =
                                    common.driversSubscribe.get(driverGeoModel!!.key!!)
                                val from = StringBuilder()
                                    .append(oldPosition!!.geoQueryModel!!.l?.get(0))
                                    .append(",")
                                    .append(oldPosition.geoQueryModel!!.l?.get(1)).toString()

                                val to = StringBuilder()
                                    .append(animationModel.geoQueryModel!!.l?.get(0))
                                    .append(",")
                                    .append(animationModel.geoQueryModel!!.l?.get(1))
                                    .toString()

                                moveMarkerAnimation(
                                    driverGeoModel.key!!,
                                    animationModel,
                                    marker,
                                    from,
                                    to
                                )
                            } else
                                common.driversSubscribe.put(
                                    driverGeoModel.key!!,
                                    animationModel
                                ) // first location init
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.d("HomeFragment", error.message)
                }

            })
        }
    }

    private fun moveMarkerAnimation(
        key: String,
        newData: AnimationModel,
        marker: Marker?,
        from: String,
        to: String
    ) {


        if (!newData.isRun) {


            //Request API
            compositeDisposable.add(iGoogleAPI.getDirections(
                "driving",
                "less_driving",
                from, to,

                getString(R.string.google_api_key)

            )
            !!.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { returnResult ->
                    Log.d("API_RETURN", returnResult)

                    try {

                        val jsonObject = JSONObject(returnResult)
                        val jsonArray = jsonObject.getJSONArray("routes")
                        for (i in 0 until jsonArray.length()) {
                            val route = jsonArray.getJSONObject(i)
                            val poly = route.getJSONObject("overview_polyline")
                            val polyline = poly.getString("points")
                            newData.polylineList = common.decodePoly(polyline)
                        }
                        //Moving

                        newData.index = -1
                        newData.next = 1
                        val runnable = object : Runnable {
                            override fun run() {
                                if (newData.polylineList != null && newData.polylineList!!.size > 1) {
                                    if (newData.index < newData.polylineList!!.size - 2) {
                                        newData.index++
                                        newData.next = newData.index + 1
                                        newData.start = newData.polylineList!![newData.index]!!
                                        newData.end = newData.polylineList!![newData.next]!!
                                    }
                                    val valueAnimator = ValueAnimator.ofInt(0, 1)
                                    valueAnimator.duration = 3000
                                    valueAnimator.interpolator = LinearInterpolator()
                                    valueAnimator.addUpdateListener { value ->
                                        newData.v = value.animatedFraction
                                        newData.lat =
                                            newData.v * newData.end!!.latitude + (1 - newData.v) * newData.start!!.latitude
                                        newData.lng =
                                            newData.v * newData.end!!.longitude + (1 - newData.v) * newData.start!!.longitude
                                        val newPos = LatLng(newData.lat, newData.lng)
                                        marker!!.position = newPos
                                        marker!!.setAnchor(0.5f, 0.5f)
                                        marker!!.rotation =
                                            common.getBearing(newData.start!!, newPos)

                                    }
                                    valueAnimator.start()
                                    if (newData.index < newData.polylineList!!.size - 2)
                                        newData.handler!!.postDelayed(this, 1500)
                                    else if (newData.index < newData.polylineList!!.size - 1) {
                                        newData.isRun = false
                                        common.driversSubscribe.put(key, newData)//update
                                    }

                                }
                            }

                        }
                        newData.handler!!.postDelayed(runnable, 1500)

                    } catch (e: java.lang.Exception) {
                        Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_LONG).show()
                    }
                })
        }
    }
}