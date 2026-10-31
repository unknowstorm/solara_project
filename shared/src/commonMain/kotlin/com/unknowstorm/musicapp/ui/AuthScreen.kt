package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.auth.AuthRepository
import com.unknowstorm.musicapp.auth.SignUpResult
import kotlinx.coroutines.launch
import androidx.compose.foundation.background

@Composable
fun AuthScreen(
    repo: AuthRepository,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> Result<*>, onOk: () -> Unit = {}) {
        scope.launch {
            busy = true; error = null; info = null
            block().onSuccess { onOk() }.onFailure { error = it.message }
            busy = false
        }
    }

    val formValid = email.contains('@') && password.length >= 6 && (!register || password == password2)

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 480.dp)
                .wrapContentWidth(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextButton(onClick = onClose) { Text("← Назад") }

            Text(
                if (register) "Создать аккаунт" else "С возвращением",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                "Аккаунт нужен для синхронизации между телефоном и ПК. Музыка работает и без него.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Переключатель режима
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !register,
                    onClick = { register = false; error = null; info = null },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Вход") }
                SegmentedButton(
                    selected = register,
                    onClick = { register = true; error = null; info = null },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Регистрация") }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Почта") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            if (register) {
                OutlinedTextField(
                    value = password2,
                    onValueChange = { password2 = it },
                    label = { Text("Повтори пароль") },
                    singleLine = true,
                    isError = password2.isNotEmpty() && password2 != password,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }

            Button(
                onClick = {
                    if (register) {
                        run({ repo.signUp(email, password) }) { /* результат ниже */ }
                        scope.launch { }
                    } else {
                        run({ repo.signIn(email, password) }, onOk = onClose)
                    }
                },
                enabled = formValid && !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (register) "Зарегистрироваться" else "Войти")
            }

            if (!register) {
                TextButton(
                    onClick = {
                        if (!email.contains('@')) error = "Введи почту, на неё придёт ссылка"
                        else run({ repo.resetPassword(email) }) { info = "Письмо для сброса пароля отправлено" }
                    },
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Забыл пароль?") }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(Modifier.weight(1f))
                Text("или", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.weight(1f))
            }

            OutlinedButton(
                onClick = { run({ repo.signInWithGoogle() }) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Box(
                    Modifier.size(24.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("G", style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.width(12.dp))
                Text("Продолжить через Google")
            }
        }
    }
}