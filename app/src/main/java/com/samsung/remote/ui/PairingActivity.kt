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
import com.samsung.remote.util.DebugLogger
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

        DebugLogger.i("PairingActivity", "=== Activité d'appairage démarrée ===")

        prefsManager = PreferencesManager(this)

        // Get TV info from intent
        val tvName = intent.getStringExtra("tv_name") ?: "Samsung TV"
        val tvIp = intent.getStringExtra("tv_ip") ?: run {
            DebugLogger.e("PairingActivity", "IP TV manquante dans l'intent!")
            return finish()
        }
        val tvPort = intent.getIntExtra("tv_port", 8002)

        DebugLogger.i("PairingActivity", "Informations TV:")
        DebugLogger.i("PairingActivity", "  • Nom: $tvName")
        DebugLogger.i("PairingActivity", "  • IP: $tvIp")
        DebugLogger.i("PairingActivity", "  • Port: $tvPort")

        tv = SamsungTV(tvName, tvIp, tvPort)
        binding.tvNameText.text = tvName

        setupWebSocket()
        setupButtons()

        // Try to connect with saved token
        val savedToken = prefsManager.getAuthToken()
        if (savedToken != null) {
            DebugLogger.i("PairingActivity", "Token sauvegardé trouvé: ${savedToken.take(20)}...")
            binding.pairingStatusText.text = "Tentative de connexion avec le token sauvegardé..."
            connectWithToken(savedToken)
        } else {
            DebugLogger.i("PairingActivity", "Aucun token sauvegardé, nouvelle connexion")
            binding.pairingStatusText.text = "En attente de l'appairage..."
            initiateConnection()
        }
    }

    private fun setupWebSocket() {
        DebugLogger.d("PairingActivity", "Configuration du WebSocket...")

        // Generate unique device name to force TV to show new PIN
        val uniqueId = (System.currentTimeMillis() % 10000).toString()
        val uniqueDeviceName = "AndroidRemote-$uniqueId"
        DebugLogger.i("PairingActivity", "Nom d'appareil unique: $uniqueDeviceName")

        webSocketClient = SamsungWebSocketClient(tv, uniqueDeviceName)
        webSocketClient.setConnectionListener(object : SamsungWebSocketClient.ConnectionListener {
            override fun onConnected() {
                DebugLogger.i("PairingActivity", "✓ WebSocket connecté!")
                runOnUiThread {
                    binding.pairingProgressBar.visibility = View.GONE
                    binding.pairingStatusText.text = "Connecté! Vérification..."
                }
            }

            override fun onDisconnected() {
                DebugLogger.w("PairingActivity", "✗ WebSocket déconnecté")
                runOnUiThread {
                    binding.pairingProgressBar.visibility = View.GONE
                    binding.pairingStatusText.text = "Déconnecté"
                }
            }

            override fun onError(error: String) {
                DebugLogger.e("PairingActivity", "❌ Erreur WebSocket: $error")
                runOnUiThread {
                    binding.pairingProgressBar.visibility = View.GONE
                    binding.pairingStatusText.text = "Erreur: $error"
                    Toast.makeText(this@PairingActivity, error, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthRequired() {
                DebugLogger.i("PairingActivity", "🔐 Authentification requise - Le PIN devrait s'afficher sur la TV")
                runOnUiThread {
                    binding.pairingProgressBar.visibility = View.GONE
                    binding.pairingStatusText.text = "✓ Connecté !\n\n" +
                            "REGARDEZ VOTRE TV SAMSUNG :\n" +
                            "• Popup \"Autoriser\" en haut/bas de l'écran\n" +
                            "• Ou notification \"Appareil connecté\"\n" +
                            "• Ou code PIN affiché\n\n" +
                            "Appuyez sur 'Test connexion TV' pour vérifier"
                    binding.pinInputLayout.visibility = View.GONE
                    binding.testConnectionButton.isEnabled = true
                    binding.pairButton.isEnabled = false
                }
            }

            override fun onAuthSuccess() {
                DebugLogger.i("PairingActivity", "✅ Authentification réussie!")
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
        DebugLogger.d("PairingActivity", "WebSocket configuré")
    }

    private fun setupButtons() {
        binding.testConnectionButton.setOnClickListener {
            DebugLogger.i("PairingActivity", "→ Test de connexion : envoi d'une commande MUTE à la TV")
            Toast.makeText(this, "Test: envoi commande MUTE à la TV...", Toast.LENGTH_SHORT).show()

            // Send MUTE command to test if TV responds
            webSocketClient.sendKey(com.samsung.remote.model.RemoteKey.MUTE)

            binding.pairingStatusText.text = "Test envoyé ! Si la TV se met en mute, la connexion fonctionne.\nSinon, vérifiez les paramètres TV."
        }

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
        DebugLogger.i("PairingActivity", "→ Initiation de la connexion WebSocket...")
        binding.pairingProgressBar.visibility = View.VISIBLE
        webSocketClient.connect()
    }

    private fun connectWithToken(token: String) {
        DebugLogger.i("PairingActivity", "→ Connexion avec token: ${token.take(20)}...")
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
