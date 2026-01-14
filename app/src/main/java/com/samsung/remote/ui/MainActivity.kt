package com.samsung.remote.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.samsung.remote.R
import com.samsung.remote.adapter.TVListAdapter
import com.samsung.remote.databinding.ActivityMainBinding
import com.samsung.remote.model.RemoteKey
import com.samsung.remote.model.SamsungTV
import com.samsung.remote.network.SamsungWebSocketClient
import com.samsung.remote.network.TVDiscoveryService
import com.samsung.remote.util.DebugLogger
import com.samsung.remote.util.PreferencesManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tvAdapter: TVListAdapter
    private lateinit var discoveryService: TVDiscoveryService
    private lateinit var prefsManager: PreferencesManager

    private val discoveredTVs = mutableListOf<SamsungTV>()
    private var discoveryJob: Job? = null

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Debug Logger
        DebugLogger.init(this)
        DebugLogger.i("MainActivity", "=== Application démarrée ===")
        DebugLogger.d("MainActivity", "Version Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        DebugLogger.d("MainActivity", "Modèle appareil: ${Build.MANUFACTURER} ${Build.MODEL}")

        // Log network information
        com.samsung.remote.util.NetworkInfoHelper.logNetworkInfo(this, "MainActivity")

        prefsManager = PreferencesManager(this)
        discoveryService = TVDiscoveryService(this)

        setupRecyclerView()
        setupButtons()

        // Check and request location permissions for WiFi SSID access
        checkLocationPermissions()

        DebugLogger.d("MainActivity", "Configuration terminée")

        // Check if there's a saved TV
        prefsManager.getSavedTV()?.let { savedTV ->
            DebugLogger.i("MainActivity", "TV sauvegardée trouvée: ${savedTV.name} (${savedTV.ip})")
            binding.statusText.text = getString(R.string.tv_found, savedTV.name)
            // Auto-navigate to pairing or remote control
            navigateToPairing(savedTV)
        } ?: run {
            DebugLogger.d("MainActivity", "Aucune TV sauvegardée")
        }
    }

    private fun checkLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

            if (fineLocation != PackageManager.PERMISSION_GRANTED ||
                coarseLocation != PackageManager.PERMISSION_GRANTED) {

                DebugLogger.w("MainActivity", "Permissions de localisation non accordées")

                AlertDialog.Builder(this)
                    .setTitle("Permission requise")
                    .setMessage("L'accès à la localisation est nécessaire pour obtenir le nom du réseau WiFi (SSID) sur Android 8.1+.\n\nCette permission est utilisée uniquement pour identifier votre réseau WiFi.")
                    .setPositiveButton("Autoriser") { _, _ ->
                        requestLocationPermissions()
                    }
                    .setNegativeButton("Plus tard", null)
                    .show()
            } else {
                DebugLogger.i("MainActivity", "✓ Permissions de localisation accordées")
            }
        }
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQUEST_LOCATION_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                DebugLogger.i("MainActivity", "✓ Permissions de localisation accordées par l'utilisateur")
                Toast.makeText(this, "Permissions accordées !", Toast.LENGTH_SHORT).show()
                // Re-log network info with permissions
                com.samsung.remote.util.NetworkInfoHelper.logNetworkInfo(this, "MainActivity")
            } else {
                DebugLogger.w("MainActivity", "❌ Permissions de localisation refusées")
                Toast.makeText(
                    this,
                    "Sans ces permissions, le nom du réseau WiFi ne pourra pas être affiché",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        // Update debug menu item text
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
            "Mode Debug activé - Les logs sont maintenant enregistrés"
        } else {
            "Mode Debug désactivé"
        }

        AlertDialog.Builder(this)
            .setTitle("Mode Debug")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                invalidateOptionsMenu() // Refresh menu
            }
            .show()
    }

    private fun openDebugLogs() {
        if (!DebugLogger.isDebugEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Mode Debug désactivé")
                .setMessage("Voulez-vous activer le mode Debug pour voir les logs en temps réel ?")
                .setPositiveButton("Activer") { _, _ ->
                    DebugLogger.setDebugEnabled(true, this)
                    invalidateOptionsMenu()
                    startActivity(Intent(this, DebugLogsActivity::class.java))
                }
                .setNegativeButton("Annuler", null)
                .show()
        } else {
            startActivity(Intent(this, DebugLogsActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        tvAdapter = TVListAdapter { tv ->
            onTVSelected(tv)
        }

        binding.tvListRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = tvAdapter
        }
    }

    private fun setupButtons() {
        binding.searchButton.setOnClickListener {
            if (discoveryJob?.isActive == true) {
                stopDiscovery()
            } else {
                startDiscovery()
            }
        }

        // Bouton test MUTE
        binding.testMuteButton.setOnClickListener {
            testMuteCommand()
        }

        // Bouton scan WiFi manuel
        binding.scanWifiButton.setOnClickListener {
            checkLocationPermissions()
            com.samsung.remote.util.NetworkInfoHelper.logNetworkInfo(this, "MainActivity")
            Toast.makeText(this, "Scan WiFi effectué - Vérifiez les logs", Toast.LENGTH_SHORT).show()
        }

        // Bouton entrée manuelle IP
        binding.manualIpButton.setOnClickListener {
            showManualIpDialog()
        }

        // Bouton télécommande universelle
        binding.universalRemoteButton.setOnClickListener {
            openUniversalRemote()
        }

        // Bouton aperçu télécommande
        binding.previewRemoteButton.setOnClickListener {
            openRemotePreview()
        }
    }

    private fun testMuteCommand() {
        val tvList = discoveredTVs
        if (tvList.isEmpty()) {
            Toast.makeText(this, "Aucune TV découverte. Lancez d'abord une recherche.", Toast.LENGTH_LONG).show()
            return
        }

        val tv = tvList.first()
        DebugLogger.i("MainActivity", "Test MUTE vers: ${tv.name} (${tv.ip})")

        Toast.makeText(this, "Envoi commande MUTE vers ${tv.name}...", Toast.LENGTH_SHORT).show()

        val testClient = SamsungWebSocketClient(tv, "AndroidRemote-Test", prefsManager)
        testClient.setConnectionListener(object : SamsungWebSocketClient.ConnectionListener {
            override fun onConnected() {
                DebugLogger.i("MainActivity", "✓ Connecté pour test MUTE")
                // Envoi de la commande MUTE
                testClient.sendKey(RemoteKey.KEY_MUTE)
                DebugLogger.i("MainActivity", "→ Commande MUTE envoyée")

                runOnUiThread {
                    Toast.makeText(this@MainActivity, "✓ Commande MUTE envoyée!", Toast.LENGTH_SHORT).show()
                }

                // Déconnexion après 1 seconde
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    testClient.disconnect()
                }, 1000)
            }

            override fun onDisconnected() {
                DebugLogger.d("MainActivity", "Déconnexion du test MUTE")
            }

            override fun onError(error: String) {
                DebugLogger.e("MainActivity", "❌ Erreur test MUTE: $error")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Erreur: $error", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthRequired() {
                DebugLogger.w("MainActivity", "Authentification requise pour test MUTE")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "TV non appairée - Utilisez le pairing d'abord", Toast.LENGTH_LONG).show()
                }
            }

            override fun onAuthSuccess() {
                DebugLogger.i("MainActivity", "Auth OK pour test MUTE")
            }
        })

        testClient.connect(prefsManager.getAuthToken())
    }

    private fun showManualIpDialog() {
        val input = EditText(this)
        input.hint = "192.168.1.100"

        AlertDialog.Builder(this)
            .setTitle("Entrée manuelle de l'IP TV")
            .setMessage("Entrez l'adresse IP de votre TV Samsung:")
            .setView(input)
            .setPositiveButton("Connecter") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotEmpty()) {
                    connectToManualIP(ip)
                } else {
                    Toast.makeText(this, "IP invalide", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun connectToManualIP(ip: String) {
        DebugLogger.i("MainActivity", "Connexion manuelle à l'IP: $ip")

        val manualTV = SamsungTV("TV Manuel ($ip)", ip, 8002)
        prefsManager.saveTV(manualTV.name, manualTV.ip, manualTV.port)

        Toast.makeText(this, "Connexion à $ip...", Toast.LENGTH_SHORT).show()
        navigateToPairing(manualTV)
    }

    private fun openUniversalRemote() {
        // Vérifier s'il y a une TV sauvegardée ou découverte
        val savedTV = prefsManager.getSavedTV()
        val tv = savedTV ?: discoveredTVs.firstOrNull()

        if (tv != null) {
            DebugLogger.i("MainActivity", "Ouverture télécommande universelle pour: ${tv.name}")
            val intent = Intent(this, RemoteControlActivity::class.java).apply {
                putExtra("tv_name", tv.name)
                putExtra("tv_ip", tv.ip)
                putExtra("tv_port", tv.port)
            }
            startActivity(intent)
        } else {
            AlertDialog.Builder(this)
                .setTitle("Aucune TV configurée")
                .setMessage("Voulez-vous entrer manuellement l'IP de votre TV ?")
                .setPositiveButton("Oui") { _, _ ->
                    showManualIpDialog()
                }
                .setNegativeButton("Rechercher une TV") { _, _ ->
                    startDiscovery()
                }
                .setNeutralButton("Annuler", null)
                .show()
        }
    }

    private fun startDiscovery() {
        DebugLogger.i("MainActivity", "=== Démarrage de la découverte des TV Samsung ===")

        // Re-check network before starting discovery
        com.samsung.remote.util.NetworkInfoHelper.logNetworkInfo(this, "MainActivity")

        val networkInfo = com.samsung.remote.util.NetworkInfoHelper.getNetworkInfo(this)
        if (!networkInfo.isConnected) {
            DebugLogger.e("MainActivity", "❌ Échec: Aucune connexion réseau")
            Toast.makeText(this, "Aucune connexion réseau détectée", Toast.LENGTH_LONG).show()
            return
        }

        if (!networkInfo.isWifi) {
            DebugLogger.w("MainActivity", "⚠️ Avertissement: Pas sur Wi-Fi (Type: ${networkInfo.networkType})")
            Toast.makeText(this, "Connectez-vous au Wi-Fi pour découvrir les TV Samsung", Toast.LENGTH_LONG).show()
            return
        }

        DebugLogger.i("MainActivity", "✓ Réseau OK - Wi-Fi connecté (SSID: ${networkInfo.ssid})")

        discoveredTVs.clear()
        tvAdapter.submitList(emptyList())

        binding.progressBar.visibility = View.VISIBLE
        binding.statusText.text = getString(R.string.searching)
        binding.searchButton.text = getString(R.string.disconnect)

        discoveryJob = lifecycleScope.launch {
            try {
                DebugLogger.d("MainActivity", "Lancement du service de découverte NSD")
                discoveryService.discoverTVs().collect { tv ->
                    DebugLogger.i("MainActivity", "TV découverte: ${tv.name} (${tv.ip}:${tv.port})")
                    if (!discoveredTVs.any { it.ip == tv.ip }) {
                        discoveredTVs.add(tv)
                        tvAdapter.submitList(discoveredTVs.toList())

                        if (discoveredTVs.size == 1) {
                            binding.statusText.text = getString(R.string.tv_found, tv.name)
                        } else {
                            binding.statusText.text = "${discoveredTVs.size} TVs trouvées"
                        }
                    } else {
                        DebugLogger.d("MainActivity", "TV déjà dans la liste, ignorée: ${tv.ip}")
                    }
                }
            } catch (e: Exception) {
                DebugLogger.e("MainActivity", "Erreur lors de la découverte", e)
                Toast.makeText(
                    this@MainActivity,
                    "Erreur: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.searchButton.text = getString(R.string.search_tv)

                if (discoveredTVs.isEmpty()) {
                    DebugLogger.w("MainActivity", "Aucune TV trouvée après la recherche")
                    binding.statusText.text = getString(R.string.no_tv_found)
                } else {
                    DebugLogger.i("MainActivity", "Découverte terminée: ${discoveredTVs.size} TV(s) trouvée(s)")
                }
            }
        }
    }

    private fun stopDiscovery() {
        discoveryJob?.cancel()
        binding.progressBar.visibility = View.GONE
        binding.searchButton.text = getString(R.string.search_tv)
    }

    private fun onTVSelected(tv: SamsungTV) {
        prefsManager.saveTV(tv.name, tv.ip, tv.port)
        navigateToPairing(tv)
    }

    private fun navigateToPairing(tv: SamsungTV) {
        val intent = Intent(this, PairingActivity::class.java).apply {
            putExtra("tv_name", tv.name)
            putExtra("tv_ip", tv.ip)
            putExtra("tv_port", tv.port)
        }
        startActivity(intent)
    }

    private fun openRemotePreview() {
        DebugLogger.i("MainActivity", "Ouverture aperçu télécommande (mode preview)")
        val intent = Intent(this, RemoteControlActivity::class.java).apply {
            putExtra("tv_name", "Aperçu Télécommande")
            putExtra("tv_ip", "0.0.0.0")
            putExtra("tv_port", 8002)
            putExtra("preview_mode", true) // Mode preview sans connexion
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDiscovery()
    }
}
