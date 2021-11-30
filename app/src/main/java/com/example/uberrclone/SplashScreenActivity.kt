package com.example.uberrclone

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.uberrclone.Utils.UserUtils
import com.example.uberrclone.comon.common
import com.example.uberrclone.model.RiderInfoData
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_splashscreen.*
import java.util.*
import java.util.concurrent.TimeUnit

class SplashScreenActivity : AppCompatActivity() {
    companion object{
        private val LOGIN_REQUEST_CODE=3214
    }

    private lateinit var provider :List<AuthUI.IdpConfig>
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var listener: FirebaseAuth.AuthStateListener

    private lateinit var database: FirebaseDatabase
    private lateinit var riderInforef: DatabaseReference

    override fun onStart() {
        super.onStart()
        delaySplashScreen()
    }

    private fun delaySplashScreen() {
        Completable.timer(3, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
            .subscribe({
                firebaseAuth.addAuthStateListener(listener)
            })
    }

    override fun onStop() {
        if(firebaseAuth!=null && listener!=null){
            firebaseAuth.removeAuthStateListener(listener)}
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splashscreen)
//        setContentView(R.layout.activity_splashscreen1)
        init()
    }
    private fun init(){

        database= FirebaseDatabase.getInstance()
        riderInforef= database.getReference(common.RIDERS_INFO_REFERENCE)
        provider = Arrays.asList (
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build(),
        )
        firebaseAuth= FirebaseAuth.getInstance()
        listener= FirebaseAuth.AuthStateListener { myfirebaseAuth->
            val user=myfirebaseAuth.currentUser
            if(user!=null){

                FirebaseMessaging.getInstance().token.addOnFailureListener { e-> Log.e("SplashScreen",e.message.toString()) }
                    .addOnSuccessListener { instanceIdResult->


                        Log.d("TOKEN"," " +instanceIdResult)
                        UserUtils.updatetoken(this@SplashScreenActivity,instanceIdResult)

                    }

                checkUserFromFirebase()
            }
            else{
                Showloginlaout()
            }
        }
    }
    private fun checkUserFromFirebase() {
        riderInforef
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if(snapshot.exists()){
//                        Toast.makeText(this@SplashScreenActivity,"user already register",Toast.LENGTH_LONG).show()
                        val model=snapshot.getValue(RiderInfoData::class.java)
                        Log.d("SplashScreen","null" +model!!.avatar)
                        gotoDriverHomeactivity(model)
                    }
                    else{
                        showRegisterlayout()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@SplashScreenActivity,"113" + error.message, Toast.LENGTH_LONG).show()
                }

            })
    }
    private fun gotoDriverHomeactivity(model: RiderInfoData?) {
        common.currentUser=model
        startActivity(Intent(this,HomeActivity::class.java))
        finish()
    }

    private fun Showloginlaout() {
        val authMethodPickerLayout= AuthMethodPickerLayout.Builder(R.layout.signin)
            .setPhoneButtonId(R.id.btn_phone_signin)
            .setGoogleButtonId(R.id.btn_google_signin)
            .build()

        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAuthMethodPickerLayout(authMethodPickerLayout)
                .setIsSmartLockEnabled(false)
                .setAvailableProviders(provider)
                .setTheme(R.style.Logintheme) // Set theme
                .build()

            ,LOGIN_REQUEST_CODE)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode== LOGIN_REQUEST_CODE){
            val response= IdpResponse.fromResultIntent(data)
            if(resultCode== Activity.RESULT_OK){
                val user= FirebaseAuth.getInstance().currentUser
            }
            else{
                Toast.makeText(this@SplashScreenActivity,"149"+response!!.error!!.message, Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun showRegisterlayout() {
        val builder = AlertDialog.Builder(this, R.style.DialogTheme)
        val itemView= LayoutInflater.from(this).inflate(R.layout.register_layout,null)



        val txt_first_name=itemView.findViewById<View>(R.id.edtxt_first_name) as TextInputEditText
        val txt_last_name=itemView.findViewById<View>(R.id.edtxt_last_name) as TextInputEditText
        val txt_phone=itemView.findViewById<View>(R.id.edtxt_phone_number) as TextInputEditText

        val btncontinue=itemView.findViewById<View>(R.id.btn_continue) as Button





//        setvalues
        if(FirebaseAuth.getInstance().currentUser!=null && FirebaseAuth.getInstance().currentUser!!.phoneNumber!=""&&
            !TextUtils.isDigitsOnly(FirebaseAuth.getInstance().currentUser!!.phoneNumber)){
            txt_phone.setText(FirebaseAuth.getInstance().currentUser!!.phoneNumber)
        }
        builder.setView(itemView)
        val dialog=builder.create()
        dialog.show()


        btncontinue.setOnClickListener {
            if (TextUtils.isDigitsOnly(txt_first_name.text.toString())){
                Toast.makeText(this@SplashScreenActivity,"Please Enter First Name", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }else if (TextUtils.isDigitsOnly(txt_last_name.text.toString())){
                Toast.makeText(this@SplashScreenActivity,"Please Enter Last Name", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }else if (TextUtils.isDigitsOnly(txt_phone.text.toString())){
                Toast.makeText(this@SplashScreenActivity,"Please Enter Phone Number", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            else{
                var model= RiderInfoData()
                model.first_name=txt_first_name.text.toString()
                model.last_name=txt_last_name.text.toString()
                model.phone_number=txt_phone.text.toString()


                riderInforef.child(FirebaseAuth.getInstance().currentUser!!.uid)
                    .setValue(model)
                    .addOnFailureListener {
                        Toast.makeText(this@SplashScreenActivity,"200"+it.message, Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        progress_bar.visibility= View.GONE
                    }
                    .addOnSuccessListener {
                        Toast.makeText(this@SplashScreenActivity,"register successfully", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        progress_bar.visibility= View.GONE
                        gotoDriverHomeactivity(model)
                    }
            }
        }
    }
}