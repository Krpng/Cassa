package it.krpng.cassa.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import it.krpng.cassa.app.navigation.CassaNavHost

@Composable
fun CassaApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            CassaNavHost()
        }
    }
}

