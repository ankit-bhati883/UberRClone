package com.example.uberrclone

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.bumptech.glide.Glide
import com.example.uberrclone.Utils.UserUtils
import com.example.uberrclone.comon.common
import com.example.uberrclone.databinding.ActivityHomeBinding

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.lang.StringBuilder

class HomeActivity : AppCompatActivity() {

    private lateinit var navView:NavigationView
    private lateinit var drawerLayout:DrawerLayout
    private lateinit var navController: NavController

    private lateinit var waitingDialog: AlertDialog
    private lateinit var storageRef: StorageReference
    private var imageUri: Uri?=null

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityHomeBinding
    private lateinit var imageavtaar:ImageView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarHome.toolbar)


        drawerLayout = binding.drawerLayout
        navView= binding.navView
        navController = findNavController(R.id.nav_host_fragment_content_home)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow), drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        init()
    }
    private fun init(){

        storageRef= FirebaseStorage.getInstance().getReference()

        waitingDialog= AlertDialog.Builder(this)
            .setMessage("Wating...")
            .setCancelable(false).create()

        navView.setNavigationItemSelectedListener {
            if(it.itemId == R.id.nav_sign_out){
                val builder= AlertDialog.Builder(this@HomeActivity)
                builder.setTitle("Sign Out")
                    .setNegativeButton("CANCEL",{dialogInterface, _-> dialogInterface.dismiss() })
                    .setPositiveButton("SING OUT"){dialogInterface,_->

                        FirebaseAuth.getInstance().signOut()
                        val intent= Intent(this,SplashScreenActivity::class.java)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    }.setCancelable(false)

                val dialog=builder.create()
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(ContextCompat.getColor(this,android.R.color.holo_red_dark))

                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(ContextCompat.getColor(this,R.color.colorAccent))
                }
                dialog.show()
            }
            true
        }
        val hearderView=navView.getHeaderView(0)

        val txt_name=hearderView.findViewById<TextView>(R.id.txt_name) as TextView
        val txt_phone=hearderView.findViewById<TextView>(R.id.txt_phone) as TextView
        imageavtaar=hearderView.findViewById<TextView>(R.id.imageavtar) as ImageView

        txt_name.setText(common.buildWelcomeMessange())
        txt_phone.setText(common.currentUser!!.phone_number)

        if(common.currentUser!=null && common.currentUser!!.avatar!=null && !TextUtils.isEmpty(
                common.currentUser!!.avatar)){
            Log.d("HomeActivity", common.currentUser!!.avatar!!)
            Glide.with(this)
                .load(common.currentUser!!.avatar).placeholder(R.drawable.common_google_signin_btn_icon_dark)
                .into(imageavtaar)
        }
        imageavtaar.setOnClickListener {
            val intent= Intent()
            intent.setType("image/*")
            intent.setAction(Intent.ACTION_GET_CONTENT)
            startActivityForResult(
                Intent.createChooser(
                    intent,
                    "Select Picture"
                ), PICK_IMAGE_REQUEST
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode== PICK_IMAGE_REQUEST && resultCode== Activity.RESULT_OK){
            if(data!=null && data.data!=null){
                imageUri=data.data
                imageavtaar.setImageURI(imageUri)
                showDialogUpload()
            }
        }
    }

    private fun showDialogUpload() {
        val builder= AlertDialog.Builder(this@HomeActivity)
        builder.setTitle("Change Avtar")
            .setNegativeButton("CANCEL",{dialogInterface, _-> dialogInterface.dismiss() })
            .setPositiveButton("Change"){dialogInterface,_->

                if(imageUri!=null){
                    waitingDialog.show()
                    val avatarFolder=storageRef.child("avatars/" + FirebaseAuth.getInstance().currentUser!!.uid)
                    avatarFolder.putFile(imageUri!!)
                        .addOnFailureListener{e->
                            Log.d("DrivierHomeActivity",e.message!!)
                            waitingDialog.dismiss()
                        }.addOnCompleteListener{task->
                            if(task.isSuccessful){
                                avatarFolder.downloadUrl.addOnSuccessListener{uri->
                                    val updatedata= HashMap<String,Any>()
                                    updatedata.put("avatar",uri.toString())
                                    Log.d("DrivierHomeActivity","put in updatedata $uri")
//                                   common.currentUser!!.avatar=uri.toString()
                                    Log.d("DrivierHomeActivity", common.currentUser!!.avatar!!)
                                    UserUtils.updateuser(drawerLayout,updatedata)

                                }
                            }
                            waitingDialog.dismiss()
                        }.addOnProgressListener { taskSnapshot->
                            val progress=(100.0*taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                            waitingDialog.setMessage(StringBuilder("Uploading: ").append(progress).append("%"))
                        }
                }
            }.setCancelable(false)

        val dialog=builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this,android.R.color.holo_red_dark))

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(ContextCompat.getColor(this,R.color.colorAccent))
        }
        dialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.home, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_home)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
    companion object{
        val PICK_IMAGE_REQUEST=8989
    }
}