package ca.ets.navigatets.ui.search

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import ca.ets.navigatets.R
import ca.ets.navigatets.databinding.FragmentChooseResearchModeBinding
import ca.ets.navigatets.utils.ToSpeech

class ChooseResearchModeFragment : Fragment() {
    private var _binding: FragmentChooseResearchModeBinding? = null
    private val binding get() = _binding!!

    private var tts: ToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentChooseResearchModeBinding.inflate(inflater, container, false)
        val root = binding.root
        
        tts = ToSpeech(requireContext()) {
            if (isAdded && _binding != null) {
                announceModesAndListen()
            }
        }

        binding.btnRecherchePhoto.setOnClickListener {
            findNavController().navigate(R.id.action_chooseResearchMode_to_searchFragment)
        }
        binding.btnRechercheVideo.setOnClickListener {
            findNavController().navigate(R.id.action_chooseResearchMode_to_rechercheVideoFragment)
        }
        binding.btnRechercheLive.setOnClickListener {
            findNavController().navigate(R.id.action_chooseResearchMode_to_rechercheLiveFragment)
        }

        return root
    }

    private fun announceModesAndListen() {
        tts?.speakObject("Quel mode de recherche voulez-vous? Les modes disponibles sont: recherche photo, recherche video, et recherche live.")
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isAdded && _binding != null) {
                startVoiceRecognitionForMode()
            }
        }, 6000)
    }

    private fun startVoiceRecognitionForMode() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dites recherche photo, video ou live")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                // Relancer l'écoute en cas d'erreur (timeout, etc.)
                if (isAdded && _binding != null) {
                    startVoiceRecognitionForMode()
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                val spoken = matches.firstOrNull()?.lowercase()?.trim() ?: ""
                handleVoiceCommand(spoken)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun handleVoiceCommand(spoken: String) {
        when {
            spoken.contains("photo") || spoken.contains("foto") -> {
                tts?.speakObject("Ouverture de la recherche photo")
                findNavController().navigate(R.id.action_chooseResearchMode_to_searchFragment)
            }
            spoken.contains("video") || spoken.contains("vidéo") -> {
                tts?.speakObject("Ouverture de la recherche video")
                findNavController().navigate(R.id.action_chooseResearchMode_to_rechercheVideoFragment)
            }
            spoken.contains("live") || spoken.contains("direct") || spoken.contains("temps reel") || spoken.contains("temps réel") -> {
                tts?.speakObject("Ouverture de la recherche live")
                findNavController().navigate(R.id.action_chooseResearchMode_to_rechercheLiveFragment)
            }
            else -> {
                tts?.speakObject("Commande non reconnue. Dites recherche photo, video ou live.")
                if (isAdded && _binding != null) {
                    startVoiceRecognitionForMode()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.destroy()
        _binding = null
    }
}
