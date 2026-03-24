package ca.ets.navigatets.ui.search

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import ca.ets.navigatets.BuildConfig
import ca.ets.navigatets.databinding.FragmentRechercheVideoBinding
import ca.ets.navigatets.utils.BitmapUtils
import ca.ets.navigatets.utils.ToSpeech
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RechercheVideoFragment : Fragment() {
    private var _binding: FragmentRechercheVideoBinding? = null
    private val binding get() = _binding!!

    private var toSpeech: ToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var searchedObjet: String? = null

    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService? = null
    private var isRecording = false
    private var captureTimer: CountDownTimer? = null
    private val capturedFrames = mutableListOf<String>()
    private val MAX_DURATION_MS = 10000L
    private val CAPTURE_INTERVAL_MS = 1000L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRechercheVideoBinding.inflate(inflater, container, false)
        toSpeech = ToSpeech(requireContext())
        cameraExecutor = Executors.newSingleThreadExecutor()

        toSpeech?.speakObject("Que recherchez-vous ?")

        binding.btnRecord.setOnClickListener {
            if (!isRecording) {
                if (searchedObjet.isNullOrBlank()) {
                    startVoiceRecognition()
                } else {
                    startVideoCapture()
                }
            } else {
                stopVideoCapture()
            }
        }

        startCamera()
        startVoiceRecognition()

        return binding.root
    }

    private fun startCamera() {
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(binding.previewView.surfaceProvider)
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                binding.result.text = "Erreur camera: " + exc.message
                toSpeech?.speakObject("Erreur camera")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun startVoiceRecognition() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Quel objet voulez-vous rechercher ?")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.result.text = "Ecoute en cours..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Aucune correspondance. Reessayez."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Temps ecoule. Reessayez."
                    else -> "Erreur de reconnaissance. Reessayez."
                }
                binding.result.text = msg
                toSpeech?.speakObject(msg)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val objet = matches?.firstOrNull()?.trim() ?: ""
                if (objet.isNotBlank()) {
                    searchedObjet = objet
                    binding.result.text = "Recherche: " + objet + "\nAppuyez sur Enregistrer"
                    toSpeech?.speakObject("Vous recherchez " + objet + ". Appuyez sur enregistrer.")
                    binding.btnRecord.text = "Enregistrer"
                } else {
                    binding.result.text = "Aucun objet detecte. Reessayez."
                    toSpeech?.speakObject("Aucun objet detecte.")
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun startVideoCapture() {
        if (searchedObjet.isNullOrBlank()) {
            toSpeech?.speakObject("Veuillez d'abord dire quel objet vous recherchez.")
            return
        }

        isRecording = true
        capturedFrames.clear()
        binding.btnRecord.text = "Arreter"
        binding.result.text = "Enregistrement en cours..."
        toSpeech?.speakObject("Enregistrement demarre. Maximum 10 secondes.")

        captureTimer = object : CountDownTimer(MAX_DURATION_MS, CAPTURE_INTERVAL_MS) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsed = MAX_DURATION_MS - millisUntilFinished
                val seconds = (elapsed / 1000).toInt()
                binding.timer.text = seconds.toString() + "s / 10s"
                captureFrame()
            }

            override fun onFinish() {
                binding.timer.text = "10s / 10s"
                captureFrame()
                stopVideoCapture()
            }
        }.start()
    }

    private fun stopVideoCapture() {
        isRecording = false
        captureTimer?.cancel()
        binding.btnRecord.text = "Enregistrer"
        binding.timer.text = ""

        if (capturedFrames.isNotEmpty()) {
            binding.result.text = "Analyse de " + capturedFrames.size + " images..."
            toSpeech?.speakObject("Analyse en cours, veuillez patienter.")
            analyzeFramesWithOpenAI()
        } else {
            binding.result.text = "Aucune image capturee."
            toSpeech?.speakObject("Aucune image capturee.")
        }
    }

    private fun captureFrame() {
        val ic = imageCapture ?: return
        val outFile = File(requireContext().cacheDir, "frame_" + System.currentTimeMillis() + ".jpg")
        val output = ImageCapture.OutputFileOptions.Builder(outFile).build()

        ic.takePicture(output, cameraExecutor!!, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                try {
                    val bmp = android.graphics.BitmapFactory.decodeFile(outFile.absolutePath)
                    if (bmp != null) {
                        val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 512, 512 * bmp.height / bmp.width, true)
                        val base64 = BitmapUtils.bitmapToBase64(scaled)
                        synchronized(capturedFrames) {
                            capturedFrames.add(base64)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RechercheVideo", "Erreur capture frame", e)
                } finally {
                    outFile.delete()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("RechercheVideo", "Erreur capture: " + exception.message)
            }
        })
    }

    private fun analyzeFramesWithOpenAI() {
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isNullOrEmpty()) {
            activity?.runOnUiThread {
                binding.result.text = "Cle OpenAI absente."
                toSpeech?.speakObject("Cle OpenAI absente.")
            }
            return
        }

        val prompt = "J'ai capture plusieurs images d'une video. Dis-moi si l'objet '" + searchedObjet + "' est present dans au moins une de ces images. Reponds simplement par oui ou non, puis explique brievement."

        try {
            val content = JSONArray()
            content.put(JSONObject().put("type", "text").put("text", prompt))

            synchronized(capturedFrames) {
                for (base64 in capturedFrames) {
                    val dataUrl = "data:image/jpeg;base64," + base64
                    val imageObj = JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", dataUrl))
                    content.put(imageObj)
                }
            }

            val message = JSONObject().put("role", "user").put("content", content)
            val body = JSONObject()
                .put("model", "gpt-4o")
                .put("temperature", 0.2)
                .put("messages", JSONArray().put(message))

            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = body.toString().toRequestBody(mediaType)
            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val client = OkHttpClient()
            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    activity?.runOnUiThread {
                        val msg = "Erreur reseau: " + e.message
                        binding.result.text = msg
                        toSpeech?.speakObject(msg)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val txt = response.body?.string() ?: ""
                    var spoken = ""
                    try {
                        val root = JSONObject(txt)
                        if (response.isSuccessful) {
                            val choices = root.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val msgObj = choices.getJSONObject(0).getJSONObject("message")
                                spoken = msgObj.optString("content", "")
                            }
                            if (spoken.isEmpty()) spoken = "Aucune reponse."
                        } else {
                            val err = root.optJSONObject("error")
                            spoken = if (err != null) err.optString("message") else "Erreur API: code " + response.code
                        }
                    } catch (t: Throwable) {
                        spoken = if (response.isSuccessful) "Reponse vide" else "Erreur API: code " + response.code
                    }

                    val finalSpoken = spoken
                    activity?.runOnUiThread {
                        binding.result.text = finalSpoken
                        toSpeech?.speakObject(finalSpoken)
                    }
                }
            })
        } catch (e: Exception) {
            activity?.runOnUiThread {
                val msg = "Erreur JSON: " + e.message
                binding.result.text = msg
                toSpeech?.speakObject(msg)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        captureTimer?.cancel()
        speechRecognizer?.destroy()
        cameraExecutor?.shutdown()
        toSpeech?.destroy()
        _binding = null
    }
}
