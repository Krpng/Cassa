package it.krpng.cassa.feature.importmenu

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OdsFilePickerButton(
    onDocumentSelected: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(onDocumentSelected)
    }

    Button(
        onClick = { launcher.launch(OdsDocumentSelection.pickerMimeTypes()) },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text("IMPORTA ODS")
    }
}
