package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.auth.AuthRepository
import com.unknowstorm.musicapp.auth.AuthState
import kotlinx.coroutines.launch

@Composable
fun AccountBlock(
    repo: AuthRepository,
    onOpenAuth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repo.state.collectAsState()
    val scope = rememberCoroutineScope()

    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Аккаунт", style = MaterialTheme.typography.titleMedium)
            when (val s = state) {
                AuthState.Loading -> CircularProgressIndicator(Modifier.size(24.dp))
                is AuthState.SignedIn -> {
                    Text(s.username ?: "Пользователь", style = MaterialTheme.typography.headlineSmall)
                    s.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    FilledTonalButton(onClick = { scope.launch { repo.signOut() } }) { Text("Выйти") }
                }
                is AuthState.Error -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onOpenAuth) { Text("Войти") }
                }
                AuthState.Guest -> {
                    Text(
                        "Вы не вошли. Войдите, чтобы синхронизировать избранное.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onOpenAuth) { Text("Войти или зарегистрироваться") }
                }
            }
        }
    }
}