package com.samsung.remote.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.samsung.remote.databinding.ActivityRemoteControlBinding
import com.samsung.remote.model.RemoteKey
import com.samsung.remote.model.SamsungTV
import com.samsung.remote.network.SamsungWebSocketClient
import com.samsung.remote.util.PreferencesManager

class RemoteControlActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRemoteControlBinding
    private lateinit var webSocketClient: SamsungWebSocketClient
    private lateinit var prefsManager: PreferencesManager
    private lateinit var tv: SamsungTV

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemoteControlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PreferencesManager(this)

        // Get TV info from intent
        val tvName = intent.getStringExtra("tv_name") ?: "Samsung TV"
        val tvIp = intent.getStringExtra("tv_ip") ?: return finish()
        val tvPort = intent.getIntExtra("tv_port", 8002)

        tv = SamsungTV(tvName, tvIp, tvPort)
        binding.tvNameTextView.text = tvName

        setupWebSocket()
        setupButtons()
        connectToTV()
    }

    private fun setupWebSocket() {
        webSocketClient = SamsungWebSocketClient(tv)
        webSocketClient.setConnectionListener(object : SamsungWebSocketClient.ConnectionListener {
            override fun onConnected() {
                runOnUiThread {
                    Toast.makeText(this@RemoteControlActivity, "Connecté à ${tv.name}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onDisconnected() {
                runOnUiThread {
                    Toast.makeText(this@RemoteControlActivity, "Déconnecté", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@RemoteControlActivity, "Erreur: $error", Toast.LENGTH_SHORT).show()
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
        val token = prefsManager.getAuthToken()
        if (token != null) {
            webSocketClient.connect(token)
        } else {
            webSocketClient.connect()
        }
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
        if (webSocketClient.isConnected()) {
            webSocketClient.sendKey(key)
        } else {
            Toast.makeText(this, "Non connecté à la TV", Toast.LENGTH_SHORT).show()
            connectToTV()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketClient.disconnect()
    }
}
