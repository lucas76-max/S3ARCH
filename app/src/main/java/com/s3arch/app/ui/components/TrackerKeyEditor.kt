package com.s3arch.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.s3arch.app.data.Tracker

/** Champ de cle API (masque) + endpoint Torznab editable + test de connexion. */
@Composable
fun TrackerKeyEditor(
    tracker: Tracker,
    keyValue: String,
    urlValue: String,
    onKeyChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onTest: (() -> Unit)? = null,
    testEnabled: Boolean = true,
) {
    var visible by remember { mutableStateOf(false) }
    var showUrl by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tracker.label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                tracker.docsHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showUrl = !showUrl }) {
                Text(if (showUrl) "Moins" else "URL", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = keyValue,
            onValueChange = onKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tracker.keyHint) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (visible) "Masquer" else "Afficher",
                    )
                }
            },
        )
        AnimatedVisibility(visible = showUrl) {
            Column {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = urlValue,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Endpoint Torznab") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }
        if (onTest != null) {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onTest,
                    enabled = testEnabled && keyValue.isNotBlank(),
                ) {
                    Text("Tester la connexion")
                }
            }
        }
    }
}
