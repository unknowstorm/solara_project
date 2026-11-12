package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.auth.AuthRepository
import com.unknowstorm.musicapp.auth.AuthState

/** Pencil icon (the Material "edit" shape), drawn in code so no icon library is needed. */
private val PencilIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34" +
                    "c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

@Composable
fun AccountBlock(
    repo: AuthRepository,
    onOpenAuth: () -> Unit,
    onEditProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by repo.state.collectAsState()

    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = state) {
                    AuthState.Loading -> CircularProgressIndicator(Modifier.size(24.dp))
                    is AuthState.SignedIn -> {
                        // Avatar on the left; name and email are level with its top edge
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            UserAvatar(
                                name = s.username ?: s.email,
                                size = 92.dp,
                                image = rememberAvatarImage(repo, s.profile?.avatar),
                            )
                            // The end padding keeps the text away from the pencil in the corner
                            Column(Modifier.weight(1f).padding(end = 28.dp)) {
                                Text(
                                    s.username ?: S.user,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                s.email?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    is AuthState.Error -> {
                        Text(S.account, style = MaterialTheme.typography.titleMedium)
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = onOpenAuth) { Text(S.signIn) }
                    }
                    AuthState.Guest -> {
                        Text(S.account, style = MaterialTheme.typography.titleMedium)
                        Text(
                            S.youAreNotSignedIn,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = onOpenAuth) { Text(S.signInOrSignUp) }
                    }
                }
            }
            // A neat pencil in the corner of the block instead of the "edit profile" button
            (state as? AuthState.SignedIn)?.let { s ->
                IconButton(
                    onClick = onEditProfile,
                    enabled = s.profile != null,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(36.dp),
                ) {
                    Icon(
                        PencilIcon,
                        contentDescription = S.editProfile,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}