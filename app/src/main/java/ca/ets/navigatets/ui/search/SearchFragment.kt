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
import ca.ets.navigatets.SEARCHED_OBJECT
import ca.ets.navigatets.databinding.FragmentSearchBinding
import ca.ets.navigatets.objectsResearch.ObjectResearchActivity
import ca.ets.navigatets.utils.ToSpeech

class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private var toSpeech: ToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        toSpeech = ToSpeech(requireContext())
        toSpeech?.speakObject("Que recherchez-vous ?")

        binding.btnRecord.setOnClickListener {
            startVoiceRecognition()
        }

        binding.btnCamera.setOnClickListener {
            val objet = binding.tvRecognized.text.toString().trim()
            if (objet.isNotBlank()) {
                launchObjectResearch(objet)
            }
        }

        binding.root.post {
            startVoiceRecognition()
        }

        return binding.root
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
                binding.btnRecord.text = "Ecoute en cours..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                binding.btnRecord.text = "Demarrer l'enregistrement"
            }
            override fun onError(error: Int) {
                binding.btnRecord.text = "Demarrer l'enregistrement"
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Aucune correspondance trouvee. Reessayez."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Temps ecoule. Reessayez."
                    else -> "Erreur de reconnaissance. Reessayez."
                }
                binding.tvRecognized.text = msg
                toSpeech?.speakObject(msg)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val objet = matches?.firstOrNull()?.trim() ?: ""
                if (objet.isNotBlank()) {
                    binding.tvRecognized.text = objet
                    binding.btnCamera.visibility = View.VISIBLE
                    toSpeech?.speakObject("Vous recherchez: " + objet + ". Lancement de la camera.")
                    launchObjectResearch(objet)
                } else {
                    binding.tvRecognized.text = "Aucun objet detecte. Reessayez."
                    toSpeech?.speakObject("Aucun objet detecte. Reessayez.")
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun launchObjectResearch(objet: String) {
        val intent = Intent(requireContext(), ObjectResearchActivity::class.java)
        intent.putExtra(SEARCHED_OBJECT, objet)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        speechRecognizer?.destroy()
        toSpeech?.destroy()
        _binding = null
    }
}
