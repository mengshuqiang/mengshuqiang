package com.example.usbaudiocontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.usbaudiocontroller.ui.screen.MainScreen
import com.example.usbaudiocontroller.ui.theme.USBAudioControllerTheme
import com.example.usbaudiocontroller.viewmodel.AudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            USBAudioControllerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val audioViewModel: AudioViewModel = viewModel()
                    MainScreen(audioViewModel = audioViewModel)
                }
            }
        }
    }
}