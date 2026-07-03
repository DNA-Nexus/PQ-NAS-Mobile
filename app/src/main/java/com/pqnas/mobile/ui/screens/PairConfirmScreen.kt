package com.pqnas.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.R
import com.pqnas.mobile.auth.AuthRepository
import com.pqnas.mobile.auth.PairQrPayload
import com.pqnas.mobile.security.PinnedTls
import kotlinx.coroutines.launch

@Composable
fun PairConfirmScreen(
    payload: PairQrPayload,
    configuredBaseUrl: String,
    authRepository: AuthRepository,
    onPaired: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var deviceName by remember { mutableStateOf(context.getString(R.string.pair_default_device_name)) }
    var status by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun normalizeOriginForCompare(value: String): String {
        return value.trim().trimEnd('/').lowercase()
    }

    val configuredOrigin = normalizeOriginForCompare(configuredBaseUrl)
    val qrOrigin = normalizeOriginForCompare(payload.origin)
    val originMismatch = configuredOrigin.isBlank() || configuredOrigin != qrOrigin
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.pair_title),
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.cpunk_about),
                        contentDescription = stringResource(R.string.pair_mascot_desc),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Text(
                    text = stringResource(R.string.pair_server, payload.origin),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (originMismatch) {
                    Text(
                        text = stringResource(R.string.pair_configured_server_mismatch, configuredBaseUrl),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = stringResource(R.string.pair_app, payload.appName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = if (PinnedTls.usesPublicCaTrust(payload.tlsPinSha256)) {
                        stringResource(R.string.pair_tls_system_ca)
                    } else {
                        stringResource(R.string.pair_tls_identity, payload.tlsPinSha256.take(24))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text(stringResource(R.string.pair_device_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (busy) {
                    CircularProgressIndicator()
                }

                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (statusIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        scope.launch {
                            if (originMismatch) {
                                statusIsError = true
                                status = context.getString(R.string.pair_error_origin_mismatch)
                                return@launch
                            }

                            busy = true
                            statusIsError = false
                            status = context.getString(R.string.pair_status_pairing)
                            try {
                                val ok = authRepository.consumePair(
                                    baseUrl = payload.origin,
                                    pairToken = payload.pairToken,
                                    tlsPinSha256 = payload.tlsPinSha256,
                                    deviceName = deviceName
                                )
                                if (ok) {
                                    onPaired()
                                } else {
                                    statusIsError = true
                                    status = context.getString(R.string.pair_status_failed)
                                }
                            } catch (_: javax.net.ssl.SSLException) {
                                statusIsError = true
                                status = context.getString(R.string.pair_error_tls_identity)
                            } catch (_: Exception) {
                                statusIsError = true
                                status = context.getString(R.string.pair_error_failed_check)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && !originMismatch
                ) {
                    Text(stringResource(R.string.pair_this_device))
                }

                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                ) {
                    Text(stringResource(R.string.pair_back))
                }
            }
        }
    }
}