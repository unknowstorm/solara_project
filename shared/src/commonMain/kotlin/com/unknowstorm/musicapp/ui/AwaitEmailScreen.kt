package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unknowstorm.musicapp.auth.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Пароль хранится только в памяти, пока открыт этот экран. Сейчас для подтверждения нужен только email. */
class PendingSignUp(val email: String, val password: String)

private const val RESEND_COOLDOWN_SEC = 30
private const val CODE_LENGTH = 8

/**
 * Экран подтверждения почты 8-значным кодом из письма.
 * Когда код принят, Supabase создаёт сессию, меняется состояние входа и App сам убирает этот экран.
 */
@Composable
fun AwaitEmailScreen(
    repo: AuthRepository,
    pending: PendingSignUp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformBackHandler(enabled = true, onBack = onBack)

    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var cooldown by remember { mutableIntStateOf(RESEND_COOLDOWN_SEC) }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    fun submit() {
        if (busy || code.length != CODE_LENGTH) return
        scope.launch {
            busy = true; error = null; info = null
            repo.verifyEmailCode(pending.email, code)
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
                Box(
                    Modifier.padding(top = 40.dp).size(96.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(44.dp), strokeWidth = 4.dp)
                    } else {
                        Text("✉", style = MaterialTheme.typography.displaySmall)
                    }
                }

                Text(
                    "Введи код из письма",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Мы отправили $CODE_LENGTH-значный код на ${pending.email}. " +
                            "Введи его ниже, чтобы подтвердить почту.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(CODE_LENGTH)
                        code = digits
                        error = null
                        // Код введён целиком: проверяем сразу, без нажатия кнопки
                        if (digits.length == CODE_LENGTH) submit()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    isError = error != null,
                    placeholder = {
                        Text(
                            "0".repeat(CODE_LENGTH),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 6.sp),
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        textAlign = TextAlign.Center,
                        letterSpacing = 6.sp,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(16.dp),
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                info?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                }

                Button(
                    onClick = { submit() },
                    enabled = !busy && code.length == CODE_LENGTH,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Text("Подтвердить")
                }

                Text(
                    "Письма нет? Проверь папку «Спам».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            busy = true; error = null; info = null
                            repo.resendConfirmation(pending.email)
                                .onSuccess {
                                    info = "Новый код отправлен"
                                    code = ""
                                    cooldown = RESEND_COOLDOWN_SEC
                                }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    },
                    enabled = !busy && cooldown == 0,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Text(if (cooldown > 0) "Отправить ещё раз через $cooldown с" else "Отправить код ещё раз")
                }

                TextButton(onClick = onBack, enabled = !busy) { Text("← Изменить почту") }
            }
        }
    }
}