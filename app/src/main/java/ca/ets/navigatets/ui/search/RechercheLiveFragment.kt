package ca.ets.navigatets.ui.search

import android.content.Intent
import android.graphics.RectF
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.navigation.fragment.findNavController
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import ca.ets.navigatets.databinding.FragmentRechercheLiveBinding
import ca.ets.navigatets.models.Detection
import ca.ets.navigatets.objectsDetection.DetectionResult
import ca.ets.navigatets.utils.CameraFrameAnalyzer
import ca.ets.navigatets.utils.CameraUtils
import ca.ets.navigatets.utils.ObjectDetectionManager
import ca.ets.navigatets.utils.ToSpeech
import java.util.ArrayDeque
import java.util.concurrent.Executors

class RechercheLiveFragment : Fragment() {
    private var _binding: FragmentRechercheLiveBinding? = null
    private val binding get() = _binding!!

    private var toSpeech: ToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var searchedObject: String? = null
    private var searchedObjectEnglish: String? = null

    private var cameraFrameAnalyzer: CameraFrameAnalyzer? = null
    private var isAnalyzing = false

    private var lastFoundTime = 0L
    private var lastNotFoundTime = 0L
    private val ANNOUNCE_COOLDOWN_MS = 3000L

    private var focalLengthPixels = -1f

    private val detectionHistory = ArrayDeque<Boolean>()
    private val HISTORY_MAX = 8
    private var holdCounter = 0
    private val HOLD_MIN_FRAMES = 3
    private var stableState: Boolean? = null
    
    private var gestureDetector: GestureDetector? = null
    private var lastTapTime = 0L

    private val frenchToEnglish = mapOf(
        "personne" to "person", "homme" to "person", "femme" to "person", "gens" to "person",
        "velo" to "bicycle", "bicyclette" to "bicycle",
        "voiture" to "car", "auto" to "car", "automobile" to "car",
        "moto" to "motorcycle", "motocyclette" to "motorcycle",
        "avion" to "airplane",
        "bus" to "bus", "autobus" to "bus",
        "train" to "train",
        "camion" to "truck",
        "bateau" to "boat",
        "feu" to "traffic light", "feu de circulation" to "traffic light",
        "bouche d'incendie" to "fire hydrant",
        "panneau stop" to "stop sign", "stop" to "stop sign",
        "parcmetre" to "parking meter",
        "banc" to "bench",
        "oiseau" to "bird",
        "chat" to "cat",
        "chien" to "dog",
        "cheval" to "horse",
        "mouton" to "sheep",
        "vache" to "cow",
        "elephant" to "elephant",
        "ours" to "bear",
        "zebre" to "zebra",
        "girafe" to "giraffe",
        "sac a dos" to "backpack", "sac" to "backpack",
        "parapluie" to "umbrella",
        "sac a main" to "handbag",
        "cravate" to "tie",
        "valise" to "suitcase",
        "frisbee" to "frisbee",
        "ski" to "skis", "skis" to "skis",
        "snowboard" to "snowboard",
        "ballon" to "sports ball", "balle" to "sports ball",
        "cerf-volant" to "kite",
        "batte" to "baseball bat",
        "gant" to "baseball glove",
        "skateboard" to "skateboard", "planche a roulettes" to "skateboard",
        "planche de surf" to "surfboard", "surf" to "surfboard",
        "raquette" to "tennis racket",
        "bouteille" to "bottle",
        "verre" to "wine glass", "verre de vin" to "wine glass",
        "tasse" to "cup", "gobelet" to "cup",
        "fourchette" to "fork",
        "couteau" to "knife",
        "cuillere" to "spoon",
        "bol" to "bowl",
        "banane" to "banana",
        "pomme" to "apple",
        "sandwich" to "sandwich",
        "orange" to "orange",
        "brocoli" to "broccoli",
        "carotte" to "carrot",
        "hot dog" to "hot dog", "hotdog" to "hot dog",
        "pizza" to "pizza",
        "beignet" to "donut", "donut" to "donut",
        "gateau" to "cake",
        "chaise" to "chair", "siege" to "chair",
        "canape" to "couch", "sofa" to "couch", "divan" to "couch",
        "plante" to "potted plant",
        "lit" to "bed",
        "table" to "dining table",
        "toilette" to "toilet", "toilettes" to "toilet",
        "tele" to "tv", "television" to "tv", "televiseur" to "tv",
        "ordinateur" to "laptop", "portable" to "laptop", "laptop" to "laptop",
        "souris" to "mouse",
        "telecommande" to "remote",
        "clavier" to "keyboard",
        "telephone" to "cell phone", "cellulaire" to "cell phone", "portable" to "cell phone",
        "micro-ondes" to "microwave",
        "four" to "oven",
        "grille-pain" to "toaster",
        "evier" to "sink", "lavabo" to "sink",
        "refrigerateur" to "refrigerator", "frigo" to "refrigerator",
        "livre" to "book",
        "horloge" to "clock", "montre" to "clock",
        "vase" to "vase",
        "ciseaux" to "scissors",
        "peluche" to "teddy bear", "ourson" to "teddy bear", "nounours" to "teddy bear",
        "seche-cheveux" to "hair drier",
        "brosse a dents" to "toothbrush"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRechercheLiveBinding.inflate(inflater, container, false)
        
        toSpeech = ToSpeech(requireContext()) {
            activity?.runOnUiThread {
                if (isAdded && _binding != null) {
                    toSpeech?.speakObject("Recherche live. Que recherchez-vous? Double-tapez pour nouvelle recherche. Glissez vers la gauche pour changer de mode.")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (isAdded && _binding != null) {
                            startVoiceRecognition()
                        }
                    }, 5000)
                }
            }
        }

        setupGestureDetection()

        return binding.root
    }

    private fun setupGestureDetection() {
        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                activity?.runOnUiThread {
                    restartSearch()
                }
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                if (diffX < -100 && Math.abs(velocityX) > 100) {
                    activity?.runOnUiThread {
                        goBackToModeSelection()
                    }
                    return true
                }
                return false
            }
        })

        binding.root.setOnTouchListener { v, event ->
            val handled = gestureDetector?.onTouchEvent(event) ?: false
            if (!handled) {
                v.performClick()
            }
            true
        }
    }

    private fun restartSearch() {
        isAnalyzing = false
        searchedObject = null
        searchedObjectEnglish = null
        detectionHistory.clear()
        stableState = null
        
        activity?.runOnUiThread {
            binding.tvSearchedObject.text = "Objet recherche: ..."
            binding.tvStatus.text = "Nouvelle recherche..."
            binding.overlayView.setDetectionResults(emptyList())
        }
        
        toSpeech?.speakObject("Nouvelle recherche. Que recherchez-vous?")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isAdded && _binding != null) {
                startVoiceRecognition()
            }
        }, 2000)
    }

    private fun goBackToModeSelection() {
        isAnalyzing = false
        toSpeech?.speakObject("Retour au choix du mode de recherche.")
        findNavController().popBackStack()
    }

    private fun translateToEnglish(frenchWord: String): String {
        val normalized = frenchWord.lowercase().trim()
        frenchToEnglish[normalized]?.let { return it }
        for ((fr, en) in frenchToEnglish) {
            if (normalized.contains(fr) || fr.contains(normalized)) {
                return en
            }
        }
        return normalized
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
                binding.tvStatus.text = "Ecoute en cours..."
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
                binding.tvStatus.text = msg
                toSpeech?.speakObject(msg)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val objet = matches?.firstOrNull()?.trim() ?: ""
                if (objet.isNotBlank()) {
                    searchedObject = objet.lowercase()
                    searchedObjectEnglish = translateToEnglish(objet)
                    binding.tvSearchedObject.text = "Recherche: " + objet
                    binding.tvStatus.text = "Recherche de: " + objet
                    toSpeech?.speakObject("Recherche de " + objet + " en cours.")
                    initializeObjectDetection()
                    startCamera()
                } else {
                    binding.tvStatus.text = "Aucun objet detecte. Reessayez."
                    toSpeech?.speakObject("Aucun objet detecte.")
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun initializeObjectDetection() {
        val odm = ObjectDetectionManager(requireContext())
        cameraFrameAnalyzer = CameraFrameAnalyzer(0.30f, odm) { detections, bitmap ->
            val targetEn = searchedObjectEnglish ?: return@CameraFrameAnalyzer
            val targetFr = searchedObject ?: return@CameraFrameAnalyzer

            var foundDetection: Detection? = null
            for (d in detections) {
                val lbl = d.label().lowercase()
                if (lbl.contains(targetEn) || targetEn.contains(lbl) ||
                    lbl.contains(targetFr) || targetFr.contains(lbl)) {
                    foundDetection = d
                    break
                }
            }

            val now = System.currentTimeMillis()
            val results = mutableListOf<DetectionResult>()
            val objectFound = foundDetection != null

            if (detectionHistory.size >= HISTORY_MAX) detectionHistory.removeFirst()
            detectionHistory.addLast(objectFound)

            var countTrue = 0
            for (v in detectionHistory) if (v) countTrue++
            val majority = countTrue >= Math.ceil(detectionHistory.size * 0.6).toInt()

            if (foundDetection != null) {
                val box = RectF(foundDetection.boundingBox())

                if (focalLengthPixels <= 0) {
                    focalLengthPixels = CameraUtils.getFocalLengthInPixels(requireContext())
                }
                val distanceM = estimateDistanceMeters(0.3, box.width(), focalLengthPixels)
                val distanceStr = if (distanceM > 0) String.format(java.util.Locale.ENGLISH, " (%.2f m)", distanceM) else ""

                val direction = getDirection(box, bitmap.width)
                val labelText = targetFr.uppercase() + " TROUVE" + distanceStr
                results.add(DetectionResult(box, labelText, foundDetection.confidence()))

                activity?.runOnUiThread {
                    binding.statusBadge.setBackgroundColor(0xAA43A047.toInt())
                    binding.tvStatus.text = "TROUVE: " + targetFr
                }

                if (stableState == null || majority != stableState) {
                    holdCounter++
                    if (holdCounter >= HOLD_MIN_FRAMES) {
                        stableState = majority
                        holdCounter = 0
                        if (majority && now - lastFoundTime > ANNOUNCE_COOLDOWN_MS) {
                            lastFoundTime = now
                            val dirMsg = when (direction) {
                                "left" -> targetFr + " detecte a gauche"
                                "right" -> targetFr + " detecte a droite"
                                else -> targetFr + " detecte devant vous"
                            }
                            val distMsg = if (distanceM > 0) ", a environ " + String.format(java.util.Locale.ENGLISH, "%.1f", distanceM) + " metres" else ""
                            activity?.runOnUiThread {
                                toSpeech?.speakObject(dirMsg + distMsg)
                            }
                        }
                    }
                } else {
                    holdCounter = 0
                }
            } else {
                activity?.runOnUiThread {
                    binding.statusBadge.setBackgroundColor(0xAAFFC107.toInt())
                    binding.tvStatus.text = "Recherche de: " + targetFr
                }

                if (stableState == null || majority != stableState) {
                    holdCounter++
                    if (holdCounter >= HOLD_MIN_FRAMES) {
                        stableState = majority
                        holdCounter = 0
                    }
                } else {
                    holdCounter = 0
                }

                if (now - lastNotFoundTime > ANNOUNCE_COOLDOWN_MS * 2) {
                    lastNotFoundTime = now
                    activity?.runOnUiThread {
                        toSpeech?.speakObject(targetFr + " non visible. Continuez a chercher.")
                    }
                }
            }

            activity?.runOnUiThread {
                binding.overlayView.setModelInputImageSize(bitmap.width.toFloat(), bitmap.height.toFloat())
                binding.overlayView.setDetectionResults(results)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(binding.previewView.surfaceProvider)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    if (isAnalyzing) {
                        cameraFrameAnalyzer?.runObjectDetection(imageProxy)
                    } else {
                        imageProxy.close()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageAnalysis)

                isAnalyzing = true

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erreur camera: " + e.message, Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun estimateDistanceMeters(realWidthMeters: Double, bboxWidthPixels: Float, focalPixels: Float): Double {
        if (focalPixels <= 0 || bboxWidthPixels <= 0) return -1.0
        return (realWidthMeters * focalPixels) / bboxWidthPixels
    }

    private fun getDirection(box: RectF, imageWidth: Int): String {
        val centerX = (box.left + box.right) / 2f
        val leftThreshold = imageWidth / 3f
        val rightThreshold = imageWidth * 2f / 3f

        return when {
            centerX < leftThreshold -> "left"
            centerX > rightThreshold -> "right"
            else -> "center"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isAnalyzing = false
        speechRecognizer?.destroy()
        toSpeech?.destroy()
        _binding = null
    }
}
