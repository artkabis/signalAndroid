package com.samsung.remote.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.samsung.remote.R
import com.samsung.remote.databinding.ActivityPairingBinding
import com.samsung.remote.model.SamsungTV
import com.samsung.remote.network.SamsungWebSocketClient
import com.samsung.remote.util.PreferencesManager

class PairingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPairingBinding
    private lateinit var webSocketClient: SamsungWebSocketClient
    private lateinit var prefsManager: PreferencesManager
    private lateinit var tv: SamsungTV

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PreferencesManager(this)

        // Get TV info from intent
        val tvName = intent.getStringExtra("tv_name") ?: "Samsung TV"
        val tvIp = intent.getStringExtra("tv_ip") ?: return finish()
        val tvPort = intent.getIntExtra("tv_port", 8002)

        tv = SamsungTV(tvName, tvIp, tvPort)
        binding.tvNameText.text = tvName

        setupWebSocket()
        setupButtons()

        // Try to connect with saved token
        val savedToken = prefsManager.getAuthToken()
        if (savedToken != null) {
            binding.pairingStatusText.text = "Tentative de connexion avec le token sauvegardé..."
            connectWithToken(savedToken)
        } else {
            binding.pairingStatusText.text = "En attente de l'appairage..."
            initiateConnection()
        }
    }

    private fun setupWebSocket() {
        webSocketClient = SamsungWebSocketClient(tv, "AndroidRemote", prefsManager)
        webSocketClient.setConnectionListener(object : SamsungWebSocketClient.ConnectionListener {
            override fun onConnected() {
                runOnUiThread {
                    binding.pairingProgressBar.visibility = View.GONE
                    binding.pairingStatusText.text = "Connecté! Vérification..."
                }
            }

            override fun onDisconnected() {
                runOnUiThread {
                    binding.pairingProgressBar.visibility = View.GONE
                    binding.pairingStatusText.text = "Déconnecté"
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    binding.pairingProgressBar.visibility = View.GONE
                    binding.pairingStatusText.text = "Erreur: $error"
                    Toast.makeText(this@PairingActivity, error, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthRequired() {
                runOnUiThread {
                    binding.pairingProgressBar.visibility = View.GONE
                    binding.pairingStatusText.text = getString(R.string.enter_pin)
                    binding.pinInputLayout.visibility = View.VISIBLE
                    binding.pairButton.isEnabled = true
                }
            }

            override fun onAuthSuccess() {
                runOnUiThread {
                    binding.pairingProgressBar.visibility = View.GONE
                    binding.pairingStatusText.text = getString(R.string.pairing_success)
                    Toast.makeText(
                        this@PairingActivity,
                        getString(R.string.pairing_success),
                        Toast.LENGTH_SHORT
                    ).show()

                    // Navigate to remote control
                    navigateToRemoteControl()
                }
            }
        })
    }

    private fun setupButtons() {
        binding.pairButton.setOnClickListener {
            val pin = binding.pinEditText.text.toString()
            if (pin.length == 4) {
                binding.pairingProgressBar.visibility = View.VISIBLE
                binding.pairingStatusText.text = "Appairage en cours..."
                // In Samsung protocol, the PIN is usually just for user confirmation
                // The actual auth happens automatically when user accepts on TV
                initiateConnection()
            } else {
                Toast.makeText(this, "Veuillez entrer un code PIN à 4 chiffres", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initiateConnection() {
        binding.pairingProgressBar.visibility = View.VISIBLE
        webSocketClient.connect()
    }

    private fun connectWithToken(token: String) {
        binding.pairingProgressBar.visibility = View.VISIBLE
        webSocketClient.connect(token)
    }

    private fun navigateToRemoteControl() {
        val intent = Intent(this, RemoteControlActivity::class.java).apply {
            putExtra("tv_name", tv.name)
            putExtra("tv_ip", tv.ip)
            putExtra("tv_port", tv.port)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't disconnect here as we want to keep the connection for RemoteControlActivity
    }
}
