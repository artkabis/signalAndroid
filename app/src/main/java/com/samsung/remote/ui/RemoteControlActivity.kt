package com.samsung.remote.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.samsung.remote.R
import com.samsung.remote.databinding.ActivityRemoteControlBinding
import com.samsung.remote.databinding.DialogVoiceInputBinding
import com.samsung.remote.model.RemoteKey
import com.samsung.remote.model.SamsungTV
import com.samsung.remote.network.SamsungWebSocketClient
import com.samsung.remote.util.DebugLogger
import com.samsung.remote.util.PreferencesManager
import com.samsung.remote.util.VoiceInputManager

class RemoteControlActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRemoteControlBinding
    private lateinit var webSocketClient: SamsungWebSocketClient
    private lateinit var prefsManager: PreferencesManager
    private lateinit var tv: SamsungTV
    private lateinit var voiceInputManager: VoiceInputManager
    private var voiceInputDialog: AlertDialog? = null
    private var recognizedText: String = ""
    private var isConnected: Boolean = false
    private var isPreviewMode: Boolean = false // Mode aperçu sans connexion

    // Auto-reconnection
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private var isReconnecting = false

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val RECONNECT_DELAY_BASE = 2000L // 2 seconds base delay
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemoteControlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        DebugLogger.i("RemoteControlActivity", "=== Activité de télécommande démarrée ===")

        prefsManager = PreferencesManager(this)

        // Get TV info from intent
        val tvName = intent.getStringExtra("tv_name") ?: "Samsung TV"
        val tvIp = intent.getStringExtra("tv_ip") ?: return finish()
        val tvPort = intent.getIntExtra("tv_port", 8002)
        isPreviewMode = intent.getBooleanExtra("preview_mode", false)

        tv = SamsungTV(tvName, tvIp, tvPort)
        binding.tvNameTextView.text = tvName

        if (isPreviewMode) {
            DebugLogger.i("RemoteControlActivity", "📱 MODE APERÇU - Interface uniquement (pas de connexion)")
            binding.connectionStatusTextView.text = "● Mode Aperçu"
            binding.connectionStatusTextView.setTextColor(getColor(R.color.samsung_blue))
        } else {
            DebugLogger.d("RemoteControlActivity", "TV cible: $tvName ($tvIp:$tvPort)")
        }

        setupWebSocket()
        setupButtons()
        setupVoiceInput()

        if (!isPreviewMode) {
            connectToTV()
        } else {
            // En mode aperçu, activer tous les boutons pour permettre la visualisation
            updateButtonsState(true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu?.findItem(R.id.action_toggle_debug)?.title = if (DebugLogger.isDebugEnabled()) {
            "Mode Debug: ON"
        } else {
            "Mode Debug: OFF"
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_debug -> {
                toggleDebugMode()
                true
            }
            R.id.action_view_logs -> {
                openDebugLogs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleDebugMode() {
        val newState = !DebugLogger.isDebugEnabled()
        DebugLogger.setDebugEnabled(newState, this)

        val message = if (newState) {
            "Mode Debug activé"
        } else {
            "Mode Debug désactivé"
        }

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        invalidateOptionsMenu()
    }

    private fun openDebugLogs() {
        DebugLogger.i("RemoteControlActivity", "Ouverture des logs de debug")
        startActivity(Intent(this, DebugLogsActivity::class.java))
    }

    private fun setupWebSocket() {
        DebugLogger.d("RemoteControlActivity", "Configuration du WebSocket client")
        val deviceName = prefsManager.getDeviceName()
        DebugLogger.i("RemoteControlActivity", "Nom de l'appareil: $deviceName")
        webSocketClient = SamsungWebSocketClient(tv, deviceName, prefsManager)
        webSocketClient.setConnectionListener(object : SamsungWebSocketClient.ConnectionListener {
            override fun onConnected() {
                runOnUiThread {
                    isConnected = true
                    reconnectAttempts = 0 // Reset reconnection attempts on successful connection
                    isReconnecting = false
                    updateConnectionStatus(ConnectionStatus.CONNECTED)
                    updateButtonsState(true)
                    DebugLogger.i("RemoteControlActivity", "✓ Connexion établie avec ${tv.name}")
                    Toast.makeText(this@RemoteControlActivity, "Connecté à ${tv.name}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onDisconnected() {
                runOnUiThread {
                    isConnected = false
                    updateConnectionStatus(ConnectionStatus.DISCONNECTED)
                    updateButtonsState(false)
                    DebugLogger.w("RemoteControlActivity", "⚠ Déconnexion de ${tv.name}")
                    Toast.makeText(this@RemoteControlActivity, "Déconnecté", Toast.LENGTH_SHORT).show()

                    // Attempt automatic reconnection
                    if (!isReconnecting) {
                        attemptReconnect()
                    }
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    isConnected = false
                    updateConnectionStatus(ConnectionStatus.ERROR)
                    updateButtonsState(false)
                    DebugLogger.e("RemoteControlActivity", "❌ Erreur de connexion: $error")
                    Toast.makeText(this@RemoteControlActivity, "Erreur: $error", Toast.LENGTH_SHORT).show()

                    // Attempt automatic reconnection on error
                    if (!isReconnecting) {
                        attemptReconnect()
                    }
                }
            }

            override fun onAuthRequired() {
                runOnUiThread {
                    Toast.makeText(
                        this@RemoteControlActivity,
                        "Autorisation requise - retour à l'appairage",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }

            override fun onAuthSuccess() {
                // Already authenticated
            }
        })
    }

    private fun connectToTV() {
        updateConnectionStatus(ConnectionStatus.CONNECTING)
        val token = prefsManager.getAuthToken()
        if (token != null) {
            DebugLogger.d("RemoteControlActivity", "Connexion avec token sauvegardé")
            webSocketClient.connect(token)
        } else {
            DebugLogger.d("RemoteControlActivity", "Connexion sans token (nouveau pairing)")
            webSocketClient.connect()
        }
    }

    private enum class ConnectionStatus {
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        ERROR
    }

    private fun updateConnectionStatus(status: ConnectionStatus) {
        val (text, color) = when (status) {
            ConnectionStatus.CONNECTING -> {
                R.string.connection_status_connecting to 0xFFFFA500.toInt() // Orange
            }
            ConnectionStatus.CONNECTED -> {
                R.string.connection_status_connected to 0xFF00C853.toInt() // Green
            }
            ConnectionStatus.DISCONNECTED -> {
                R.string.connection_status_disconnected to 0xFF9E9E9E.toInt() // Gray
            }
            ConnectionStatus.ERROR -> {
                R.string.connection_status_error to 0xFFD32F2F.toInt() // Red
            }
        }

        binding.connectionStatusTextView.setText(text)
        binding.connectionStatusTextView.setTextColor(color)
    }

    private fun updateButtonsState(enabled: Boolean) {
        // Power and Menu buttons
        binding.powerButton.isEnabled = enabled
        binding.sourceButton.isEnabled = enabled
        binding.menuButton.isEnabled = enabled

        // Navigation buttons
        binding.upButton.isEnabled = enabled
        binding.downButton.isEnabled = enabled
        binding.leftButton.isEnabled = enabled
        binding.rightButton.isEnabled = enabled
        binding.okButton.isEnabled = enabled

        // Back and Home buttons
        binding.backButton.isEnabled = enabled
        binding.homeButton.isEnabled = enabled

        // Volume and Channel buttons
        binding.volumeUpButton.isEnabled = enabled
        binding.volumeDownButton.isEnabled = enabled
        binding.muteButton.isEnabled = enabled
        binding.channelUpButton.isEnabled = enabled
        binding.channelDownButton.isEnabled = enabled
        binding.infoButton.isEnabled = enabled

        // Media controls
        binding.playButton.isEnabled = enabled
        binding.pauseButton.isEnabled = enabled
        binding.stopButton.isEnabled = enabled
        binding.rewindButton.isEnabled = enabled
        binding.forwardButton.isEnabled = enabled

        // Number pad
        binding.num0Button.isEnabled = enabled
        binding.num1Button.isEnabled = enabled
        binding.num2Button.isEnabled = enabled
        binding.num3Button.isEnabled = enabled
        binding.num4Button.isEnabled = enabled
        binding.num5Button.isEnabled = enabled
        binding.num6Button.isEnabled = enabled
        binding.num7Button.isEnabled = enabled
        binding.num8Button.isEnabled = enabled
        binding.num9Button.isEnabled = enabled

        // Voice input button
        binding.voiceInputButton.isEnabled = enabled
    }

    private fun attemptReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            val message = "Échec de la reconnexion après $maxReconnectAttempts tentatives"
            DebugLogger.e("RemoteControlActivity", message)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            reconnectAttempts = 0
            isReconnecting = false
            return
        }

        isReconnecting = true
        reconnectAttempts++

        // Exponential backoff: 2s, 4s, 8s, 16s, 32s
        val delay = RECONNECT_DELAY_BASE * (1 shl (reconnectAttempts - 1))

        val message = "Tentative de reconnexion ($reconnectAttempts/$maxReconnectAttempts) dans ${delay / 1000}s..."
        DebugLogger.i("RemoteControlActivity", message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        updateConnectionStatus(ConnectionStatus.CONNECTING)

        reconnectHandler.postDelayed({
            if (!isConnected) {
                DebugLogger.d("RemoteControlActivity", "Exécution de la tentative de reconnexion #$reconnectAttempts")
                connectToTV()
            }
        }, delay)
    }

    private fun cancelReconnect() {
        reconnectHandler.removeCallbacksAndMessages(null)
        isReconnecting = false
        reconnectAttempts = 0
    }

    private fun setupButtons() {
        // Power and Menu
        binding.powerButton.setOnClickListener { sendKey(RemoteKey.KEY_POWER) }
        binding.sourceButton.setOnClickListener { sendKey(RemoteKey.KEY_SOURCE) }
        binding.menuButton.setOnClickListener { sendKey(RemoteKey.KEY_MENU) }

        // Navigation
        binding.upButton.setOnClickListener { sendKey(RemoteKey.KEY_UP) }
        binding.downButton.setOnClickListener { sendKey(RemoteKey.KEY_DOWN) }
        binding.leftButton.setOnClickListener { sendKey(RemoteKey.KEY_LEFT) }
        binding.rightButton.setOnClickListener { sendKey(RemoteKey.KEY_RIGHT) }
        binding.okButton.setOnClickListener { sendKey(RemoteKey.KEY_ENTER) }

        // Back and Home
        binding.backButton.setOnClickListener { sendKey(RemoteKey.KEY_RETURN) }
        binding.homeButton.setOnClickListener { sendKey(RemoteKey.KEY_HOME) }

        // Volume
        binding.volumeUpButton.setOnClickListener { sendKey(RemoteKey.KEY_VOLUP) }
        binding.volumeDownButton.setOnClickListener { sendKey(RemoteKey.KEY_VOLDOWN) }
        binding.muteButton.setOnClickListener { sendKey(RemoteKey.KEY_MUTE) }

        // Channel
        binding.channelUpButton.setOnClickListener { sendKey(RemoteKey.KEY_CHUP) }
        binding.channelDownButton.setOnClickListener { sendKey(RemoteKey.KEY_CHDOWN) }

        // Info
        binding.infoButton.setOnClickListener { sendKey(RemoteKey.KEY_INFO) }

        // Media Controls
        binding.playButton.setOnClickListener { sendKey(RemoteKey.KEY_PLAY) }
        binding.pauseButton.setOnClickListener { sendKey(RemoteKey.KEY_PAUSE) }
        binding.stopButton.setOnClickListener { sendKey(RemoteKey.KEY_STOP) }
        binding.rewindButton.setOnClickListener { sendKey(RemoteKey.KEY_REWIND) }
        binding.forwardButton.setOnClickListener { sendKey(RemoteKey.KEY_FF) }

        // Number Pad
        binding.num0Button.setOnClickListener { sendKey(RemoteKey.KEY_0) }
        binding.num1Button.setOnClickListener { sendKey(RemoteKey.KEY_1) }
        binding.num2Button.setOnClickListener { sendKey(RemoteKey.KEY_2) }
        binding.num3Button.setOnClickListener { sendKey(RemoteKey.KEY_3) }
        binding.num4Button.setOnClickListener { sendKey(RemoteKey.KEY_4) }
        binding.num5Button.setOnClickListener { sendKey(RemoteKey.KEY_5) }
        binding.num6Button.setOnClickListener { sendKey(RemoteKey.KEY_6) }
        binding.num7Button.setOnClickListener { sendKey(RemoteKey.KEY_7) }
        binding.num8Button.setOnClickListener { sendKey(RemoteKey.KEY_8) }
        binding.num9Button.setOnClickListener { sendKey(RemoteKey.KEY_9) }
    }

    private fun sendKey(key: RemoteKey) {
        if (isPreviewMode) {
            // En mode aperçu, afficher simplement l'action sans l'envoyer
            Toast.makeText(this, "Mode Aperçu: ${key.keyCode}", Toast.LENGTH_SHORT).show()
            DebugLogger.d("RemoteControlActivity", "Mode preview - Touche simulée: ${key.keyCode}")
            return
        }

        if (webSocketClient.isConnected()) {
            DebugLogger.d("RemoteControlActivity", "Envoi touche: ${key.keyCode}")
            webSocketClient.sendKey(key)
        } else {
            DebugLogger.w("RemoteControlActivity", "Tentative d'envoi de touche sans connexion: ${key.keyCode}")
            Toast.makeText(this, "Non connecté à la TV", Toast.LENGTH_SHORT).show()
            connectToTV()
        }
    }

    private fun setupVoiceInput() {
        voiceInputManager = VoiceInputManager(this)

        binding.voiceInputButton.setOnClickListener {
            if (checkAudioPermission()) {
                showVoiceInputDialog()
            } else {
                requestAudioPermission()
            }
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showVoiceInputDialog()
            } else {
                Toast.makeText(
                    this,
                    R.string.voice_input_permission_denied,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showVoiceInputDialog() {
        val dialogBinding = DialogVoiceInputBinding.inflate(LayoutInflater.from(this))
        recognizedText = ""

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        voiceInputDialog = dialog

        // Configuration du VoiceInputManager
        voiceInputManager.setVoiceInputListener(object : VoiceInputManager.VoiceInputListener {
            override fun onListeningStarted() {
                runOnUiThread {
                    dialogBinding.instructionTextView.text = getString(R.string.voice_input_listening)
                    dialogBinding.recognizedTextView.text = ""
                }
            }

            override fun onPartialResult(text: String) {
                runOnUiThread {
                    dialogBinding.recognizedTextView.text = text
                    recognizedText = text
                }
            }

            override fun onFinalResult(text: String) {
                runOnUiThread {
                    dialogBinding.recognizedTextView.text = text
                    recognizedText = text
                    dialogBinding.sendButton.isEnabled = text.isNotEmpty()
                }
            }

            override fun onError(errorMessage: String) {
                runOnUiThread {
                    Toast.makeText(this@RemoteControlActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    dialogBinding.instructionTextView.text = getString(R.string.voice_input_error)
                }
            }

            override fun onListeningEnded() {
                runOnUiThread {
                    dialogBinding.instructionTextView.text = getString(R.string.voice_input_ready)
                }
            }
        })

        // Bouton Annuler
        dialogBinding.cancelButton.setOnClickListener {
            voiceInputManager.stopListening()
            dialog.dismiss()
        }

        // Bouton Envoyer
        dialogBinding.sendButton.setOnClickListener {
            if (recognizedText.isNotEmpty()) {
                sendTextToTV(recognizedText)
                dialog.dismiss()
            } else {
                Toast.makeText(
                    this,
                    R.string.voice_input_no_text,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        dialog.setOnDismissListener {
            voiceInputManager.stopListening()
            voiceInputDialog = null
        }

        dialog.show()

        // Démarrer l'écoute automatiquement
        voiceInputManager.startListening("fr-FR")
    }

    private fun sendTextToTV(text: String) {
        if (isPreviewMode) {
            // En mode aperçu, afficher simplement le texte qui aurait été envoyé
            Toast.makeText(this, "Mode Aperçu - Texte: \"$text\"", Toast.LENGTH_LONG).show()
            DebugLogger.d("RemoteControlActivity", "Mode preview - Texte simulé: \"$text\"")
            return
        }

        if (webSocketClient.isConnected()) {
            DebugLogger.i("RemoteControlActivity", "Envoi de texte vers la TV: \"$text\"")

            // First, send a search key to open the search bar
            // This ensures the text input field is focused
            DebugLogger.d("RemoteControlActivity", "Ouverture de la barre de recherche (KEY_SEARCH)")
            webSocketClient.sendKey(RemoteKey.KEY_SEARCH)

            // Wait a bit for the search bar to open, then send the text
            Handler(Looper.getMainLooper()).postDelayed({
                if (webSocketClient.isConnected()) {
                    DebugLogger.d("RemoteControlActivity", "Envoi du texte via SendInputString")
                    webSocketClient.sendText(text, useDirectMethod = true)
                    Toast.makeText(this, R.string.voice_input_sent, Toast.LENGTH_SHORT).show()
                }
            }, 500) // 500ms delay to allow search bar to open
        } else {
            DebugLogger.w("RemoteControlActivity", "Tentative d'envoi de texte sans connexion: \"$text\"")
            Toast.makeText(this, "Non connecté à la TV", Toast.LENGTH_SHORT).show()
            connectToTV()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.i("RemoteControlActivity", "=== Fermeture de l'activité de télécommande ===")
        cancelReconnect()
        voiceInputManager.destroy()
        webSocketClient.disconnect()
        voiceInputDialog?.dismiss()
    }
}
