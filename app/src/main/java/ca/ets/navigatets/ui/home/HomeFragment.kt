package ca.ets.navigatets.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Intent
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import ca.ets.navigatets.R
import ca.ets.navigatets.databinding.FragmentHomeBinding
import ca.ets.navigatets.objectsDetection.ChairOccupancyActivity
import ca.ets.navigatets.describe.DescribeModeChooserActivity
import ca.ets.navigatets.describe.DescribeSceneActivity
import ca.ets.navigatets.describe.DescribeVideoActivity
import ca.ets.navigatets.utils.ToSpeech
import ca.ets.navigatets.llm.LlmClient
import java.text.Normalizer

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by activityViewModels()
    private lateinit var locationAdapter: LocationAdapter

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private var isListening: Boolean = false
    private var lastStartMs: Long = 0L
    private var retryCount: Int = 0
    private val handler = Handler(Looper.getMainLooper())
    private val MIN_START_INTERVAL_MS = 800L
    private val MAX_RETRY = 3
    private var tts: ToSpeech? = null
    private var llm: LlmClient? = null

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startVoiceListening()
            } else {
                // Permission refusée — suppression du Toast
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupRecyclerView()
        setupSearchView()
        setupSpeechRecognizer()
        tts = ToSpeech(requireContext())
        llm = LlmClient()

        homeViewModel.filteredLocations.observe(viewLifecycleOwner) { locations ->
            locationAdapter.submitList(locations)
        }

        homeViewModel.selectedLocationName.observe(viewLifecycleOwner) { name ->
            Log.d("HomeFragment", "Selected location name updated to: $name")
            if (::locationAdapter.isInitialized) {
                locationAdapter.notifyDataSetChanged()
            }
        }

        homeViewModel.navigateToDashboard.observe(viewLifecycleOwner) { shouldNavigate ->
            if (shouldNavigate == true) {
                Log.d("HomeFragment", "Navigating to Dashboard.")
                try {
                    findNavController().navigate(R.id.action_navigation_home_to_navigation_navigate)
                } finally {
                    homeViewModel.onNavigationToDashboardHandled()
                }
            }
        }

        binding.btnVoiceChairs.setOnClickListener {
            ensureAudioPermissionAndListen()
        }

        return root
    }

    private fun setupRecyclerView() {
        locationAdapter = LocationAdapter(homeViewModel)
        binding.recyclerViewLocations.adapter = locationAdapter
    }

    private fun setupSearchView() {
        binding.searchViewLocations.setOnQueryTextListener(object :
            SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                homeViewModel.searchLocations(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                homeViewModel.searchLocations(newText)
                return true
            }
        })
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            // Reconnaissance vocale indisponible — suppression du Toast
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext()).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                }

                override fun onError(error: Int) {
                    when (error) {
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            recreateRecognizerAndRestart()
                        }
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        SpeechRecognizer.ERROR_NO_MATCH -> {
                            stopVoiceListening()
                        }
                        else -> {
                            stopVoiceListening()
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                    if (matches.isEmpty()) { if (isResumed && isListening) scheduleRestart(300L); return }
                    val top = matches.first().trim()
                    val confs = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    val conf = confs?.firstOrNull() ?: -1f
                    val norm = normalizeUtterance(top)

                    // Feedback vocal
                    tts?.speakObject("Tu viens de dire: " + top)

                    // Low confidence: demander de répéter
                    if (conf in 0f..1f && conf < 0.35f) {
                        tts?.speakObject("Je n'ai pas bien entendu. Pouvez-vous répéter ?")
                        stopVoiceListening()
                        return
                    }

                    if (isDescriptionVideoIntent(norm)) {
                        val ctx = requireContext()
                        tts?.speakObject("Veuillez patienter, ouverture de la description vidéo.")
                        startActivity(Intent(ctx, DescribeVideoActivity::class.java))
                        stopVoiceListening()
                    } else if (isDescriptionPhotoIntent(norm)) {
                        val ctx = requireContext()
                        tts?.speakObject("Veuillez patienter, ouverture de la description photo.")
                        startActivity(Intent(ctx, DescribeSceneActivity::class.java))
                        stopVoiceListening()
                    } else if (isDescriptionIntent(norm)) {
                        val ctx = requireContext()
                        tts?.speakObject("Veuillez patienter, ouverture du menu description.")
                        startActivity(Intent(ctx, DescribeModeChooserActivity::class.java))
                        stopVoiceListening()
                    } else if (isChairIntent(norm)) {
                        val ctx = requireContext()
                        tts?.speakObject("Tenez le téléphone devant vous et ne bougez pas, je vérifie la chaise.")
                        startActivity(Intent(ctx, ChairOccupancyActivity::class.java))
                        stopVoiceListening()
                    } else if (norm.contains("recherche") || norm.contains("rechercher")) {
                        tts?.speakObject("Recherche activée. Choisissez un mode de recherche.")
                        stopVoiceListening()
                        findNavController().navigate(R.id.chooseResearchModeFragment)
                    } else {
                        // Fallback LLM: extraction d'intention structurée (function calling)
                        val poiNames: List<String>? = null // TODO: alimenter si tu as une liste de POIs
                        llm?.extractIntentAsync(norm, poiNames) { res ->
                            activity?.runOnUiThread {
                                if (res == null) {
                                    if (isResumed && isListening) scheduleRestart(300L)
                                    return@runOnUiThread
                                }
                                when (res.intent) {
                                    "CHECK_CHAIR_AVAILABILITY" -> {
                                        tts?.speakObject("Tenez le téléphone devant vous et ne bougez pas, je vérifie la chaise.")
                                        val ctx2 = requireContext()
                                        startActivity(Intent(ctx2, ChairOccupancyActivity::class.java))
                                        stopVoiceListening()
                                    }
                                    "NAVIGATE_TO_POI" -> {
                                        // Ici, câbler la navigation vers un POI si applicable dans ton app Android
                                        // tts?.speakObject("Je vous guide vers ${res.poiName ?: "la destination"}.")
                                        // TODO: implémenter l'action de navigation si disponible
                                        if (isResumed && isListening) scheduleRestart(300L)
                                    }
                                    else -> {
                                        tts?.speakObject("Pouvez-vous reformuler, s'il vous plaît ?")
                                        if (isResumed && isListening) scheduleRestart(300L)
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dites 'chaise libre' ou 'chaise occupée'")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
    }

    private fun normalizeUtterance(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFD)
        val noAccents = n.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return noAccents.lowercase()
            .replace("[^a-z0-9\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun isChairIntent(t: String): Boolean {
        // Doit mentionner chaise/seat/chair et un mot-clé d'état ou de demande de s'asseoir
        val hasSeat = t.contains("chaise") || t.contains("chair") || t.contains("seat")
        if (!hasSeat) return false
        val keywords = listOf(
            "libre", "occupe", "occupee", "occupees", "occupees", "occuper",
            "free", "available", "occupied",
            "asseoir", "m assoir", "m assoire", "m assoir", "m assoirai", "s assoir", "s assoire",
            "possible", "ici", "reste", "rester"
        )
        return keywords.any { t.contains(it) }
    }

    private fun isDescriptionVideoIntent(t: String): Boolean {
        // "description video" ou "description vidéo" ou "decrire video"
        return (t.contains("description") || t.contains("decrire") || t.contains("decris")) &&
               (t.contains("video") || t.contains("vidéo") || t.contains("film"))
    }

    private fun isDescriptionPhotoIntent(t: String): Boolean {
        // "description photo" ou "description image" ou "decrire photo"
        return (t.contains("description") || t.contains("decrire") || t.contains("decris")) &&
               (t.contains("photo") || t.contains("image") || t.contains("picture"))
    }

    private fun isDescriptionIntent(t: String): Boolean {
        // Juste "description" sans photo/video
        return (t.contains("description") || t.contains("decrire") || t.contains("decris")) &&
               !isDescriptionVideoIntent(t) && !isDescriptionPhotoIntent(t)
    }

    private fun ensureAudioPermissionAndListen() {
        val hasPermission =
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            startVoiceListening()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceListening() {
        val sr = speechRecognizer
        val intent = recognizerIntent
        if (!isResumed) return
        if (sr != null && intent != null) {
            val now = System.currentTimeMillis()
            val since = now - lastStartMs
            if (since < MIN_START_INTERVAL_MS) {
                scheduleRestart(MIN_START_INTERVAL_MS - since)
                return
            }
            lastStartMs = now
            try {
                sr.cancel()
            } catch (_: Exception) {
            }
            try {
                sr.startListening(intent)
                retryCount = 0
            } catch (_: Exception) {
                recreateRecognizerAndRestart()
                return
            }
            isListening = true
            _binding?.tvListeningIndicator?.visibility = View.VISIBLE
        } else {
            // Micro non prêt — suppression du Toast
        }
    }

    private fun stopVoiceListening() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
        _binding?.tvListeningIndicator?.visibility = View.GONE
    }

    private fun scheduleRestart(delayMs: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (isResumed && isListening) startVoiceListening()
        }, delayMs)
    }

    private fun recreateRecognizerAndRestart() {
        if (!isResumed) return
        stopVoiceListening()
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        if (retryCount >= MAX_RETRY) {
            // Trop d’essais — suppression du Toast
            return
        }
        retryCount++
        setupSpeechRecognizer()
        scheduleRestart(600L)
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Réseau indisponible (délai)"
        SpeechRecognizer.ERROR_NETWORK -> "Erreur réseau"
        SpeechRecognizer.ERROR_AUDIO -> "Erreur audio"
        SpeechRecognizer.ERROR_SERVER -> "Erreur serveur"
        SpeechRecognizer.ERROR_CLIENT -> "Erreur client (service occupé)"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Aucune parole détectée"
        SpeechRecognizer.ERROR_NO_MATCH -> "Aucune correspondance"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconnaissance occupée"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission insuffisante"
        else -> "Erreur inconnue ($code)"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopVoiceListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        binding.recyclerViewLocations.adapter = null
        _binding = null
        try { tts?.destroy() } catch (_: Exception) {}
        tts = null
        llm = null
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        stopVoiceListening()
    }
}
