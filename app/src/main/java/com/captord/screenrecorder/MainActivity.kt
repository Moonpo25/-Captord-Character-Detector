package com.captord.screenrecorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.captord.screenrecorder.ui.navigation.CaptordNavHost
import com.captord.screenrecorder.ui.theme.CaptordTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CaptordTheme {
                CaptordNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
