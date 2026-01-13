package com.samsung.remote.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import com.samsung.remote.model.SamsungTV
import com.samsung.remote.network.TVDiscoveryService
import com.samsung.remote.util.DebugLogger
import com.samsung.remote.util.PreferencesManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tvAdapter: TVListAdapter
    private lateinit var discoveryService: TVDiscoveryService
    private lateinit var prefsManager: PreferencesManager

    private val discoveredTVs = mutableListOf<SamsungTV>()
    private var discoveryJob: Job? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
        private const val DISCOVERY_TIMEOUT_MS = 30_000L // 30 seconds
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

        DebugLogger.d("MainActivity", "Configuration terminée")

        // Request location permission for WiFi SSID (required on Android 8.1+)
        checkAndRequestLocationPermission()

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

        binding.manualIpButton.setOnClickListener {
            showManualIpDialog()
        }

        binding.scanNetworkButton.setOnClickListener {
            startNetworkScan()
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
                DebugLogger.i("MainActivity", "Timeout configuré: ${DISCOVERY_TIMEOUT_MS / 1000} secondes")

                // Start timeout counter
                val timeoutJob = launch {
                    var elapsedSeconds = 0
                    while (elapsedSeconds < DISCOVERY_TIMEOUT_MS / 1000) {
                        delay(1000)
                        elapsedSeconds++
                        val remaining = (DISCOVERY_TIMEOUT_MS / 1000) - elapsedSeconds
                        binding.statusText.text = "Recherche... (${remaining}s)"

                        if (elapsedSeconds % 5 == 0) {
                            DebugLogger.d("MainActivity", "Recherche en cours... (${elapsedSeconds}s écoulées)")
                        }
                    }
                }

                val result = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
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
                }

                timeoutJob.cancel()

                if (result == null) {
                    DebugLogger.w("MainActivity", "⏱ Timeout de découverte atteint (${DISCOVERY_TIMEOUT_MS / 1000}s)")
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
                    DebugLogger.w("MainActivity", "❌ Aucune TV trouvée après la recherche")
                    DebugLogger.i("MainActivity", "Suggestions:")
                    DebugLogger.i("MainActivity", "  1. Vérifiez que la TV est allumée")
                    DebugLogger.i("MainActivity", "  2. Vérifiez que la TV est sur le même WiFi")
                    DebugLogger.i("MainActivity", "  3. Essayez de redémarrer la TV")
                    DebugLogger.i("MainActivity", "  4. Vérifiez les paramètres réseau de la TV")
                    binding.statusText.text = getString(R.string.no_tv_found)
                } else {
                    DebugLogger.i("MainActivity", "✅ Découverte terminée: ${discoveredTVs.size} TV(s) trouvée(s)")
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

    private fun showManualIpDialog() {
        DebugLogger.i("MainActivity", "Ouverture du dialogue d'entrée manuelle d'IP")

        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.ip_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                       android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("192.168.1.")
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.manual_ip_dialog_title)
            .setMessage(R.string.manual_ip_dialog_message)
            .setView(input)
            .setPositiveButton("Connecter") { _, _ ->
                val ip = input.text.toString().trim()
                if (isValidIp(ip)) {
                    DebugLogger.i("MainActivity", "IP entrée manuellement : $ip")
                    connectToManualIp(ip)
                } else {
                    DebugLogger.w("MainActivity", "IP invalide entrée : $ip")
                    Toast.makeText(this, R.string.invalid_ip, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun isValidIp(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false

        return parts.all { part ->
            part.toIntOrNull()?.let { it in 0..255 } ?: false
        }
    }

    private fun connectToManualIp(ip: String) {
        DebugLogger.i("MainActivity", "=== Connexion manuelle à $ip ===")
        binding.progressBar.visibility = View.VISIBLE
        binding.statusText.text = getString(R.string.checking_ip)

        lifecycleScope.launch {
            try {
                val host = com.samsung.remote.util.NetworkScanner.checkSingleHost(ip)

                if (host != null) {
                    DebugLogger.i("MainActivity", "✓ Hôte $ip accessible")

                    // Use the detected port, or fallback to 8001 if no Samsung port detected
                    val port = host.detectedPort ?: 8001
                    DebugLogger.i("MainActivity", "Port sélectionné: $port")

                    val tv = SamsungTV(
                        name = host.hostname ?: "Samsung TV ($ip)",
                        ip = ip,
                        port = port
                    )

                    discoveredTVs.clear()
                    discoveredTVs.add(tv)
                    tvAdapter.submitList(discoveredTVs.toList())

                    binding.statusText.text = getString(R.string.tv_found, tv.name)

                    // Auto-connect
                    onTVSelected(tv)
                } else {
                    DebugLogger.w("MainActivity", "✗ Impossible de joindre $ip")
                    Toast.makeText(
                        this@MainActivity,
                        "Impossible de joindre $ip. Vérifiez que la TV est allumée.",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.statusText.text = "Échec de connexion à $ip"
                }
            } catch (e: Exception) {
                DebugLogger.e("MainActivity", "Erreur lors de la vérification de $ip", e)
                Toast.makeText(
                    this@MainActivity,
                    "Erreur : ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun startNetworkScan() {
        DebugLogger.i("MainActivity", "=== Démarrage du scan réseau ===")
        binding.progressBar.visibility = View.VISIBLE
        binding.statusText.text = getString(R.string.scanning_network)

        lifecycleScope.launch {
            try {
                val activeHosts = com.samsung.remote.util.NetworkScanner.scanLocalNetwork(this@MainActivity)

                if (activeHosts.isNotEmpty()) {
                    DebugLogger.i("MainActivity", "✓ ${activeHosts.size} hôte(s) actif(s) trouvé(s)")

                    // Add potential Samsung TVs to the list
                    val samsungTVs = activeHosts.filter { it.hasSamsungPort }

                    if (samsungTVs.isNotEmpty()) {
                        DebugLogger.i("MainActivity", "✓ ${samsungTVs.size} TV(s) Samsung potentielle(s) détectée(s)")
                        discoveredTVs.clear()
                        discoveredTVs.addAll(samsungTVs.map { host ->
                            val port = host.detectedPort ?: 8002
                            DebugLogger.i("MainActivity", "TV ${host.ip} configurée avec le port $port")
                            SamsungTV(
                                name = host.hostname ?: "Samsung TV (${host.ip})",
                                ip = host.ip,
                                port = port
                            )
                        })
                        tvAdapter.submitList(discoveredTVs.toList())
                        binding.statusText.text = "${samsungTVs.size} TV(s) trouvée(s)"
                    } else {
                        DebugLogger.w("MainActivity", "Aucune TV Samsung détectée parmi les hôtes actifs")
                        Toast.makeText(
                            this@MainActivity,
                            "Aucune TV Samsung détectée. Consultez les logs pour voir tous les hôtes actifs.",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.statusText.text = getString(R.string.no_tv_found)
                    }
                } else {
                    DebugLogger.w("MainActivity", "Aucun hôte actif trouvé sur le réseau")
                    binding.statusText.text = "Aucun appareil trouvé"
                }
            } catch (e: Exception) {
                DebugLogger.e("MainActivity", "Erreur lors du scan réseau", e)
                Toast.makeText(
                    this@MainActivity,
                    "Erreur : ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun checkAndRequestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED -> {
                    DebugLogger.i("MainActivity", "✓ Permission de localisation accordée")
                    // Re-log network info with permission granted
                    com.samsung.remote.util.NetworkInfoHelper.logNetworkInfo(this, "MainActivity")
                }
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) -> {
                    // Show explanation dialog
                    DebugLogger.d("MainActivity", "Affichage de l'explication de la permission")
                    showLocationPermissionRationale()
                }
                else -> {
                    // Request permission
                    DebugLogger.d("MainActivity", "Demande de permission de localisation")
                    requestLocationPermission()
                }
            }
        } else {
            DebugLogger.d("MainActivity", "Android < M, permission automatique")
        }
    }

    private fun showLocationPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Permission requise")
            .setMessage("L'accès à la localisation est nécessaire pour obtenir le nom du réseau WiFi (SSID). " +
                    "Cette information aide à vérifier que votre téléphone et votre TV sont sur le même réseau.\n\n" +
                    "Aucune donnée de localisation n'est collectée ou partagée.")
            .setPositiveButton("Autoriser") { _, _ ->
                requestLocationPermission()
            }
            .setNegativeButton("Refuser") { _, _ ->
                DebugLogger.w("MainActivity", "Permission de localisation refusée par l'utilisateur")
                Toast.makeText(
                    this,
                    "Sans cette permission, le SSID WiFi ne sera pas disponible dans les logs",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    DebugLogger.i("MainActivity", "✓ Permission de localisation accordée")
                    Toast.makeText(
                        this,
                        "Permission accordée ! Le SSID WiFi sera maintenant visible dans les logs",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Re-log network info with permission granted
                    com.samsung.remote.util.NetworkInfoHelper.logNetworkInfo(this, "MainActivity")
                } else {
                    DebugLogger.w("MainActivity", "❌ Permission de localisation refusée")
                    Toast.makeText(
                        this,
                        "Permission refusée. Le SSID WiFi ne sera pas disponible dans les logs",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDiscovery()
    }
}
