package org.avmedia.remotevideocam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.remotevideocam.camera.Camera
import org.avmedia.remotevideocam.utils.ProgressEvents
import org.avmedia.remotevideocam.databinding.ActivityMainBinding
import org.avmedia.remotevideocam.display.Display
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks,
    EasyPermissions.RationaleCallbacks {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivity"

    init {
        instance = this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setScreenCharacteristics() // this should be called after "setContentView()"
        getPermission()

        ScreenSelector.add("main screen", binding.mainLayout)
        ScreenSelector.add("waiting for connection screen", binding.waitingToConnectLayout)
        ScreenSelector.add("display screen", binding.displayLayout)
        ScreenSelector.add("camera screen", binding.cameraLayout)

        createAppEventsSubscription()
        ProgressEvents.onNext(ProgressEvents.Events.ShowWaitingForConnectionScreen)
    }

    @Override
    override fun onPause() {
        super.onPause()
    }

    @Override
    override fun onResume() {
        super.onResume()

        if (!Camera.isConnected()) {
            // Open display first, which waits on 'accept'
            Display.init(this, binding.videoView)
            Display.connect(this)

            Camera.init(this, binding.videoWindow)
            Camera.connect(this)
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show()
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults, this
        )
    }

    @AfterPermissionGranted(RC_ALL_PERMISSIONS)
    private fun getPermission() {
        val perms = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        if (!EasyPermissions.hasPermissions(this, *perms)) {
            // Do not have permissions, request them now
            // EasyPermissions.requestPermissions()
            EasyPermissions.requestPermissions(
                this, getString(R.string.camera_and_location_rationale),
                RC_ALL_PERMISSIONS, *perms
            )
        }
    }

    private fun setScreenCharacteristics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window.navigationBarColor = getColor(R.color.colorPrimaryDark)
                it.hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }
    }

    override fun onRationaleDenied(requestCode: Int) {
        // Not yet
    }

    override fun onRationaleAccepted(requestCode: Int) {
        // Not yet
    }

    private fun createAppEventsSubscription(): Disposable =
        ProgressEvents.connectionEventFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {

                when (it) {
                    ProgressEvents.Events.DisplayDisconnected -> {
                        restartApp()
                    }

                    ProgressEvents.Events.CameraDisconnected -> {
                        restartApp()
                    }
                }
            }
            .subscribe(
                { },
                { throwable ->
                    Timber.d(
                        "Got error on subscribe: $throwable"
                    )
                })

    private fun restartApp() {
        Handler()
            .postDelayed(
                {
                    val pm: PackageManager = this.packageManager
                    val intent =
                        pm.getLaunchIntentForPackage(this.packageName)
                    this.finishAffinity() // Finishes all activities.
                    this.startActivity(intent) // Start the launch activity
                    exitProcess(0) // System finishes and automatically relaunches us.
                },
                100
            )
    }

    companion object {
        private const val RC_ALL_PERMISSIONS = 123
        private var instance: MainActivity? = null

        // Make context available from anywhere in the code (not yet used).
        fun applicationContext() : Context {
            return instance!!.applicationContext
        }
    }
}
