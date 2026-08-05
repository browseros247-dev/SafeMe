package com.safeme.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.safeme.app.ui.screens.permissions.OnboardingNavHost
import com.safeme.app.ui.theme.SafeMeApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeMeApp {
                OnboardingNavHost()
            }
        }
    }
}