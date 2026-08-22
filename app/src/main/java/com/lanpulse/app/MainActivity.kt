package com.lanpulse.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.lanpulse.app.ui.LanPulseRoot
import com.lanpulse.app.ui.ScannerViewModel
import com.lanpulse.app.ui.theme.LanPulseTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ScannerViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshNetwork()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestScanPermissions()
        setContent {
            LanPulseTheme {
                LanPulseRoot(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNetwork()
    }

    private fun requestScanPermissions() {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        permissionLauncher.launch(needed.toTypedArray())
    }
}
