package ca.ets.navigatets.describe

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ca.ets.navigatets.BuildConfig
import ca.ets.navigatets.R
import ca.ets.navigatets.utils.ObjectDetectionUtilities
import ca.ets.navigatets.utils.ToSpeech
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DescribeSceneActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var countdownText: TextView
    private lateinit var statusText: TextView
    private lateinit var videoButton: View
    private lateinit var toSpeech: ToSpeech
    private lateinit var imageCapture: ImageCapture
    private lateinit var imageAnalysis: ImageAnalysis
    private lateinit var cameraExecutor: ExecutorService
    private val handler = Handler(Looper.getMainLooper())
    private var capturingVideo = false
    private var frameCounter = 0
    private val framesBase64 = mutableListOf<String>()

    companion object { private const val REQ_CAMERA = 2011 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_describe_scene)
        previewView = findViewById(R.id.preview_view)
        countdownText = findViewById(R.id.tv_countdown)
        statusText = findViewById(R.id.tv_status)
        videoButton = findViewById(R.id.btn_video_desc)
        toSpeech = ToSpeech(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        } else {
            startCamera()
            startCountdownAndCapture()
        }

        videoButton.setOnClickListener {
            if (capturingVideo) return@setOnClickListener
            framesBase64.clear()
            frameCounter = 0
            capturingVideo = true
            statusText.visibility = View.VISIBLE
            statusText.text = getString(R.string.video_desc_collecting)
            handler.postDelayed({
                capturingVideo = false
                statusText.text = getString(R.string.video_desc_analyzing)
                callOpenAiVisionMulti(framesBase64.toList())
            }, 5000L)
        }
    }

    private fun allPermissionsGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(cameraExecutor) { image ->
                try {
                    if (capturingVideo) {
                        frameCounter += 1
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
            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, preview, imageCapture, imageAnalysis)
            } catch (t: Throwable) {
                Toast.makeText(this, getString(R.string.camera_start_error_message, t.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCountdownAndCapture() {
        // Instruction vocale
        try { toSpeech.speakObject(getString(R.string.desc_instruction)) } catch (_: Throwable) {}
        // Compte à rebours 10 -> 0
        var remaining = 10
        countdownText.visibility = View.VISIBLE
        countdownText.text = getString(R.string.desc_countdown, remaining)
        val tick = object : Runnable {
            override fun run() {
                remaining -= 1
                if (remaining <= 0) {
                    countdownText.text = getString(R.string.desc_capturing)
                    captureAndDescribe()
                } else {
                    countdownText.text = getString(R.string.desc_countdown, remaining)
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        handler.postDelayed(tick, 1000L)
    }

    private fun captureAndDescribe() {
        statusText.visibility = View.VISIBLE
        statusText.text = getString(R.string.desc_capturing)
        val outFile = File(cacheDir, "capture_${'$'}{System.currentTimeMillis()}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(outFile).build()
        imageCapture.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                try {
                    val bmp = BitmapFactory.decodeFile(outFile.absolutePath)
                    if (bmp == null) {
                        onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Decode failed", null))
                        return
                    }
                    runOnUiThread { statusText.text = getString(R.string.desc_analyzing) }
                    val scaled = scaleBitmapMaxSide(bmp, 1024)
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                    val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    callOpenAiVision(base64)
                } catch (t: Throwable) {
                    onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, t.message ?: "", t))
                } finally {
                    runCatching { outFile.delete() }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread {
                    val msg = getString(R.string.desc_error, exception.message ?: "")
                    statusText.text = msg
                    Toast.makeText(this@DescribeSceneActivity, msg, Toast.LENGTH_LONG).show()
                    try { toSpeech.speakObject(msg) } catch (_: Throwable) {}
                }
            }
        })
    }

    private fun callOpenAiVision(jpegBase64: String) {
        // Check API key first
        val apiKey = BuildConfig.OPENAI_API_KEY ?: ""
        if (apiKey.isBlank()) {
            runOnUiThread {
                val msg = "Clé OpenAI absente. Ajoutez OPENAI_API_KEY dans local.properties."
                statusText.text = msg
                try { toSpeech.speakObject(msg) } catch (_: Throwable) {}
            }
            return
        }
        // Build chat.completions payload using gpt-4o-mini vision
        val content = JSONArray()
        content.put(JSONObject().put("type", "text").put("text", "Décris brièvement et clairement à voix haute ce qui est présent sur la photo pour une personne malvoyante."))
        val dataUrl = "data:image/jpeg;base64,$jpegBase64"
        val imageObj = JSONObject()
            .put("type", "image_url")
            .put("image_url", JSONObject().put("url", dataUrl))
        content.put(imageObj)

        val message = JSONObject()
            .put("role", "user")
            .put("content", content)

        val body = JSONObject()
            .put("model", "gpt-4o")
            .put("temperature", 0.2)
            .put("messages", JSONArray().put(message))

        val mediaType = "application/json".toMediaType()
        val requestBody = body.toString().toRequestBody(mediaType)
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val start = System.currentTimeMillis()
        OkHttpClient().newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread {
                    val msg = getString(R.string.desc_error, e.message ?: "")
                    statusText.text = msg
                    try { toSpeech.speakObject(msg) } catch (_: Throwable) {}
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val latencyMs = System.currentTimeMillis() - start
                    val txt = it.body?.string() ?: ""
                    var spoken: String = ""
                    try {
                        val root = JSONObject(txt)
                        if (it.isSuccessful) {
                            val choices = root.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val first = choices.getJSONObject(0)
                                val msgObj = first.getJSONObject("message")
                                // Try string content first
                                spoken = msgObj.optString("content", "")
                                // If empty, try array-of-parts format
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
                            spoken = err?.optString("message") ?: "Erreur API: code ${it.code}"
                        }
                    } catch (_: Throwable) {
                        spoken = if (it.isSuccessful) "Réponse vide" else "Erreur API: code ${it.code}"
                    }

                    runOnUiThread {
                        val withLatency = "$spoken\n(délai LLM: ${latencyMs} ms)"
                        statusText.text = withLatency
                        try { toSpeech.speakObject(spoken) } catch (_: Throwable) {}
                    }
                }
            }
        })
    }

    private fun callOpenAiVisionMulti(jpegsBase64: List<String>) {
        val apiKey = BuildConfig.OPENAI_API_KEY ?: ""
        if (apiKey.isBlank()) {
            runOnUiThread {
                val msg = "Clé OpenAI absente. Ajoutez OPENAI_API_KEY dans local.properties."
                statusText.text = msg
                try { toSpeech.speakObject(msg) } catch (_: Throwable) {}
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
                    try { toSpeech.speakObject(msg) } catch (_: Throwable) {}
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
                            spoken = err?.optString("message") ?: "Erreur API: code ${it.code}"
                        }
                    } catch (_: Throwable) {
                        spoken = if (it.isSuccessful) "Réponse vide" else "Erreur API: code ${it.code}"
                    }
                    runOnUiThread {
                        statusText.text = spoken
                        try { toSpeech.speakObject(spoken) } catch (_: Throwable) {}
                    }
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA) {
            if (allPermissionsGranted()) {
                startCamera()
                startCountdownAndCapture()
            } else {
                Toast.makeText(this, getString(R.string.permission_camera_needed_toast), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { toSpeech.destroy() } catch (_: Throwable) {}
        cameraExecutor.shutdown()
    }
}

// Helper to convert ImageProxy to Bitmap
// Removed raw ImageProxy->Bitmap conversion. Using file-based capture for robustness.
