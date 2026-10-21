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
                val campsite1 = Campsite(1)
                campsite1.name = "test1"
                val campsite2 = Campsite(2)
                campsite2.name = "test2"
                var CampsiteList = listOf(campsite1, campsite2)
                val view = CampsiteView()
                view.SummaryView(CampsiteList)
            }
        }
    }
}