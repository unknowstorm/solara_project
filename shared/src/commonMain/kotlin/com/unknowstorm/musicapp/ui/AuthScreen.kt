package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unknowstorm.musicapp.auth.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RESET_CODE_LENGTH = 8
private const val RESET_MIN_PASSWORD = 6
private const val RESET_RESEND_COOLDOWN_SEC = 60

@Composable
fun AuthScreen(
    repo: AuthRepository,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var register by remember { mutableStateOf(false) }
    // При входе сюда можно ввести и почту, и логин (ник); при регистрации нужна именно почта
    var login by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    // Сохраняем между сворачиванием и пересозданием Activity, чтобы шторка не пропадала сама
    var showForgot by rememberSaveable { mutableStateOf(false) }

    fun run(block: suspend () -> Result<*>, onOk: () -> Unit = {}) {
        scope.launch {
            busy = true; error = null; info = null
            block().onSuccess { onOk() }.onFailure { error = it.message }
            busy = false
        }
    }

    val formValid = if (register) {
        login.contains('@') && password.length >= 6 && password == password2
    } else {
        login.isNotBlank() && password.length >= 6
    }

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
                value = login,
                onValueChange = { login = it },
                label = { Text(if (register) "Почта" else "Почта или логин") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (register) KeyboardType.Email else KeyboardType.Text,
                ),
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
                        // После регистрации App сам покажет экран ожидания письма (pending) или откроет профиль
                        run({ repo.signUp(login, password) })
                    } else {
                        run({ repo.signIn(login, password) }, onOk = onClose)
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
                    onClick = { showForgot = true },
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

        if (showForgot) {
            ForgotPasswordSheet(
                repo = repo,
                initialLogin = login,
                onDismiss = { showForgot = false },
            )
        }
    }
}

private enum class ResetStep { Login, Code, Password }

/**
 * Шторка сброса пароля: выезжает снизу.
 * Шаг 1: почта или логин, мы находим почту и отправляем на неё код ([AuthRepository.startPasswordReset]).
 * Шаг 2: 8-значный код. Шаг 3: новый пароль.
 * Только после шага 3 вызывается resetPasswordWithCode: проверка кода создаёт сессию,
 * и пользователь сразу оказывается вошедшим (App закрывает экран входа).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForgotPasswordSheet(
    repo: AuthRepository,
    initialLogin: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Шаг, почта и код переживают сворачивание приложения; пароли намеренно не сохраняем
    var step by rememberSaveable { mutableStateOf(ResetStep.Login) }
    var login by rememberSaveable { mutableStateOf(initialLogin) }
    // Реальная почта аккаунта: нужна для повторной отправки и проверки кода
    var mail by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var cooldown by rememberSaveable { mutableIntStateOf(0) }

    // Шторка закрывается только кнопкой «Отмена»: свайп вниз, тап по затемнению и «Назад» ничего не делают
    var allowClose by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden || allowClose },
    )

    fun close() {
        allowClose = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    fun sendCode() {
        if (busy || login.isBlank()) return
        scope.launch {
            busy = true; error = null; info = null
            repo.startPasswordReset(login)
                .onSuccess {
                    mail = it
                    code = ""; newPassword = ""; repeat = ""
                    step = ResetStep.Code
                    info = "Код отправлен"
                    cooldown = RESET_RESEND_COOLDOWN_SEC
                }
                .onFailure { error = it.message }
            busy = false
        }
    }

    fun resend() {
        if (busy || cooldown > 0 || mail.isBlank()) return
        scope.launch {
            busy = true; error = null; info = null
            repo.sendResetCode(mail)
                .onSuccess {
                    info = "Новый код отправлен"
                    code = ""; newPassword = ""; repeat = ""
                    step = ResetStep.Code
                    cooldown = RESET_RESEND_COOLDOWN_SEC
                }
                .onFailure { error = it.message }
            busy = false
        }
    }

    fun save() {
        if (busy) return
        when {
            newPassword.length < RESET_MIN_PASSWORD ->
                error = "Пароль слишком простой (минимум $RESET_MIN_PASSWORD символов)"
            newPassword != repeat -> error = "Пароли не совпадают"
            else -> scope.launch {
                busy = true; error = null; info = null
                repo.resetPasswordWithCode(mail, code, newPassword)
                    .onFailure {
                        error = it.message
                        // Код не подошёл: возвращаем на ввод кода
                        if (it.message?.contains("код") == true) {
                            step = ResetStep.Code
                            code = ""
                        }
                    }
                busy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { /* закрытие только через «Отмена» */ },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Сброс пароля", style = MaterialTheme.typography.titleLarge)

            when (step) {
                ResetStep.Login -> {
                    Text(
                        "Укажи почту или логин аккаунта, мы отправим на почту $RESET_CODE_LENGTH-значный код.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedTextField(
                        value = login,
                        onValueChange = { login = it; error = null },
                        label = { Text("Почта или логин") },
                        singleLine = true,
                        enabled = !busy,
                        isError = error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                ResetStep.Code -> {
                    Text(
                        "Письмо с кодом отправлено на ${mail.trim()}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { input ->
                            code = input.filter { it.isDigit() }.take(RESET_CODE_LENGTH)
                            error = null
                            // Код введён целиком: переходим к вводу нового пароля
                            if (code.length == RESET_CODE_LENGTH) { step = ResetStep.Password; info = null }
                        },
                        singleLine = true,
                        enabled = !busy,
                        isError = error != null,
                        placeholder = {
                            Text(
                                "0".repeat(RESET_CODE_LENGTH),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 4.sp),
                            )
                        },
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            textAlign = TextAlign.Center,
                            letterSpacing = 4.sp,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                ResetStep.Password -> {
                    Text(
                        "Код принят. Придумай новый пароль.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; error = null },
                        label = { Text("Новый пароль") },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it; error = null },
                        label = { Text("Повтори пароль") },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }

            when (step) {
                ResetStep.Login -> Button(
                    onClick = { sendCode() },
                    enabled = !busy && login.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Отправить код")
                }

                ResetStep.Code -> Button(
                    onClick = { if (code.length == RESET_CODE_LENGTH) step = ResetStep.Password },
                    enabled = !busy && code.length == RESET_CODE_LENGTH,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) { Text("Далее") }

                ResetStep.Password -> Button(
                    onClick = { save() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Сохранить пароль")
                }
            }

            if (step != ResetStep.Login) {
                TextButton(
                    onClick = { resend() },
                    enabled = !busy && cooldown == 0,
                ) {
                    Text(if (cooldown > 0) "Отправить снова через $cooldown с" else "Отправить снова")
                }
            }
            TextButton(onClick = { close() }, enabled = !busy) { Text("Отмена") }
        }
    }
}