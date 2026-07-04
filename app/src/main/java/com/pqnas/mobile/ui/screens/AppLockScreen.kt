package com.pqnas.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.R
import com.pqnas.mobile.ui.theme.PqnasAppTheme

@Composable
fun AppLockScreen(
    status: String,
    appTheme: PqnasAppTheme,
    appTitle: String = "",
    serverHost: String = "",
    onUnlock: () -> Unit,
    onLogout: () -> Unit
) {
    val displayTitle = appTitle.trim().ifBlank {
        stringResource(R.string.app_lock_title)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        

Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    val logoResId = when (appTheme) {
                        PqnasAppTheme.Bright -> R.drawable.dna_nexus_logo_bright
                        PqnasAppTheme.CpunkOrange -> R.drawable.dna_nexus_logo_orange
                        PqnasAppTheme.WinClassic -> R.drawable.dna_nexus_logo_bright
                        PqnasAppTheme.Dark -> R.drawable.dna_nexus_logo_dark
                    }

                    Image(
                        painter = painterResource(id = logoResId),
                        contentDescription = stringResource(R.string.app_lock_logo_desc),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (serverHost.isNotBlank()) {
                    // Runtime branding is display-only. Keep the real connected
                    // origin visible so a server name cannot hide where data lives.
                    Text(
                        text = stringResource(R.string.connected_server_domain, serverHost),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = stringResource(R.string.app_lock_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onUnlock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.app_lock_unlock))
                }

                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(stringResource(R.string.logout))
                }
            }
        }
    }
}