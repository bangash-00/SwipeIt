package com.example.swipeit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private lateinit var tvHeroState: TextView
    private lateinit var tvHeroSub: TextView
    private lateinit var btnToggle: TextView
    private lateinit var tvAccStatus: TextView
    private lateinit var btnAccessibility: TextView
    private lateinit var tvYaw: TextView
    private lateinit var tvPitch: TextView
    private lateinit var barYaw: LinearProgressIndicator
    private lateinit var barPitch: LinearProgressIndicator
    private lateinit var tvBlink: TextView
    private lateinit var tvHand: TextView
    private lateinit var tvLast: TextView
    private lateinit var tvError: TextView

    private var running = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.CAMERA] == true) {
                startGestureService()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        tvHeroState = findViewById(R.id.tvHeroState)
        tvHeroSub = findViewById(R.id.tvHeroSub)
        btnToggle = findViewById(R.id.btnToggle)
        tvAccStatus = findViewById(R.id.tvAccStatus)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        tvYaw = findViewById(R.id.tvYaw)
        tvPitch = findViewById(R.id.tvPitch)
        barYaw = findViewById(R.id.barYaw)
        barPitch = findViewById(R.id.barPitch)
        tvBlink = findViewById(R.id.tvBlink)
        tvHand = findViewById(R.id.tvHand)
        tvLast = findViewById(R.id.tvLast)
        tvError = findViewById(R.id.tvError)

        bindSwitch(R.id.swHead, prefs.headEnabled) { prefs.headEnabled = it }
        bindSwitch(R.id.swEye, prefs.eyeEnabled) { prefs.eyeEnabled = it }
        bindSwitch(R.id.swHand, prefs.handEnabled) { prefs.handEnabled = it }
        bindSwitch(R.id.swInvertH, prefs.invertHorizontal) { prefs.invertHorizontal = it }
        bindSwitch(R.id.swInvertV, prefs.invertVertical) { prefs.invertVertical = it }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnToggle.setOnClickListener {
            if (running) GestureService.stop(this) else onStartClicked()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                GestureState.flow.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityCard()
    }

    private fun bindSwitch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val sw = findViewById<CompoundButton>(id)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private fun updateAccessibilityCard() {
        val on = SwipeService.instance != null
        tvAccStatus.text = if (on) "Enabled ✓" else "Required for swiping. Tap Enable."
        tvAccStatus.setTextColor(color(if (on) R.color.success else R.color.warn))
        btnAccessibility.text = if (on) "Settings" else "Enable"
    }

    private fun render(s: GestureState.Snapshot) {
        running = s.running

        tvHeroState.text = if (running) "Active" else "Ready"
        tvHeroState.setTextColor(color(if (running) R.color.success else R.color.text_primary))
        tvHeroSub.text =
            if (running) "Camera is on and gestures are live."
            else "Start to control your phone hands-free."
        btnToggle.text = if (running) "Stop" else "Start"
        btnToggle.setBackgroundResource(
            if (running) R.drawable.bg_btn_stop else R.drawable.bg_btn_start
        )

        tvYaw.text = "%.0f°".format(s.yaw)
        tvPitch.text = "%.0f°".format(s.pitch)
        barYaw.setProgressCompat(toBar(s.yaw), true)
        barPitch.setProgressCompat(toBar(s.pitch), true)

        tvBlink.text = if (s.blink) "👁 Blinking" else "👁 Eyes open"
        tvBlink.setBackgroundResource(if (s.blink) R.drawable.bg_chip_on else R.drawable.bg_chip_off)
        tvBlink.setTextColor(color(if (s.blink) R.color.success else R.color.text_secondary))

        tvHand.text = if (s.handSeen) "✋ Hand seen" else "✋ No hand"
        tvHand.setBackgroundResource(if (s.handSeen) R.drawable.bg_chip_on else R.drawable.bg_chip_off)
        tvHand.setTextColor(color(if (s.handSeen) R.color.success else R.color.text_secondary))

        tvLast.text = if (s.lastAction == "-") "—"
        else s.lastAction.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

        tvError.visibility = if (s.error.isNotEmpty()) View.VISIBLE else View.GONE
        tvError.text = s.error

        updateAccessibilityCard()
    }

    /** Maps -40..40 degrees to a 0..100 progress bar (50 = centered). */
    private fun toBar(deg: Float): Int =
        (((deg.coerceIn(-40f, 40f) + 40f) / 80f) * 100f).toInt()

    private fun onStartClicked() {
        if (SwipeService.instance == null) {
            Toast.makeText(this, "Enable the Accessibility service first", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val needed = mutableListOf<String>()
        if (!granted(Manifest.permission.CAMERA)) needed += Manifest.permission.CAMERA
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isEmpty()) startGestureService() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun startGestureService() {
        GestureService.start(this)
        Toast.makeText(this, "Gesture control started", Toast.LENGTH_SHORT).show()
    }
}