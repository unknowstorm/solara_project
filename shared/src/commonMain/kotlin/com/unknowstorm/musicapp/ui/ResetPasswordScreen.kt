package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.auth.AuthRepository
import kotlinx.coroutines.launch

private const val CODE_LENGTH = 8
private const val MIN_PASSWORD = 6

/**
 * Password reset with a code from the email: first the email, then the code + new password.
 * When the password is saved the user automatically ends up signed in,
 * App sees this and closes the sign-in screen, so [onClose] does not need to be called after success.
 */
@Composable
fun ResetPasswordScreen(
    repo: AuthRepository,
    initialEmail: String = "",
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformBackHandler(enabled = true, onBack = onClose)

    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(initialEmail) }
    var codeSent by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun sendCode() {
        if (busy || email.isBlank()) return
        scope.launch {
            busy = true; error = null; info = null
            repo.sendResetCode(email)
                .onSuccess { codeSent = true; info = S.codeSentTo(email.trim()) }
                .onFailure { error = it.message }
            busy = false
        }
    }

    fun confirm() {
        if (busy) return
        if (code.length != CODE_LENGTH) { error = S.enterTheDigitCode(CODE_LENGTH); return }
        if (password.length < MIN_PASSWORD) { error = S.passwordIsTooSimpleAt2(MIN_PASSWORD); return }
        scope.launch {
            busy = true; error = null; info = null
            repo.resetPasswordWithCode(email, code, password)
                .onFailure { error = it.message }
            busy = false
        }
    }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    S.resetPassword,
                    modifier = Modifier.padding(top = 48.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (codeSent) S.enterTheCodeFromThe2
                    else S.enterYourAccountEmailAnd,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    label = { Text(S.email) },
                    singleLine = true,
                    enabled = !busy && !codeSent,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )

                if (codeSent) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(CODE_LENGTH); error = null },
                        label = { Text(S.codeFromTheEmail) },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text(S.newPassword) },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    )
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                info?.let { Text(it, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center) }

                Button(
                    onClick = { if (codeSent) confirm() else sendCode() },
                    enabled = !busy && email.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Text(if (codeSent) S.savePassword else S.sendCode)
                }

                if (codeSent) {
                    TextButton(onClick = { sendCode() }, enabled = !busy) { Text(S.sendTheCodeAgain) }
                }
                TextButton(onClick = onClose, enabled = !busy) { BackLabel(S.back) }
            }
        }
    }
}