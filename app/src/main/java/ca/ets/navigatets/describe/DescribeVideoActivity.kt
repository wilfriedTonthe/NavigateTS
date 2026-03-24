package ca.ets.navigatets.describe

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ca.ets.navigatets.R
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import androidx.camera.core.CameraUnavailableException
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import android.view.View
import android.widget.TextView
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import ca.ets.navigatets.BuildConfig
import ca.ets.navigatets.utils.ObjectDetectionUtilities
import ca.ets.navigatets.utils.ToSpeech
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

class DescribeVideoActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var imageAnalysis: ImageAnalysis
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var statusText: TextView
    private lateinit var btnVideo: View
    private lateinit var toSpeech: ToSpeech
    private val handler = Handler(Looper.getMainLooper())
    private var capturingVideo = false
    private var frameCounter = 0
    private val framesBase64 = mutableListOf<String>()
    private var autoStarted = false
    private val REQ_CAMERA = 2101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_describe_video)
        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.tv_video_info)
        btnVideo = findViewById(R.id.btn_video_desc)
        toSpeech = ToSpeech(this)
        previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }

        btnVideo.setOnClickListener { startFiveSecondCapture() }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, getString(R.string.no_camera_message), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(cameraExecutor) { image ->
                    try {
                        if (capturingVideo) {
                            frameCounter += 1
                            // ~6 fps sampling
                            if (frameCounter % 6 == 0) {
                                val rotation = image.imageInfo.rotationDegrees
                                val bmp = ObjectDetectionUtilities.convertImageToBitmap(image)
                                val m = Matrix()
                                m.postRotate(rotation.toFloat())
                                val rotated = Bitmap.createBitmap(bmp, 0, 0, image.width, image.height, m, true)
                                val scaled = scaleBitmapMaxSide(rotated, 1024)
                                val baos = ByteArrayOutputStream()
                                scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                                val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                                if (framesBase64.size < 8) framesBase64.add(b64)
                            }
                        }
                    } catch (_: Throwable) {
                    } finally {
                        image.close()
                    }
                }

                // Try back camera first, then fallback to front
                val selector = trySelectCamera(cameraProvider)
                if (selector == null) {
                    Toast.makeText(this, getString(R.string.no_camera_message), Toast.LENGTH_LONG).show()
                    runCatching { toSpeech.speakObject(getString(R.string.no_camera_message)) }
                    finish()
                    return@addListener
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, preview, imageAnalysis)
                // speak hint to hold the phone still
                runCatching { toSpeech.speakObject(getString(R.string.hold_phone_still)) }
                // auto-start capture shortly after bind to ensure frames arrive
                handler.postDelayed({
                    if (!autoStarted) {
                        autoStarted = true
                        startFiveSecondCapture()
                    }
                }, 600L)
            } catch (t: Throwable) {
                Toast.makeText(this, getString(R.string.camera_start_error_message, t.message ?: ""), Toast.LENGTH_LONG).show()
                runCatching { toSpeech.speakObject(getString(R.string.camera_start_error_message, t.message ?: "")) }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startFiveSecondCapture() {
        if (capturingVideo) return
        framesBase64.clear()
        frameCounter = 0
        capturingVideo = true
        statusText.text = getString(R.string.video_desc_collecting)
        handler.postDelayed({
            capturingVideo = false
            statusText.text = getString(R.string.video_desc_analyzing)
            callOpenAiVisionMulti(framesBase64.toList())
        }, 5000L)
    }

    private fun trySelectCamera(provider: ProcessCameraProvider): CameraSelector? {
        return try {
            when {
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> null
            }
        } catch (e: CameraInfoUnavailableException) {
            null
        } catch (e: CameraUnavailableException) {
            null
        }
    }

    override fun onDestroy() {
        try {
            val provider = cameraProviderFuture.get()
            provider.unbindAll()
        } catch (_: Throwable) {}
        cameraExecutor.shutdown()
        runCatching { toSpeech.destroy() }
        super.onDestroy()
    }

    private fun callOpenAiVisionMulti(jpegsBase64: List<String>) {
        val apiKey = BuildConfig.OPENAI_API_KEY ?: ""
        if (apiKey.isBlank()) {
            runOnUiThread {
                val msg = "Clé OpenAI absente. Ajoutez OPENAI_API_KEY dans local.properties."
                statusText.text = msg
                runCatching { toSpeech.speakObject(msg) }
            }
            return
        }
        val content = JSONArray()
        content.put(JSONObject().put("type", "text").put("text", "Décris clairement ce qui se passe dans cette courte séquence vidéo pour une personne malvoyante."))
        for (b64 in jpegsBase64) {
            val dataUrl = "data:image/jpeg;base64,$b64"
            content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl)))
        }
        val message = JSONObject().put("role", "user").put("content", content)
        val body = JSONObject().put("model", "gpt-4o").put("temperature", 0.2).put("messages", JSONArray().put(message))
        val mediaType = "application/json".toMediaType()
        val requestBody = body.toString().toRequestBody(mediaType)
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        OkHttpClient().newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread {
                    val msg = getString(R.string.video_desc_error, e.message ?: "")
                    statusText.text = msg
                    runCatching { toSpeech.speakObject(msg) }
                }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val txt = it.body?.string() ?: ""
                    var spoken = ""
                    try {
                        val root = JSONObject(txt)
                        if (it.isSuccessful) {
                            val choices = root.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val msgObj = choices.getJSONObject(0).getJSONObject("message")
                                spoken = msgObj.optString("content", "")
                                if (spoken.isBlank()) {
                                    val parts = msgObj.optJSONArray("content")
                                    if (parts != null && parts.length() > 0) {
                                        val sb = StringBuilder()
                                        for (i in 0 until parts.length()) {
                                            val p = parts.optJSONObject(i)
                                            if (p != null) {
                                                val t = p.optString("text", null)
                                                if (!t.isNullOrBlank()) sb.append(t)
                                            }
                                        }
                                        spoken = sb.toString()
                                    }
                                }
                            }
                            if (spoken.isBlank()) spoken = "Description indisponible."
                        } else {
                            val err = root.optJSONObject("error")
                            spoken = err?.optString("message") ?: "Erreur API: code ${'$'}{it.code}"
                        }
                    } catch (_: Throwable) {
                        spoken = if (it.isSuccessful) "Réponse vide" else "Erreur API: code ${'$'}{it.code}"
                    }
                    runOnUiThread { statusText.text = spoken }
                    runCatching { toSpeech.speakObject(spoken) }
                }
            }
        })
    }

    private fun scaleBitmapMaxSide(src: Bitmap, maxSide: Int): Bitmap {
        val w = src.width
        val h = src.height
        val maxDim = maxOf(w, h)
        if (maxDim <= maxSide) return src
        val ratio = maxSide.toFloat() / maxDim.toFloat()
        val nw = (w * ratio).toInt().coerceAtLeast(1)
        val nh = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }
}
