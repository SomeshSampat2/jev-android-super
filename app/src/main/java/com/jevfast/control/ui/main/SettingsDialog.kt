package com.jevfast.control.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jevfast.control.Keys
import com.jevfast.control.theme.Accent
import com.jevfast.control.theme.Border
import com.jevfast.control.theme.SurfaceCard
import com.jevfast.control.theme.TextMuted
import com.jevfast.control.theme.TextPrimary

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var typeSafe by remember { mutableStateOf(Keys.typeSafe(ctx)) }
    var openRouter by remember { mutableStateOf(Keys.openRouter(ctx)) }
    var gemini by remember { mutableStateOf(Keys.gemini(ctx)) }
    var textModel by remember { mutableStateOf(Keys.textModel(ctx)) }
    var jevModel by remember { mutableStateOf(Keys.jevModel(ctx)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = { Text("Settings", color = TextPrimary) },
        text = {
            Column {
                Text("Keys are stored on-device only.", color = TextMuted)
                Spacer(Modifier.height(12.dp))
                KeyField("TypeSafe API key (Jev)", typeSafe) { typeSafe = it }
                Spacer(Modifier.height(10.dp))
                KeyField("Gemini API key (primary text)", gemini) { gemini = it }
                Spacer(Modifier.height(10.dp))
                KeyField("OpenRouter API key (text fallback)", openRouter) { openRouter = it }
                Spacer(Modifier.height(10.dp))
                KeyField("Text model", textModel) { textModel = it }
                Spacer(Modifier.height(10.dp))
                KeyField("Jev model", jevModel) { jevModel = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Keys.save(
                    ctx,
                    typeSafe = typeSafe,
                    openRouter = openRouter,
                    gemini = gemini,
                    jevModel = jevModel,
                    textModel = textModel,
                )
                onDismiss()
            }) { Text("Save", color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
    )
}

@Composable
private fun KeyField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = Border,
            focusedLabelColor = Accent,
            unfocusedLabelColor = TextMuted,
            cursorColor = Accent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
    )
}
