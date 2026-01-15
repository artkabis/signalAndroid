package com.samsung.remote.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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

        DebugLogger.i("PairingActivity", "=== Démarrage de l'appairage ===")

        prefsManager = PreferencesManager(this)

        // Get TV info from intent
        val tvName = intent.getStringExtra("tv_name") ?: "Samsung TV"
        val tvIp = intent.getStringExtra("tv_ip") ?: return finish()
        val tvPort = intent.getIntExtra("tv_port", 8002)

        tv = SamsungTV(tvName, tvIp, tvPort)
        binding.tvNameText.text = tvName

        DebugLogger.d("PairingActivity", "TV cible: $tvName ($tvIp:$tvPort)")

        setupWebSocket()
        setupButtons()

        // Try to connect with saved token
        val savedToken = prefsManager.getAuthToken()
        if (savedToken != null) {
            DebugLogger.i("PairingActivity", "Token sauvegardé trouvé, tentative de connexion automatique")
            binding.pairingStatusText.text = "Tentative de connexion avec le token sauvegardé..."
            connectWithToken(savedToken)
        } else {
            DebugLogger.d("PairingActivity", "Pas de token sauvegardé, appairage manuel requis")
            binding.pairingStatusText.text = "En attente de l'appairage..."
            initiateConnection()
        }
    }

    private fun setupWebSocket() {
        DebugLogger.d("PairingActivity", "Configuration du WebSocket client pour le pairing")
        val deviceName = prefsManager.getDeviceName()
        DebugLogger.i("PairingActivity", "Nom de l'appareil pour le pairing: $deviceName")
        webSocketClient = SamsungWebSocketClient(tv, deviceName, prefsManager)
        webSocketClient.setConnectionListener(object : SamsungWebSocketClient.ConnectionListener {
            override fun onConnected() {
                runOnUiThread {
                    DebugLogger.i("PairingActivity", "✓ Connecté à la TV")
                    binding.pairingProgressBar.visibility = View.GONE
                    binding.pairingStatusText.text = "Connecté! En attente du popup sur la TV..."

                    // Show instructions for enabling network control if first connection
                    if (prefsManager.getAuthToken() == null) {
                        showTVSettingsInstructions()
                    }
                }
            }

            override fun onDisconnected() {
                runOnUiThread {
                    DebugLogger.w("PairingActivity", "⚠ Déconnecté de la TV")
                    binding.pairingProgressBar.visibility = View.GONE
                    binding.pairingStatusText.text = "Déconnecté"
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    DebugLogger.e("PairingActivity", "❌ Erreur d'appairage: $error")
                    binding.pairingProgressBar.visibility = View.GONE
                    binding.pairingStatusText.text = "Erreur: $error"

                    // Check if it's a connection error
                    if (error.contains("Connection refused") || error.contains("connection abort")) {
                        showTVSettingsInstructions()
                    } else {
                        Toast.makeText(this@PairingActivity, error, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onAuthRequired() {
                runOnUiThread {
                    DebugLogger.i("PairingActivity", "⚠ Authentification requise - affichage du champ PIN")
                    binding.pairingProgressBar.visibility = View.GONE
                    binding.pairingStatusText.text = getString(R.string.enter_pin)
                    binding.pinInputLayout.visibility = View.VISIBLE
                    binding.pairButton.isEnabled = true
                    binding.resetIdentityButton.visibility = View.VISIBLE
                }
            }

            override fun onAuthSuccess() {
                runOnUiThread {
                    DebugLogger.i("PairingActivity", "✓✓✓ Appairage réussi!")
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
                DebugLogger.d("PairingActivity", "PIN saisi (${pin.length} digits), démarrage de l'appairage")
                binding.pairingProgressBar.visibility = View.VISIBLE
                binding.pairingStatusText.text = "Appairage en cours..."
                // In Samsung protocol, the PIN is usually just for user confirmation
                // The actual auth happens automatically when user accepts on TV
                initiateConnection()
            } else {
                DebugLogger.w("PairingActivity", "PIN invalide (${pin.length} digits au lieu de 4)")
                Toast.makeText(this, "Veuillez entrer un code PIN à 4 chiffres", Toast.LENGTH_SHORT).show()
            }
        }

        binding.resetIdentityButton.setOnClickListener {
            DebugLogger.i("PairingActivity", "Utilisateur demande un nouveau pairing forcé")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🔄 Forcer nouveau pairing ?")
                .setMessage("""
                    Cette action va :
                    • Générer un nouveau nom d'appareil
                    • Supprimer le token sauvegardé
                    • Forcer la TV à redemander l'autorisation

                    ⚠️ Avant de continuer, allez sur votre TV et supprimez les anciens appareils "AndroidRemote-*" dans :
                    Menu → Réseau → Smart View → Liste des appareils

                    Continuer ?
                """.trimIndent())
                .setPositiveButton("Continuer") { _, _ ->
                    forceNewPairing()
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    private fun initiateConnection() {
        DebugLogger.i("PairingActivity", "Initiation de la connexion (sans token)")
        binding.pairingProgressBar.visibility = View.VISIBLE
        webSocketClient.connect()
    }

    private fun connectWithToken(token: String) {
        DebugLogger.i("PairingActivity", "Connexion avec token sauvegardé")
        binding.pairingProgressBar.visibility = View.VISIBLE
        webSocketClient.connect(token)
    }

    private fun showTVSettingsInstructions() {
        DebugLogger.i("PairingActivity", "Affichage des instructions de configuration TV")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚠️ TV Série J (2015) - Action requise")
            .setMessage("""
                🔴 PROBLÈME DÉTECTÉ :
                Votre TV a mémorisé plusieurs appareils "AndroidRemote-*" (6+) et refuse d'afficher le popup de pairing.

                ✅ SOLUTION OBLIGATOIRE pour UE32J6300AW (Série J 2015) :

                📺 Sur votre téléviseur Samsung :

                Méthode 1 - Réinitialiser Smart Hub (RECOMMANDÉ) :
                • Menu → Système → Réinitialiser Smart Hub
                • PIN : 0000
                • Supprime TOUS les appareils mémorisés

                Méthode 2 - Supprimer manuellement :
                • Menu → Réseau → Paramètres réseau
                • Smart View ou AllShare Settings
                • Liste des appareils
                • Supprimez TOUS les "AndroidRemote-*"

                Méthode 3 - Redémarrage complet :
                • Débranchez la TV 30 secondes
                • Rebranchez et rallumez

                ⚡ Après nettoyage : cliquez sur "🔄 Forcer nouveau pairing" pour générer un nouveau nom !
            """.trimIndent())
            .setPositiveButton("J'ai nettoyé") { _, _ ->
                // Retry connection
                Toast.makeText(this, "Cliquez sur '🔄 Forcer nouveau pairing' en bas", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Plus tard", null)
            .setNeutralButton("Voir les logs") { _, _ ->
                startActivity(Intent(this, DebugLogsActivity::class.java))
            }
            .show()
    }

    private fun forceNewPairing() {
        DebugLogger.i("PairingActivity", "=== Forçage d'un nouveau pairing ===")

        // Déconnecter le WebSocket actuel
        webSocketClient.disconnect()

        // Générer une nouvelle identité
        prefsManager.resetDeviceIdentity()

        // Recréer le WebSocket avec le nouveau nom
        val newDeviceName = prefsManager.getDeviceName()
        DebugLogger.i("PairingActivity", "Nouvelle identité: $newDeviceName")

        // Mise à jour UI
        binding.pairingProgressBar.visibility = View.VISIBLE
        binding.pairingStatusText.text = "Reconnexion avec nouvelle identité..."
        binding.pinInputLayout.visibility = View.GONE
        binding.resetIdentityButton.visibility = View.GONE

        Toast.makeText(
            this,
            "Nouveau nom: $newDeviceName\nReconnexion...",
            Toast.LENGTH_LONG
        ).show()

        // Recréer le client WebSocket et se reconnecter
        setupWebSocket()

        // Attendre 1 seconde avant de se reconnecter
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            initiateConnection()
        }, 1000)
    }

    private fun navigateToRemoteControl() {
        DebugLogger.i("PairingActivity", "Navigation vers RemoteControlActivity")
        val intent = Intent(this, RemoteControlActivity::class.java).apply {
            putExtra("tv_name", tv.name)
            putExtra("tv_ip", tv.ip)
            putExtra("tv_port", tv.port)
        }
        startActivity(intent)
        finish()
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
        DebugLogger.i("PairingActivity", "Ouverture des logs de debug")
        startActivity(Intent(this, DebugLogsActivity::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.d("PairingActivity", "Fermeture de l'activité de pairing")
        // Don't disconnect here as we want to keep the connection for RemoteControlActivity
    }
}
