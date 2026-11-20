package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
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
    // Here you can enter either an email or a login (nickname) when signing in; registration needs an email
    var login by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    // Saved across minimizing and Activity recreation so the sheet does not disappear by itself
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
            TextButton(onClick = onClose) { BackLabel(S.back) }

            Text(
                if (register) S.createAccount else S.welcomeBack,
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                S.anAccountIsNeededTo,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Mode switch
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !register,
                    onClick = { register = false; error = null; info = null },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(S.signIn2) }
                SegmentedButton(
                    selected = register,
                    onClick = { register = true; error = null; info = null },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(S.signUp) }
            }

            OutlinedTextField(
                value = login,
                onValueChange = { login = it },
                label = { Text(if (register) S.email else S.emailOrLogin) },
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
                label = { Text(S.password) },
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
                    label = { Text(S.repeatPassword) },
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
                        // After registration App will itself show the waiting-for-email screen (pending) or open the profile
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
                else Text(if (register) S.signUp2 else S.signIn)
            }

            if (!register) {
                TextButton(
                    onClick = { showForgot = true },
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text(S.forgotPassword) }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(Modifier.weight(1f))
                Text(S.or, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(S.continueWithGoogle)
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
 * Password reset sheet: slides up from the bottom.
 * Step 1: email or login, we find the email and send a code to it ([AuthRepository.startPasswordReset]).
 * Step 2: the 8-digit code. Step 3: the new password.
 * Only after step 3 is resetPasswordWithCode called: checking the code creates a session,
 * and the user immediately ends up signed in (App closes the sign-in screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForgotPasswordSheet(
    repo: AuthRepository,
    initialLogin: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // The step, email and code survive minimizing the app; passwords are deliberately not saved
    var step by rememberSaveable { mutableStateOf(ResetStep.Login) }
    var login by rememberSaveable { mutableStateOf(initialLogin) }
    // The account's real email: needed for resending and checking the code
    var mail by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var cooldown by rememberSaveable { mutableIntStateOf(0) }

    // The sheet closes only with the "Cancel" button: swipe down, tap on the scrim and "Back" do nothing
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
                    info = S.codeSent
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
                    info = S.newCodeSent
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
                error = S.passwordIsTooSimpleAt2(RESET_MIN_PASSWORD)
            newPassword != repeat -> error = S.passwordsDoNotMatch
            else -> scope.launch {
                busy = true; error = null; info = null
                repo.resetPasswordWithCode(mail, code, newPassword)
                    .onFailure {
                        error = it.message
                        // The code did not fit: return to code entry
                        if (it.message?.contains(S.code) == true) {
                            step = ResetStep.Code
                            code = ""
                        }
                    }
                busy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { /* closing only via "Cancel" */ },
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
            Text(S.resetPassword, style = MaterialTheme.typography.titleLarge)

            when (step) {
                ResetStep.Login -> {
                    Text(
                        S.enterYourAccountEmailOr(RESET_CODE_LENGTH),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedTextField(
                        value = login,
                        onValueChange = { login = it; error = null },
                        label = { Text(S.emailOrLogin) },
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
                        S.anEmailWithTheCode(mail.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { input ->
                            code = input.filter { it.isDigit() }.take(RESET_CODE_LENGTH)
                            error = null
                            // The code is fully entered: go to entering the new password
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
                        S.codeAcceptedCreateANew,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; error = null },
                        label = { Text(S.newPassword) },
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
                        label = { Text(S.repeatPassword) },
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
                    else Text(S.sendCode)
                }

                ResetStep.Code -> Button(
                    onClick = { if (code.length == RESET_CODE_LENGTH) step = ResetStep.Password },
                    enabled = !busy && code.length == RESET_CODE_LENGTH,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) { Text(S.next) }

                ResetStep.Password -> Button(
                    onClick = { save() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text(S.savePassword)
                }
            }

            if (step != ResetStep.Login) {
                TextButton(
                    onClick = { resend() },
                    enabled = !busy && cooldown == 0,
                ) {
                    Text(if (cooldown > 0) S.resendInS(cooldown) else S.resend)
                }
            }
            TextButton(onClick = { close() }, enabled = !busy) { Text(S.cancel) }
        }
    }
}