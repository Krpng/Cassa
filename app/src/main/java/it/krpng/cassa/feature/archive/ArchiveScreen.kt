package it.krpng.cassa.feature.archive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.krpng.cassa.feature.common.CassaBackButton

@Composable
fun ArchiveScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        CassaBackButton(onClick = onBack)
        Text("Archivio", style = MaterialTheme.typography.headlineMedium)
    }
}
