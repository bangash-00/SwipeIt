package com.example.swipeit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.flow.update
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.hypot

class GestureService : LifecycleService() {

    companion object {
        private const val TAG = "GestureService"
        private const val CHANNEL_ID = "gesture"
        private const val NOTIF_ID = 1
        private const val ACTION_STOP = "com.example.swipeit.STOP"
        private const val MIN_FRAME_GAP_MS = 33L

        private val PALM = intArrayOf(0, 5, 9, 13, 17)

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GestureService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GestureService::class.java))
        }
    }

    @Volatile private var faceLandmarker: FaceLandmarker? = null
    @Volatile private var handLandmarker: HandLandmarker? = null
    private var faceFailed = false
    private var handFailed = false

    private lateinit var executor: ExecutorService
    private lateinit var prefs: Prefs
    private val engine = GestureEngine()
    private val handDetector = HandSwipeDetector()
    private var lastFrameTs = 0L

    @Volatile private var lastActionLabel = "-"

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        executor = Executors.newSingleThreadExecutor()

        createChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        )

        GestureState.flow.value = GestureState.Snapshot(running = true)
        startCamera()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    // ---------------- models (created/closed on demand) ----------------
    private fun ensureModels() {
        val needFace = prefs.headEnabled || prefs.eyeEnabled
        val needHand = prefs.handEnabled

        if (needFace) {
            if (faceLandmarker == null && !faceFailed) {
                try {
                    faceLandmarker = createFace()
                } catch (t: Throwable) {
                    faceFailed = true
                    setError("Face model failed: ${t.message}")
                }
            }
        } else {
            faceFailed = false
            val l = faceLandmarker
            faceLandmarker = null
            l?.close()
        }

        if (needHand) {
            if (handLandmarker == null && !handFailed) {
                try {
                    handLandmarker = createHand()
                } catch (t: Throwable) {
                    handFailed = true
                    setError("Hand model failed (is hand_landmarker.task in assets?): ${t.message}")
                }
            }
        } else {
            handFailed = false
            val l = handLandmarker
            handLandmarker = null
            l?.close()
        }
    }

    private fun createFace(): FaceLandmarker {
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("face_landmarker.task")
                    .setDelegate(Delegate.CPU)
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .setResultListener { result, _ -> onFaceResult(result) }
            .setErrorListener { Log.e(TAG, "Face error: ${it.message}") }
            .build()
        return FaceLandmarker.createFromOptions(this, options)
    }

    private fun createHand(): HandLandmarker {
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .setDelegate(Delegate.CPU)
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.3f)
            .setMinHandPresenceConfidence(0.3f)
            .setMinTrackingConfidence(0.3f)
            .setResultListener { result, _ -> onHandResult(result) }
            .setErrorListener { Log.e(TAG, "Hand error: ${it.message}") }
            .build()
        return HandLandmarker.createFromOptions(this, options)
    }

    private fun setError(msg: String) {
        Log.e(TAG, msg)
        GestureState.flow.update { it.copy(error = msg) }
    }

    // ---------------- camera ----------------
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(480, 640))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analysis.setAnalyzer(executor) { proxy ->
                proxy.use {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastFrameTs < MIN_FRAME_GAP_MS) return@use
                    lastFrameTs = now

                    ensureModels()
                    val face = faceLandmarker
                    val hand = handLandmarker
                    if (face == null && hand == null) return@use

                    val mpImage = BitmapImageBuilder(it.toBitmap()).build()
                    val opts = ImageProcessingOptions.builder()
                        .setRotationDegrees(it.imageInfo.rotationDegrees)
                        .build()
                    face?.detectAsync(mpImage, opts, now)
                    hand?.detectAsync(mpImage, opts, now)
                }
            }

            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    // ---------------- hand ----------------
    private fun onHandResult(result: HandLandmarkerResult) {
        if (!prefs.handEnabled) return
        val hand = result.landmarks().firstOrNull()
        if (hand == null || hand.size < 21) {
            GestureState.flow.update { it.copy(handSeen = false) }
            return
        }
        var cx = 0f
        var cy = 0f
        for (i in PALM) {
            cx += hand[i].x()
            cy += hand[i].y()
        }
        cx /= PALM.size
        cy /= PALM.size

        val dir = handDetector.update(cx, cy, SystemClock.elapsedRealtime())
        if (dir != null) {
            SwipeService.instance?.swipe(dir)
            lastActionLabel = "HAND_$dir"
        }
        GestureState.flow.update {
            it.copy(
                handSeen = true,
                handMove = "dx=%.2f dy=%.2f".format(handDetector.lastDx, handDetector.lastDy),
                lastAction = lastActionLabel
            )
        }
    }

    // ---------------- face ----------------
    private fun onFaceResult(result: FaceLandmarkerResult) {
        val headOn = prefs.headEnabled
        val eyeOn = prefs.eyeEnabled
        if (!headOn && !eyeOn) return

        val matrix = result.facialTransformationMatrixes().orElse(null)?.firstOrNull()
        val blends = result.faceBlendshapes().orElse(null)?.firstOrNull()
        if (matrix == null || blends == null) return

        val pitch = Math.toDegrees(atan2(matrix[6].toDouble(), matrix[10].toDouble())).toFloat()
        val yaw = Math.toDegrees(
            atan2(-matrix[2].toDouble(), hypot(matrix[6].toDouble(), matrix[10].toDouble()))
        ).toFloat()

        var left = 0f
        var right = 0f
        for (c in blends) {
            when (c.categoryName()) {
                "eyeBlinkLeft" -> left = c.score()
                "eyeBlinkRight" -> right = c.score()
            }
        }

        val action = engine.update(
            yaw, pitch, left, right,
            SystemClock.elapsedRealtime(),
            prefs.invertHorizontal, prefs.invertVertical,
            headOn, eyeOn
        )

        val swipe = SwipeService.instance
        when (action) {
            GestureEngine.Action.SWIPE_LEFT -> swipe?.swipe(SwipeDir.LEFT)
            GestureEngine.Action.SWIPE_RIGHT -> swipe?.swipe(SwipeDir.RIGHT)
            GestureEngine.Action.SWIPE_UP -> swipe?.swipe(SwipeDir.UP)
            GestureEngine.Action.SWIPE_DOWN -> swipe?.swipe(SwipeDir.DOWN)
            GestureEngine.Action.DOUBLE_TAP -> swipe?.doubleTap()
            null -> {}
        }
        if (action != null) lastActionLabel = action.name

        GestureState.flow.update {
            it.copy(
                running = true,
                yaw = yaw,
                pitch = pitch,
                blink = left > 0.55f && right > 0.55f,
                lastAction = lastActionLabel
            )
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Gesture control", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, GestureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gesture control active")
            .setContentText("Camera is in use.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        faceLandmarker?.close()
        handLandmarker?.close()
        faceLandmarker = null
        handLandmarker = null
        executor.shutdown()
        GestureState.flow.value = GestureState.Snapshot(running = false)
        super.onDestroy()
    }
}