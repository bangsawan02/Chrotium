package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.BrowserMainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Request permission for showing notifications in Android 13+ (API 33)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
    }

    // Explicitly enable hardware acceleration for smooth video playback and UI rendering
    window.setFlags(
      android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
      android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    )

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        BrowserMainScreen(modifier = Modifier.fillMaxSize())
      }
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme { Greeting("Android") }
}

