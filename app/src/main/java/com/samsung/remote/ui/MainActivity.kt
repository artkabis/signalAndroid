package com.samsung.remote.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.samsung.remote.R
import com.samsung.remote.adapter.TVListAdapter
import com.samsung.remote.databinding.ActivityMainBinding
import com.samsung.remote.model.SamsungTV
import com.samsung.remote.network.TVDiscoveryService
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PreferencesManager(this)
        discoveryService = TVDiscoveryService(this)

        setupRecyclerView()
        setupButtons()

        // Check if there's a saved TV
        prefsManager.getSavedTV()?.let { savedTV ->
            binding.statusText.text = getString(R.string.tv_found, savedTV.name)
            // Auto-navigate to pairing or remote control
            navigateToPairing(savedTV)
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
    }

    private fun startDiscovery() {
        discoveredTVs.clear()
        tvAdapter.submitList(emptyList())

        binding.progressBar.visibility = View.VISIBLE
        binding.statusText.text = getString(R.string.searching)
        binding.searchButton.text = getString(R.string.disconnect)

        discoveryJob = lifecycleScope.launch {
            try {
                discoveryService.discoverTVs().collect { tv ->
                    if (!discoveredTVs.any { it.ip == tv.ip }) {
                        discoveredTVs.add(tv)
                        tvAdapter.submitList(discoveredTVs.toList())

                        if (discoveredTVs.size == 1) {
                            binding.statusText.text = getString(R.string.tv_found, tv.name)
                        } else {
                            binding.statusText.text = "${discoveredTVs.size} TVs trouvées"
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Erreur: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.searchButton.text = getString(R.string.search_tv)

                if (discoveredTVs.isEmpty()) {
                    binding.statusText.text = getString(R.string.no_tv_found)
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

    override fun onDestroy() {
        super.onDestroy()
        stopDiscovery()
    }
}
