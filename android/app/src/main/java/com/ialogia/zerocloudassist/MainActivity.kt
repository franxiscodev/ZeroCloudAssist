package com.ialogia.zerocloudassist

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ialogia.zerocloudassist.ui.ChatScreen
import com.ialogia.zerocloudassist.ui.ZcaTheme

/** Solo el ciclo de vida: la pantalla está en `ui/ChatScreen.kt` y el estado en [Assistant]. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tema único oscuro: iconos claros en las barras del sistema, sin depender del tema del móvil.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { ZcaTheme { ChatScreen() } }
    }

    override fun onStart() {
        super.onStart()
        Assistant.start(this)
    }

    override fun onStop() {
        Assistant.stop()
        super.onStop()
    }
}
