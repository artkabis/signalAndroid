package com.samsung.remote.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class VoiceInputManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    companion object {
        private const val TAG = "VoiceInputManager"
    }

    interface VoiceInputListener {
        fun onListeningStarted()
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onError(errorMessage: String)
        fun onListeningEnded()
    }

    private var listener: VoiceInputListener? = null

    fun setVoiceInputListener(listener: VoiceInputListener) {
        this.listener = listener
    }

    fun startListening(language: String = "fr-FR") {
        if (isListening) {
            Log.w(TAG, "Already listening")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener?.onError("Reconnaissance vocale non disponible sur cet appareil")
            return
        }

        // Créer le SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
                isListening = true
                listener?.onListeningStarted()
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Niveau sonore (optionnel pour animation)
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Données audio brutes (non utilisées)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partialText = matches[0]
                    Log.d(TAG, "Partial result: $partialText")
                    listener?.onPartialResult(partialText)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val finalText = matches[0]
                    Log.d(TAG, "Final result: $finalText")
                    listener?.onFinalResult(finalText)
                } else {
                    listener?.onError("Aucun résultat reconnu")
                }
                isListening = false
                listener?.onListeningEnded()
            }

            override fun onError(error: Int) {
                val errorMessage = getErrorMessage(error)
                Log.e(TAG, "Speech recognition error: $errorMessage")
                isListening = false
                listener?.onError(errorMessage)
                listener?.onListeningEnded()
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech")
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Événements personnalisés (non utilisés)
            }
        })

        // Configurer l'intent de reconnaissance
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        // Démarrer l'écoute
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            listener?.onError("Erreur lors du démarrage : ${e.message}")
            isListening = false
        }
    }

    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }

    private fun getErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Erreur audio"
            SpeechRecognizer.ERROR_CLIENT -> "Erreur client"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission microphone refusée"
            SpeechRecognizer.ERROR_NETWORK -> "Erreur réseau"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Délai réseau dépassé"
            SpeechRecognizer.ERROR_NO_MATCH -> "Aucune correspondance trouvée"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconnaissance occupée"
            SpeechRecognizer.ERROR_SERVER -> "Erreur serveur"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Aucun son détecté"
            else -> "Erreur inconnue ($error)"
        }
    }

    fun isCurrentlyListening(): Boolean = isListening
}
