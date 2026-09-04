package it.krpng.cassa.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onNewOrder: () -> Unit,
    onTodayOrders: () -> Unit,
    onArchive: () -> Unit,
    onMenu: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Cassa", style = MaterialTheme.typography.headlineLarge)
        Button(
            onClick = onNewOrder,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "NUOVO ORDINE", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedButton(onClick = onTodayOrders, modifier = Modifier.fillMaxWidth()) {
            Text("ORDINI DI OGGI")
        }
        OutlinedButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) {
            Text("ARCHIVIO")
        }
        OutlinedButton(onClick = onMenu, modifier = Modifier.fillMaxWidth()) {
            Text("MENU")
        }
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Text("IMPOSTAZIONI")
        }
    }
}

