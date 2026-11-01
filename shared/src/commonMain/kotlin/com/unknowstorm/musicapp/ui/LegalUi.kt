package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Полноэкранный текст (условия или политика) со ссылками на документы Google и YouTube. */
@Composable
fun LegalDialog(title: String, body: String, onDismiss: () -> Unit) {
    val openLink = rememberExternalLinkOpener()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)
                ) {
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { openLink(LegalTexts.YOUTUBE_TERMS_URL) }) {
                        Text("Условия использования YouTube")
                    }
                    TextButton(onClick = { openLink(LegalTexts.GOOGLE_PRIVACY_URL) }) {
                        Text("Политика конфиденциальности Google")
                    }
                    TextButton(onClick = { openLink(LegalTexts.GOOGLE_PERMISSIONS_URL) }) {
                        Text("Доступ приложений к аккаунту Google")
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
            }
        }
    }
}

/** Блок для экрана «Настройки»: условия и политика должны быть доступны всегда. */
@Composable
fun LegalLinks(modifier: Modifier = Modifier) {
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    Column(modifier) {
        Text("Правовая информация", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { showTerms = true }) { Text("Условия использования") }
        TextButton(onClick = { showPrivacy = true }) { Text("Политика конфиденциальности") }
    }
    if (showTerms) LegalDialog("Условия использования", LegalTexts.TERMS) { showTerms = false }
    if (showPrivacy) LegalDialog("Политика конфиденциальности", LegalTexts.PRIVACY) { showPrivacy = false }
}

/** Узкая строка со ссылками внизу экрана YouTube. */
@Composable
fun LegalFooter(modifier: Modifier = Modifier) {
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = { showTerms = true }) { Text("Условия", style = MaterialTheme.typography.labelMedium) }
        TextButton(onClick = { showPrivacy = true }) { Text("Конфиденциальность", style = MaterialTheme.typography.labelMedium) }
    }
    if (showTerms) LegalDialog("Условия использования", LegalTexts.TERMS) { showTerms = false }
    if (showPrivacy) LegalDialog("Политика конфиденциальности", LegalTexts.PRIVACY) { showPrivacy = false }
}

/** Экран согласия перед первым открытием раздела YouTube. */
@Composable
fun YouTubeConsentScreen(onAccept: () -> Unit, onBack: () -> Unit) {
    PlatformBackHandler(onBack = onBack)
    val openLink = rememberExternalLinkOpener()
    var agreed by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("YouTube в Solara", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(LegalTexts.YOUTUBE_INTRO, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { openLink(LegalTexts.YOUTUBE_TERMS_URL) }) { Text("Условия использования YouTube") }
        TextButton(onClick = { openLink(LegalTexts.GOOGLE_PRIVACY_URL) }) { Text("Политика конфиденциальности Google") }
        TextButton(onClick = { showTerms = true }) { Text("Условия использования Solara") }
        TextButton(onClick = { showPrivacy = true }) { Text("Политика конфиденциальности Solara") }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clickable { agreed = !agreed },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = agreed, onCheckedChange = { agreed = it })
            Text(LegalTexts.CONSENT_LINE, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAccept, enabled = agreed, modifier = Modifier.fillMaxWidth()) { Text("Продолжить") }
    }

    if (showTerms) LegalDialog("Условия использования", LegalTexts.TERMS) { showTerms = false }
    if (showPrivacy) LegalDialog("Политика конфиденциальности", LegalTexts.PRIVACY) { showPrivacy = false }
}
