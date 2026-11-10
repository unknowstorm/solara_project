package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.auth.AuthRepository
import kotlinx.coroutines.launch

private const val MIN_PASSWORD = 6

/** Диалог смены пароля для вошедшего пользователя. Закрывается сам после успешного сохранения. */
@Composable
fun ChangePasswordDialog(repo: AuthRepository, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        if (busy) return
        when {
            password.length < MIN_PASSWORD -> error = S.passwordIsTooSimpleAt2(MIN_PASSWORD)
            password != repeat -> error = S.passwordsDoNotMatch
            else -> scope.launch {
                busy = true; error = null
                repo.changePassword(password)
                    .onSuccess { onDismiss() }
                    .onFailure { error = it.message }
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(S.changePassword2) },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text(S.newPassword) },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = repeat,
                    onValueChange = { repeat = it; error = null },
                    label = { Text(S.repeatPassword) },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }, enabled = !busy) { Text(S.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(S.cancel) }
        },
    )
}