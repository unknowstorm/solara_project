package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
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

/** Fullscreen text (terms or policy) with links to the Google and YouTube documents. */
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
                        Text(S.youtubeTermsOfService)
                    }
                    TextButton(onClick = { openLink(LegalTexts.GOOGLE_PRIVACY_URL) }) {
                        Text(S.googlePrivacyPolicy)
                    }
                    TextButton(onClick = { openLink(LegalTexts.GOOGLE_PERMISSIONS_URL) }) {
                        Text(S.appAccessToYourGoogle)
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(S.close) }
            }
        }
    }
}

/** Block for the “Settings” screen: the terms and the policy must always be accessible. */
@Composable
fun LegalLinks(modifier: Modifier = Modifier) {
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(S.legalInformation, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { showTerms = true }) { Text(S.termsOfUse) }
        TextButton(onClick = { showPrivacy = true }) { Text(S.privacyPolicy) }
    }
    if (showTerms) LegalDialog(S.termsOfUse, LegalTexts.TERMS) { showTerms = false }
    if (showPrivacy) LegalDialog(S.privacyPolicy, LegalTexts.PRIVACY) { showPrivacy = false }
}

/** Narrow line with links at the bottom of the YouTube screen. */
@Composable
fun LegalFooter(modifier: Modifier = Modifier) {
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = { showTerms = true }) { Text(S.terms, style = MaterialTheme.typography.labelMedium) }
        TextButton(onClick = { showPrivacy = true }) { Text(S.privacy, style = MaterialTheme.typography.labelMedium) }
    }
    if (showTerms) LegalDialog(S.termsOfUse, LegalTexts.TERMS) { showTerms = false }
    if (showPrivacy) LegalDialog(S.privacyPolicy, LegalTexts.PRIVACY) { showPrivacy = false }
}

/** Consent screen before the YouTube section is opened for the first time. */
@Composable
fun YouTubeConsentScreen(onAccept: () -> Unit, onBack: () -> Unit) {
    PlatformBackHandler(onBack = onBack)
    val openLink = rememberExternalLinkOpener()
    var agreed by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack) { BackLabel(S.back) }
        Text(S.youtubeInSolara, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(LegalTexts.YOUTUBE_INTRO, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { openLink(LegalTexts.YOUTUBE_TERMS_URL) }) { Text(S.youtubeTermsOfService) }
        TextButton(onClick = { openLink(LegalTexts.GOOGLE_PRIVACY_URL) }) { Text(S.googlePrivacyPolicy) }
        TextButton(onClick = { showTerms = true }) { Text(S.solaraTermsOfUse) }
        TextButton(onClick = { showPrivacy = true }) { Text(S.solaraPrivacyPolicy) }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clickable { agreed = !agreed },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = agreed, onCheckedChange = { agreed = it })
            Text(LegalTexts.CONSENT_LINE, modifier = Modifier.weight(1f))
        }
    }
        Spacer(Modifier.height(12.dp))
        // The button is pinned to the bottom of the screen and does not move when the text scrolls
        Button(onClick = onAccept, enabled = agreed, modifier = Modifier.fillMaxWidth()) { Text(S.continueText) }
    }

    if (showTerms) LegalDialog(S.termsOfUse, LegalTexts.TERMS) { showTerms = false }
    if (showPrivacy) LegalDialog(S.privacyPolicy, LegalTexts.PRIVACY) { showPrivacy = false }
}
