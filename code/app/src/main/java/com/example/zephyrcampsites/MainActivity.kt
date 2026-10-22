package com.example.zephyrcampsites

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZephyrCampsitesTheme {
                val campsiteModel = CampsiteModel()
                val campsiteView = CampsiteView()
                val campsiteController = CampsiteController(campsiteModel)
                campsiteModel.addObserver(campsiteView)
                campsiteView.SummaryView(campsiteModel.campsites, { campsiteController.onClick() })
            }
        }
    }
}