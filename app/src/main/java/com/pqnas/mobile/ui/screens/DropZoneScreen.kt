package com.pqnas.mobile.ui.screens
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import com.pqnas.mobile.api.DropZoneUploadDto
import androidx.compose.material3.OutlinedButton

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.R
import com.pqnas.mobile.api.DropZoneBrandingDto
import com.pqnas.mobile.api.DropZoneInfo
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

private val DzBg = Color(0xFF070A10)
private val DzPanel = Color(0xFF15161D)
private val DzPanelSoft = Color(0xFF1E2028)
private val DzPanelLine = Color(0xFF3B3F4A)
private val DzOrange = Color(0xFFFF9F1A)
private val DzOrangeSoft = Color(0xFFFFC15A)
private val DzText = Color(0xFFF4F4F6)
private val DzMuted = Color(0xFFB5B7C3)
private val DzBad = Color(0xFFFF6B6B)
private val DzGood = Color(0xFF7DE38B)

private data class DzExpiryOption(
    val labelRes: Int,
    val seconds: Long,
    val descriptionRes: Int
)

private val DzExpiryOptions = listOf(
    DzExpiryOption(R.string.drop_zone_expiry_1_day, 1L * 24L * 60L * 60L, R.string.drop_zone_expiry_1_day_desc),
    DzExpiryOption(R.string.drop_zone_expiry_7_days, 7L * 24L * 60L * 60L, R.string.drop_zone_expiry_7_days_desc),
    DzExpiryOption(R.string.drop_zone_expiry_30_days, 30L * 24L * 60L * 60L, R.string.drop_zone_expiry_30_days_desc),
    DzExpiryOption(R.string.drop_zone_expiry_90_days, 90L * 24L * 60L * 60L, R.string.drop_zone_expiry_90_days_desc)
)

private data class DzEditDraft(
    val id: String,
    val name: String,
    val maxFileBytesText: String,
    val maxTotalBytesText: String,
    val duplicatePolicy: String,
    val brandingCompanyName: String,
    val brandingKicker: String,
    val brandingTitle: String,
    val brandingDescription: String,
    val brandingButtonText: String,
    val brandingFooterText: String,
    val brandingLogoUrl: String,
    val brandingPrimaryColor: String,
    val brandingBackgroundColor: String,
    val brandingPanelColor: String,
    val brandingTextColor: String,
    val brandingButtonTextColor: String
)

@Composable
fun DropZoneScreen(
    zones: List<DropZoneInfo>,
    loading: Boolean,
    creating: Boolean,
    status: String,
    latestUrl: String,
    name: String,
    destination: String,
    password: String,
    expiresInSeconds: Long,
    maxFileBytesText: String,
    maxTotalBytesText: String,
    duplicatePolicy: String,
    brandingCompanyName: String,
    brandingKicker: String,
    brandingTitle: String,
    brandingDescription: String,
    brandingButtonText: String,
    brandingFooterText: String,
    brandingLogoUrl: String,
    brandingPrimaryColor: String,
    brandingBackgroundColor: String,
    brandingPanelColor: String,
    brandingTextColor: String,
    brandingButtonTextColor: String,
    onNameChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onExpiresInSecondsChange: (Long) -> Unit,
    onMaxFileBytesTextChange: (String) -> Unit,
    onMaxTotalBytesTextChange: (String) -> Unit,
    onDuplicatePolicyChange: (String) -> Unit,
    onBrandingCompanyNameChange: (String) -> Unit,
    onBrandingKickerChange: (String) -> Unit,
    onBrandingTitleChange: (String) -> Unit,
    onBrandingDescriptionChange: (String) -> Unit,
    onBrandingButtonTextChange: (String) -> Unit,
    onBrandingFooterTextChange: (String) -> Unit,
    onBrandingLogoUrlChange: (String) -> Unit,
    onBrandingPrimaryColorChange: (String) -> Unit,
    onBrandingBackgroundColorChange: (String) -> Unit,
    onBrandingPanelColorChange: (String) -> Unit,
    onBrandingTextColorChange: (String) -> Unit,
    onBrandingButtonTextColorChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onCopyLatest: () -> Unit,
    onUpdate: (
        id: String,
        name: String,
        maxFileBytesText: String,
        maxTotalBytesText: String,
        duplicatePolicy: String,
        branding: DropZoneBrandingDto
    ) -> Unit,
    onDisable: (String) -> Unit,
    onRenew: (String, Long) -> Unit,
    onClearHistory: (String) -> Unit,
    historyOpen: Boolean,
    historyTitle: String,
    historyUploads: List<DropZoneUploadDto>,
    historyLoading: Boolean,
    historyStatus: String,
    onViewHistory: (DropZoneInfo) -> Unit,
    onCloseHistory: () -> Unit,
    onClose: () -> Unit
) {
    var disableCandidate by remember { mutableStateOf<DropZoneInfo?>(null) }
    var renewCandidate by remember { mutableStateOf<DropZoneInfo?>(null) }
    var clearHistoryCandidate by remember { mutableStateOf<DropZoneInfo?>(null) }
    var showCreateForm by remember { mutableStateOf(false) }
    var editDraft by remember { mutableStateOf<DzEditDraft?>(null) }
    val dropZoneNameFallback = stringResource(R.string.drop_zone)
    val dropZoneHistoryFallback = stringResource(R.string.drop_zone_history_fallback)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DzBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DzBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            DropZoneHeader(onClose = onClose)

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    if (showCreateForm) {
                        DropZoneCreateCard(
                        name = name,
                        destination = destination,
                        password = password,
                        expiresInSeconds = expiresInSeconds,
                        maxFileBytesText = maxFileBytesText,
                        maxTotalBytesText = maxTotalBytesText,
                        duplicatePolicy = duplicatePolicy,
                        brandingCompanyName = brandingCompanyName,
                        brandingKicker = brandingKicker,
                        brandingTitle = brandingTitle,
                        brandingDescription = brandingDescription,
                        brandingButtonText = brandingButtonText,
                        brandingFooterText = brandingFooterText,
                        brandingLogoUrl = brandingLogoUrl,
                        brandingPrimaryColor = brandingPrimaryColor,
                        brandingBackgroundColor = brandingBackgroundColor,
                        brandingPanelColor = brandingPanelColor,
                        brandingTextColor = brandingTextColor,
                        brandingButtonTextColor = brandingButtonTextColor,
                        creating = creating,
                        onNameChange = onNameChange,
                        onDestinationChange = onDestinationChange,
                        onPasswordChange = onPasswordChange,
                        onExpiresInSecondsChange = onExpiresInSecondsChange,
                        onMaxFileBytesTextChange = onMaxFileBytesTextChange,
                        onMaxTotalBytesTextChange = onMaxTotalBytesTextChange,
                        onDuplicatePolicyChange = onDuplicatePolicyChange,
                        onBrandingCompanyNameChange = onBrandingCompanyNameChange,
                        onBrandingKickerChange = onBrandingKickerChange,
                        onBrandingTitleChange = onBrandingTitleChange,
                        onBrandingDescriptionChange = onBrandingDescriptionChange,
                        onBrandingButtonTextChange = onBrandingButtonTextChange,
                        onBrandingFooterTextChange = onBrandingFooterTextChange,
                        onBrandingLogoUrlChange = onBrandingLogoUrlChange,
                        onBrandingPrimaryColorChange = onBrandingPrimaryColorChange,
                        onBrandingBackgroundColorChange = onBrandingBackgroundColorChange,
                        onBrandingPanelColorChange = onBrandingPanelColorChange,
                        onBrandingTextColorChange = onBrandingTextColorChange,
                        onBrandingButtonTextColorChange = onBrandingButtonTextColorChange,
                        onCreate = {
                            showCreateForm = false
                            onCreate()
                        }
                        )
                    } else {
                        DropZoneCollapsedCreateCard(
                            onOpen = { showCreateForm = true }
                        )
                    }
                }

                if (latestUrl.isNotBlank()) {
                    item {
                        DropZoneLatestLinkCard(
                            latestUrl = latestUrl,
                            onCopyLatest = onCopyLatest
                        )
                    }
                }

                if (status.isNotBlank()) {
                    item {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                status.contains("created", ignoreCase = true) ||
                                        status.contains("copied", ignoreCase = true) ||
                                        status.contains("disabled", ignoreCase = true) ->
                                    DzGood
                                status.contains("failed", ignoreCase = true) ||
                                        status.contains("could not", ignoreCase = true) ||
                                        status.contains("denied", ignoreCase = true) ->
                                    DzBad
                                else -> DzMuted
                            }
                        )
                    }
                }

                item {
                    DropZoneExistingHeader(
                        loading = loading,
                        onRefresh = onRefresh
                    )
                }

                if (loading) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = DzOrange,
                            trackColor = DzPanelSoft
                        )
                    }
                }

                items(
                    items = zones,
                    key = { it.id }
                ) { zone ->
                    DropZoneExistingCard(
                        zone = zone,
                        onEdit = {
                            editDraft = dzEditDraftFrom(zone)
                        },
                        onDisable = {
                            disableCandidate = zone
                        },
                        onRenew = {
                            renewCandidate = zone
                        },
                        onClearHistory = {
                            clearHistoryCandidate = zone
                        },
                        onHistory = {
                            onViewHistory(zone)
                        }
                    )
                }

                if (!loading && zones.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = DzPanel.copy(alpha = 0.72f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                DzPanelLine.copy(alpha = 0.75f)
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.drop_zone_empty_owner),
                                modifier = Modifier.padding(18.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = DzMuted
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(96.dp))
                }
            }
        }
    }

    renewCandidate?.let { zone ->
        DropZoneRenewDialog(
            zoneName = zone.name.ifBlank { dropZoneNameFallback },
            onDismiss = { renewCandidate = null },
            onRenew = { seconds ->
                renewCandidate = null
                onRenew(zone.id, seconds)
            }
        )
    }

    clearHistoryCandidate?.let { zone ->
        AlertDialog(
            onDismissRequest = { clearHistoryCandidate = null },
            containerColor = DzPanel,
            titleContentColor = DzText,
            textContentColor = DzMuted,
            title = { Text(stringResource(R.string.drop_zone_clear_history_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(zone.name.ifBlank { dropZoneNameFallback })
                    Text(stringResource(R.string.drop_zone_clear_history_desc))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearHistoryCandidate = null
                        onClearHistory(zone.id)
                    }
                ) {
                    Text(stringResource(R.string.drop_zone_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearHistoryCandidate = null }) {
                    Text(stringResource(R.string.drop_zone_cancel))
                }
            }
        )
    }

    if (historyOpen) {
        DropZoneHistoryDialog(
            title = historyTitle.ifBlank { dropZoneHistoryFallback },
            uploads = historyUploads,
            loading = historyLoading,
            status = historyStatus,
            onDismiss = onCloseHistory
        )
    }

    disableCandidate?.let { zone ->
        AlertDialog(
            onDismissRequest = { disableCandidate = null },
            containerColor = DzPanel,
            titleContentColor = DzText,
            textContentColor = DzMuted,
            title = { Text(stringResource(R.string.drop_zone_disable_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(zone.name.ifBlank { dropZoneNameFallback })
                    Text(stringResource(R.string.drop_zone_disable_desc))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        disableCandidate = null
                        onDisable(zone.id)
                    }
                ) {
                    Text(stringResource(R.string.drop_zone_disable), color = DzBad)
                }
            },
            dismissButton = {
                TextButton(onClick = { disableCandidate = null }) {
                    Text(stringResource(R.string.drop_zone_cancel), color = DzMuted)
                }
            }
        )
    }

    editDraft?.let { draft ->
        DropZoneEditDialog(
            draft = draft,
            onDraftChange = { editDraft = it },
            onDismiss = { editDraft = null },
            onSave = { saved ->
                editDraft = null
                onUpdate(
                    saved.id,
                    saved.name,
                    saved.maxFileBytesText,
                    saved.maxTotalBytesText,
                    saved.duplicatePolicy,
                    DropZoneBrandingDto(
                        company_name = saved.brandingCompanyName,
                        kicker = saved.brandingKicker,
                        title = saved.brandingTitle,
                        description = saved.brandingDescription,
                        button_text = saved.brandingButtonText,
                        footer_text = saved.brandingFooterText,
                        logo_url = saved.brandingLogoUrl,
                        primary_color = saved.brandingPrimaryColor,
                        background_color = saved.brandingBackgroundColor,
                        panel_color = saved.brandingPanelColor,
                        text_color = saved.brandingTextColor,
                        button_text_color = saved.brandingButtonTextColor
                    )
                )
            }
        )
    }
}

@Composable
private fun DropZoneHeader(
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.drop_zone_back),
                tint = DzText
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.drop_zone_header_kicker),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = DzOrange
            )

            Text(
                text = stringResource(R.string.drop_zone),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = DzText
            )

            Text(
                text = stringResource(R.string.drop_zone_header_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )
        }
    }
}

@Composable
private fun DropZoneRenewDialog(
    zoneName: String,
    onDismiss: () -> Unit,
    onRenew: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DzPanel,
        titleContentColor = DzText,
        textContentColor = DzMuted,
        title = { Text(stringResource(R.string.drop_zone_renew_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(zoneName)
                Text(stringResource(R.string.drop_zone_renew_desc))
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onRenew(7L * 24L * 60L * 60L) }) {
                    Text(stringResource(R.string.drop_zone_renew_7_days))
                }
                TextButton(onClick = { onRenew(30L * 24L * 60L * 60L) }) {
                    Text(stringResource(R.string.drop_zone_renew_30_days))
                }
                TextButton(onClick = { onRenew(90L * 24L * 60L * 60L) }) {
                    Text(stringResource(R.string.drop_zone_renew_90_days))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.drop_zone_cancel))
            }
        }
    )
}

@Composable
private fun DropZoneCollapsedCreateCard(
    onOpen: () -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DzPanel.copy(alpha = 0.82f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            DzPanelLine.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.drop_zone_create_branded_link),
                style = MaterialTheme.typography.titleMedium,
                color = DzText
            )

            Text(
                text = stringResource(R.string.drop_zone_create_collapsed_desc),
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )

            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.drop_zone_create_branded_link))
            }
        }
    }
}

@Composable
private fun DropZoneCreateCard(
    name: String,
    destination: String,
    password: String,
    expiresInSeconds: Long,
    maxFileBytesText: String,
    maxTotalBytesText: String,
    duplicatePolicy: String,
    brandingCompanyName: String,
    brandingKicker: String,
    brandingTitle: String,
    brandingDescription: String,
    brandingButtonText: String,
    brandingFooterText: String,
    brandingLogoUrl: String,
    brandingPrimaryColor: String,
    brandingBackgroundColor: String,
    brandingPanelColor: String,
    brandingTextColor: String,
    brandingButtonTextColor: String,
    creating: Boolean,
    onNameChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onExpiresInSecondsChange: (Long) -> Unit,
    onMaxFileBytesTextChange: (String) -> Unit,
    onMaxTotalBytesTextChange: (String) -> Unit,
    onDuplicatePolicyChange: (String) -> Unit,
    onBrandingCompanyNameChange: (String) -> Unit,
    onBrandingKickerChange: (String) -> Unit,
    onBrandingTitleChange: (String) -> Unit,
    onBrandingDescriptionChange: (String) -> Unit,
    onBrandingButtonTextChange: (String) -> Unit,
    onBrandingFooterTextChange: (String) -> Unit,
    onBrandingLogoUrlChange: (String) -> Unit,
    onBrandingPrimaryColorChange: (String) -> Unit,
    onBrandingBackgroundColorChange: (String) -> Unit,
    onBrandingPanelColorChange: (String) -> Unit,
    onBrandingTextColorChange: (String) -> Unit,
    onBrandingButtonTextColorChange: (String) -> Unit,
    onCreate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DzPanel
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, DzPanelLine)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.drop_zone_create_branded_link),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DzText
            )

            Text(
                text = stringResource(R.string.drop_zone_create_security_note),
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )

            DzSectionTitle(stringResource(R.string.drop_zone_section_basics))

            DzTextField(
                value = name,
                onValueChange = onNameChange,
                label = stringResource(R.string.drop_zone_name_label),
                placeholder = stringResource(R.string.drop_zone_name_placeholder)
            )

            DzTextField(
                value = destination,
                onValueChange = onDestinationChange,
                label = stringResource(R.string.drop_zone_destination_label),
                placeholder = stringResource(R.string.drop_zone_destination_placeholder)
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.drop_zone_password_label)) },
                placeholder = { Text(stringResource(R.string.drop_zone_password_placeholder)) },
                visualTransformation = PasswordVisualTransformation(),
                colors = dzTextFieldColors()
            )

            DzSectionTitle(stringResource(R.string.drop_zone_section_validity))

            DzExpiryOptions.forEach { option ->
                DzSelectionRow(
                    title = stringResource(option.labelRes),
                    subtitle = stringResource(option.descriptionRes),
                    selected = expiresInSeconds == option.seconds,
                    onClick = { onExpiresInSecondsChange(option.seconds) }
                )
            }

            DzSectionTitle(stringResource(R.string.drop_zone_section_limits))

            DzTextField(
                value = maxFileBytesText,
                onValueChange = onMaxFileBytesTextChange,
                label = stringResource(R.string.drop_zone_max_file_label),
                placeholder = stringResource(R.string.drop_zone_max_file_placeholder)
            )

            DzTextField(
                value = maxTotalBytesText,
                onValueChange = onMaxTotalBytesTextChange,
                label = stringResource(R.string.drop_zone_max_total_label),
                placeholder = stringResource(R.string.drop_zone_max_total_placeholder)
            )

            DzSectionTitle(stringResource(R.string.drop_zone_section_duplicates))

            DzSelectionRow(
                title = stringResource(R.string.drop_zone_duplicate_version_title),
                subtitle = stringResource(R.string.drop_zone_duplicate_version_desc),
                selected = duplicatePolicy == "version",
                onClick = { onDuplicatePolicyChange("version") }
            )

            DzSelectionRow(
                title = stringResource(R.string.drop_zone_duplicate_keep_both_title),
                subtitle = stringResource(R.string.drop_zone_duplicate_keep_both_desc),
                selected = duplicatePolicy == "keep_both",
                onClick = { onDuplicatePolicyChange("keep_both") }
            )

            DzSelectionRow(
                title = stringResource(R.string.drop_zone_duplicate_reject_title),
                subtitle = stringResource(R.string.drop_zone_duplicate_reject_desc),
                selected = duplicatePolicy == "reject",
                onClick = { onDuplicatePolicyChange("reject") }
            )

            DzSectionTitle(stringResource(R.string.drop_zone_section_branding))

            DzTextField(
                value = brandingCompanyName,
                onValueChange = onBrandingCompanyNameChange,
                label = stringResource(R.string.drop_zone_company_brand_label),
                placeholder = "Pohjola Cloud Oy"
            )

            DzTextField(
                value = brandingKicker,
                onValueChange = onBrandingKickerChange,
                label = stringResource(R.string.drop_zone_kicker_label),
                placeholder = stringResource(R.string.drop_zone_kicker_placeholder)
            )

            DzTextField(
                value = brandingTitle,
                onValueChange = onBrandingTitleChange,
                label = stringResource(R.string.drop_zone_public_title_label),
                placeholder = stringResource(R.string.drop_zone_public_title_placeholder)
            )

            OutlinedTextField(
                value = brandingDescription,
                onValueChange = onBrandingDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                label = { Text(stringResource(R.string.drop_zone_public_desc_label)) },
                placeholder = { Text(stringResource(R.string.drop_zone_public_desc_placeholder)) },
                colors = dzTextFieldColors()
            )

            DzTextField(
                value = brandingButtonText,
                onValueChange = onBrandingButtonTextChange,
                label = stringResource(R.string.drop_zone_button_text_label),
                placeholder = stringResource(R.string.drop_zone_button_text_placeholder)
            )

            DzTextField(
                value = brandingFooterText,
                onValueChange = onBrandingFooterTextChange,
                label = stringResource(R.string.drop_zone_footer_text_label),
                placeholder = stringResource(R.string.drop_zone_footer_text_placeholder)
            )

            DzTextField(
                value = brandingLogoUrl,
                onValueChange = onBrandingLogoUrlChange,
                label = stringResource(R.string.drop_zone_logo_url_label),
                placeholder = "https://example.com/logo.png"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DzTextField(
                    value = brandingPrimaryColor,
                    onValueChange = onBrandingPrimaryColorChange,
                    label = stringResource(R.string.drop_zone_primary_color_label),
                    placeholder = "#ff9f1a",
                    modifier = Modifier.weight(1f)
                )

                DzTextField(
                    value = brandingButtonTextColor,
                    onValueChange = onBrandingButtonTextColorChange,
                    label = stringResource(R.string.drop_zone_button_text_color_label),
                    placeholder = "#000000",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DzTextField(
                    value = brandingBackgroundColor,
                    onValueChange = onBrandingBackgroundColorChange,
                    label = stringResource(R.string.drop_zone_background_color_label),
                    placeholder = "#070a10",
                    modifier = Modifier.weight(1f)
                )

                DzTextField(
                    value = brandingPanelColor,
                    onValueChange = onBrandingPanelColorChange,
                    label = stringResource(R.string.drop_zone_panel_color_label),
                    placeholder = "#15161d",
                    modifier = Modifier.weight(1f)
                )
            }

            DzTextField(
                value = brandingTextColor,
                onValueChange = onBrandingTextColorChange,
                label = stringResource(R.string.drop_zone_text_color_label),
                placeholder = "#f4f4f6"
            )

            Button(
                onClick = onCreate,
                enabled = !creating,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DzOrange,
                    contentColor = Color.Black,
                    disabledContainerColor = DzPanelSoft,
                    disabledContentColor = DzMuted
                )
            ) {
                Text(if (creating) stringResource(R.string.drop_zone_creating) else stringResource(R.string.drop_zone_create_button))
            }
        }
    }
}

@Composable
private fun DropZoneLatestLinkCard(
    latestUrl: String,
    onCopyLatest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DzPanelSoft
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, DzOrange.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.drop_zone_latest_link_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = DzOrangeSoft
            )

            Text(
                text = latestUrl,
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )

            Button(
                onClick = onCopyLatest,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DzPanel,
                    contentColor = DzText
                )
            ) {
                Text(stringResource(R.string.drop_zone_copy_link))
            }
        }
    }
}

@Composable
private fun DropZoneExistingHeader(
    loading: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DzPanel.copy(alpha = 0.55f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, DzPanelLine.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.drop_zone_existing_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DzText
                )

                TextButton(
                    onClick = onRefresh,
                    enabled = !loading,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = DzOrangeSoft,
                        disabledContentColor = DzMuted
                    )
                ) {
                    Text(if (loading) stringResource(R.string.drop_zone_loading) else stringResource(R.string.drop_zone_refresh))
                }
            }

            Text(
                text = stringResource(R.string.drop_zone_public_urls_note),
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )
        }
    }
}



@Composable
private fun DropZoneActionButton(
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .defaultMinSize(minWidth = 0.dp, minHeight = 32.dp)
            .height(32.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = DzOrangeSoft
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun DropZoneExistingCard(
    zone: DropZoneInfo,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onDisable: () -> Unit,
    onRenew: () -> Unit,
    onClearHistory: () -> Unit) {
    val zoneExpired = dropZoneIsExpired(zone)
    val zoneStatusText = when {
        zone.disabled -> stringResource(R.string.drop_zone_status_disabled)
        zoneExpired -> stringResource(R.string.drop_zone_status_expired)
        else -> stringResource(R.string.drop_zone_status_active)
    }
    val zoneStatusColor = when {
        zone.disabled -> DzMuted
        zoneExpired -> DzBad
        else -> DzGood
    }
    val dropZoneNameFallback = stringResource(R.string.drop_zone)
    val noDestinationText = stringResource(R.string.drop_zone_no_destination)
    val uploadCountText = stringResource(R.string.drop_zone_uploads_count, zone.upload_count)
    val passwordRequiredText = stringResource(R.string.drop_zone_password_required)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DzPanel
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, DzPanelLine)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = zone.name.ifBlank { dropZoneNameFallback },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = DzText
                    )

                    Text(
                        text = zone.destination_path.ifBlank { noDestinationText },
                        style = MaterialTheme.typography.bodySmall,
                        color = DzMuted
                    )
                }

                Text(
                    text = zoneStatusText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = zoneStatusColor
                )
            }

            Text(
                text = buildString {
                    append(uploadCountText)
                    if (zone.bytes_uploaded > 0L) {
                        append(" • ")
                        append(formatDzBytes(zone.bytes_uploaded))
                    }
                    if (zone.password_required) {
                        append(" • ")
                        append(passwordRequiredText)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )

            DropZoneExistingDetails(zone)

            HorizontalDivider(color = DzPanelLine.copy(alpha = 0.8f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DropZoneActionButton(
                        text = stringResource(R.string.drop_zone_edit),
                        onClick = onEdit
                    )

                    DropZoneActionButton(
                        text = stringResource(R.string.drop_zone_new_uploads),
                        onClick = onHistory
                    )

                    DropZoneActionButton(
                        text = stringResource(R.string.drop_zone_renew),
                        onClick = onRenew
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DropZoneActionButton(
                        text = stringResource(R.string.drop_zone_clear_new),
                        onClick = onClearHistory
                    )

                    if (!zone.disabled) {
                        DropZoneActionButton(
                            text = stringResource(R.string.drop_zone_disable),
                            onClick = onDisable
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DropZoneHistoryDialog(
    title: String,
    uploads: List<DropZoneUploadDto>,
    loading: Boolean,
    status: String,
    onDismiss: () -> Unit
) {
    val uploadedFileFallback = stringResource(R.string.drop_zone_uploaded_file)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DzPanel,
        titleContentColor = DzText,
        textContentColor = DzMuted,
        title = { Text(stringResource(R.string.drop_zone_history_title, title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = DzOrange,
                        trackColor = DzPanelSoft
                    )
                }

                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = DzMuted
                    )
                }

                if (!loading && uploads.isEmpty()) {
                    Text(
                        text = stringResource(R.string.drop_zone_no_new_uploads),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DzMuted
                    )
                }

                uploads.forEach { upload ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = DzPanelSoft
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            DzPanelLine.copy(alpha = 0.8f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = upload.stored_filename
                                    .ifBlank { upload.original_filename }
                                    .ifBlank { uploadedFileFallback },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = DzText
                            )

                            Text(
                                text = buildString {
                                    append(formatDzBytes(upload.size_bytes))
                                    if (upload.created_epoch > 0L) {
                                        append(" • ")
                                        append(formatDzEpoch(upload.created_epoch))
                                    }
                                    if (upload.uploader_name.isNotBlank()) {
                                        append(" • ")
                                        append(upload.uploader_name)
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = DzMuted
                            )

                            if (upload.stored_path.isNotBlank()) {
                                Text(
                                    text = upload.stored_path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DzMuted
                                )
                            }

                            if (upload.uploader_message.isNotBlank()) {
                                Text(
                                    text = upload.uploader_message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DzMuted
                                )
                            }

                            if (upload.scan_status.isNotBlank() &&
                                upload.scan_status != "not_scanned"
                            ) {
                                Text(
                                    text = stringResource(R.string.drop_zone_scan_value, upload.scan_status),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DzMuted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.drop_zone_close), color = DzOrangeSoft)
            }
        }
    )
}

private fun formatDzEpoch(epoch: Long): String {
    if (epoch <= 0L) return ""
    return java.text.SimpleDateFormat(
        "yyyy-MM-dd HH:mm",
        Locale.getDefault()
    ).format(java.util.Date(epoch * 1000L))
}

private fun dropZoneIsExpired(zone: DropZoneInfo): Boolean {
    val expires = zone.expires_epoch
    if (expires <= 0L) return false
    return expires <= (System.currentTimeMillis() / 1000L)
}

@Composable
private fun DzSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = DzOrangeSoft
    )
}

@Composable
private fun DzSelectionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) DzOrange.copy(alpha = 0.18f) else DzPanelSoft.copy(alpha = 0.78f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) DzOrange.copy(alpha = 0.85f) else DzPanelLine.copy(alpha = 0.85f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = if (selected) "✓" else "○",
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) DzOrangeSoft else DzMuted
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DzText
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DzMuted
                )
            }
        }
    }
}

@Composable
private fun DropZoneExistingDetails(zone: DropZoneInfo) {
    val brandingSummary = dropZoneBrandingSummary(zone)
    val duplicatePolicyText = stringResource(duplicatePolicyLabelRes(zone.duplicate_policy))
    val maxFileLimitText = if (zone.max_file_bytes > 0L) {
        stringResource(R.string.drop_zone_max_file_value, formatDzBytes(zone.max_file_bytes))
    } else {
        ""
    }
    val maxTotalLimitText = if (zone.max_total_bytes > 0L) {
        stringResource(R.string.drop_zone_max_total_value, formatDzBytes(zone.max_total_bytes))
    } else {
        ""
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (zone.has_pending_uploads || zone.pending_upload_count > 0L) {
            Text(
                text = stringResource(R.string.drop_zone_pending_uploads, zone.pending_upload_count),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = DzOrangeSoft
            )
        }

        Text(
            text = stringResource(R.string.drop_zone_duplicate_policy_value, duplicatePolicyText),
            style = MaterialTheme.typography.bodySmall,
            color = DzMuted
        )

        if (zone.max_file_bytes > 0L || zone.max_total_bytes > 0L) {
            Text(
                text = buildString {
                    if (zone.max_file_bytes > 0L) {
                        append(maxFileLimitText)
                    }
                    if (zone.max_total_bytes > 0L) {
                        if (isNotEmpty()) append(" • ")
                        append(maxTotalLimitText)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )
        }

        if (brandingSummary.isNotBlank()) {
            Text(
                text = stringResource(R.string.drop_zone_branding_value, brandingSummary),
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )
        }
    }
}

@Composable
private fun DropZoneEditDialog(
    draft: DzEditDraft,
    onDraftChange: (DzEditDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: (DzEditDraft) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DzPanel,
        titleContentColor = DzText,
        textContentColor = DzMuted,
        title = { Text(stringResource(R.string.drop_zone_edit_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.drop_zone_edit_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = DzMuted
                )

                DzSectionTitle(stringResource(R.string.drop_zone_section_basics))

                DzTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    label = stringResource(R.string.drop_zone_name_label),
                    placeholder = stringResource(R.string.drop_zone_name_placeholder)
                )

                DzSectionTitle(stringResource(R.string.drop_zone_section_limits))

                DzTextField(
                    value = draft.maxFileBytesText,
                    onValueChange = { onDraftChange(draft.copy(maxFileBytesText = it)) },
                    label = stringResource(R.string.drop_zone_max_file_label),
                    placeholder = stringResource(R.string.drop_zone_max_file_placeholder)
                )

                DzTextField(
                    value = draft.maxTotalBytesText,
                    onValueChange = { onDraftChange(draft.copy(maxTotalBytesText = it)) },
                    label = stringResource(R.string.drop_zone_max_total_label),
                    placeholder = stringResource(R.string.drop_zone_max_total_placeholder)
                )

                DzSectionTitle(stringResource(R.string.drop_zone_section_duplicates))

                DzSelectionRow(
                    title = stringResource(R.string.drop_zone_duplicate_version_title),
                    subtitle = stringResource(R.string.drop_zone_duplicate_version_desc),
                    selected = draft.duplicatePolicy == "version",
                    onClick = { onDraftChange(draft.copy(duplicatePolicy = "version")) }
                )

                DzSelectionRow(
                    title = stringResource(R.string.drop_zone_duplicate_keep_both_title),
                    subtitle = stringResource(R.string.drop_zone_duplicate_keep_both_desc),
                    selected = draft.duplicatePolicy == "keep_both",
                    onClick = { onDraftChange(draft.copy(duplicatePolicy = "keep_both")) }
                )

                DzSelectionRow(
                    title = stringResource(R.string.drop_zone_duplicate_reject_title),
                    subtitle = stringResource(R.string.drop_zone_duplicate_reject_desc),
                    selected = draft.duplicatePolicy == "reject",
                    onClick = { onDraftChange(draft.copy(duplicatePolicy = "reject")) }
                )

                DzSectionTitle(stringResource(R.string.drop_zone_section_branding))

                DzTextField(
                    value = draft.brandingCompanyName,
                    onValueChange = { onDraftChange(draft.copy(brandingCompanyName = it)) },
                    label = stringResource(R.string.drop_zone_company_brand_label),
                    placeholder = "Pohjola Cloud Oy"
                )

                DzTextField(
                    value = draft.brandingKicker,
                    onValueChange = { onDraftChange(draft.copy(brandingKicker = it)) },
                    label = stringResource(R.string.drop_zone_kicker_label),
                    placeholder = stringResource(R.string.drop_zone_kicker_placeholder)
                )

                DzTextField(
                    value = draft.brandingTitle,
                    onValueChange = { onDraftChange(draft.copy(brandingTitle = it)) },
                    label = stringResource(R.string.drop_zone_public_title_label),
                    placeholder = stringResource(R.string.drop_zone_public_title_placeholder)
                )

                OutlinedTextField(
                    value = draft.brandingDescription,
                    onValueChange = { onDraftChange(draft.copy(brandingDescription = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    label = { Text(stringResource(R.string.drop_zone_public_desc_label)) },
                    placeholder = { Text(stringResource(R.string.drop_zone_public_desc_placeholder)) },
                    colors = dzTextFieldColors()
                )

                DzTextField(
                    value = draft.brandingButtonText,
                    onValueChange = { onDraftChange(draft.copy(brandingButtonText = it)) },
                    label = stringResource(R.string.drop_zone_button_text_label),
                    placeholder = stringResource(R.string.drop_zone_button_text_placeholder)
                )

                DzTextField(
                    value = draft.brandingFooterText,
                    onValueChange = { onDraftChange(draft.copy(brandingFooterText = it)) },
                    label = stringResource(R.string.drop_zone_footer_text_label),
                    placeholder = stringResource(R.string.drop_zone_footer_text_placeholder)
                )

                DzTextField(
                    value = draft.brandingLogoUrl,
                    onValueChange = { onDraftChange(draft.copy(brandingLogoUrl = it)) },
                    label = stringResource(R.string.drop_zone_logo_url_label),
                    placeholder = "https://example.com/logo.png"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DzTextField(
                        value = draft.brandingPrimaryColor,
                        onValueChange = { onDraftChange(draft.copy(brandingPrimaryColor = it)) },
                        label = stringResource(R.string.drop_zone_primary_color_label),
                        placeholder = "#ff9f1a",
                        modifier = Modifier.weight(1f)
                    )

                    DzTextField(
                        value = draft.brandingButtonTextColor,
                        onValueChange = { onDraftChange(draft.copy(brandingButtonTextColor = it)) },
                        label = stringResource(R.string.drop_zone_button_text_color_label),
                        placeholder = "#000000",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DzTextField(
                        value = draft.brandingBackgroundColor,
                        onValueChange = { onDraftChange(draft.copy(brandingBackgroundColor = it)) },
                        label = stringResource(R.string.drop_zone_background_color_label),
                        placeholder = "#070a10",
                        modifier = Modifier.weight(1f)
                    )

                    DzTextField(
                        value = draft.brandingPanelColor,
                        onValueChange = { onDraftChange(draft.copy(brandingPanelColor = it)) },
                        label = stringResource(R.string.drop_zone_panel_color_label),
                        placeholder = "#15161d",
                        modifier = Modifier.weight(1f)
                    )
                }

                DzTextField(
                    value = draft.brandingTextColor,
                    onValueChange = { onDraftChange(draft.copy(brandingTextColor = it)) },
                    label = stringResource(R.string.drop_zone_text_color_label),
                    placeholder = "#f4f4f6"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text(stringResource(R.string.drop_zone_save), color = DzOrangeSoft)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.drop_zone_cancel), color = DzMuted)
            }
        }
    )
}

@Composable
private fun DzTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        colors = dzTextFieldColors()
    )
}

@Composable
private fun dzTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = DzText,
        unfocusedTextColor = DzText,
        focusedContainerColor = DzBg.copy(alpha = 0.35f),
        unfocusedContainerColor = DzBg.copy(alpha = 0.35f),
        focusedBorderColor = DzOrange,
        unfocusedBorderColor = DzPanelLine,
        focusedLabelColor = DzOrange,
        unfocusedLabelColor = DzMuted,
        cursorColor = DzOrange,
        focusedPlaceholderColor = DzMuted,
        unfocusedPlaceholderColor = DzMuted
    )

private fun formatDzBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups - 1])
}

private fun dzEditDraftFrom(zone: DropZoneInfo): DzEditDraft {
    val branding = zone.branding

    return DzEditDraft(
        id = zone.id,
        name = zone.name.ifBlank { "Drop Zone" },
        maxFileBytesText = formatDzLimitForEdit(zone.max_file_bytes),
        maxTotalBytesText = formatDzLimitForEdit(zone.max_total_bytes),
        duplicatePolicy = when (zone.duplicate_policy.lowercase(Locale.US)) {
            "keep_both" -> "keep_both"
            "reject" -> "reject"
            else -> "version"
        },
        brandingCompanyName = branding.company_name,
        brandingKicker = branding.kicker,
        brandingTitle = branding.title,
        brandingDescription = branding.description,
        brandingButtonText = branding.button_text,
        brandingFooterText = branding.footer_text,
        brandingLogoUrl = branding.logo_url,
        brandingPrimaryColor = branding.primary_color,
        brandingBackgroundColor = branding.background_color,
        brandingPanelColor = branding.panel_color,
        brandingTextColor = branding.text_color,
        brandingButtonTextColor = branding.button_text_color
    )
}

private fun formatDzLimitForEdit(bytes: Long): String {
    if (bytes <= 0L) return ""

    val gib = 1024L * 1024L * 1024L
    val mib = 1024L * 1024L
    val kib = 1024L

    return when {
        bytes % gib == 0L -> "${bytes / gib} GB"
        bytes % mib == 0L -> "${bytes / mib} MB"
        bytes % kib == 0L -> "${bytes / kib} KB"
        else -> "$bytes B"
    }
}

private fun duplicatePolicyLabelRes(policy: String): Int =
    when (policy.lowercase(Locale.US)) {
        "keep_both" -> R.string.drop_zone_duplicate_label_keep_both
        "reject" -> R.string.drop_zone_duplicate_label_reject
        else -> R.string.drop_zone_duplicate_label_version
    }

private fun dropZoneBrandingSummary(zone: DropZoneInfo): String {
    val parts = listOf(
        zone.branding.company_name,
        zone.branding.title,
        zone.branding.kicker
    )
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    return parts.take(2).joinToString(" • ")
}
