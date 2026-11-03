package com.dreh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dreh.app.ui.DrEhApp
import com.dreh.app.ui.DrEhTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DrEhTheme { DrEhApp() } }
    }

    override fun onStop() {
        // keep level/XP/vitals/tasks/rewards across cold opens
        com.dreh.app.AppStateHolder.save(applicationContext)
        super.onStop()
    }
}
