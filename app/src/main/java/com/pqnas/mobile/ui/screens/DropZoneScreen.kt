package com.pqnas.mobile.ui.screens

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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
    val label: String,
    val seconds: Long,
    val description: String
)

private val DzExpiryOptions = listOf(
    DzExpiryOption("1 day", 1L * 24L * 60L * 60L, "Short-lived upload link"),
    DzExpiryOption("7 days", 7L * 24L * 60L * 60L, "Good default for most cases"),
    DzExpiryOption("30 days", 30L * 24L * 60L * 60L, "Longer customer/project collection"),
    DzExpiryOption("90 days", 90L * 24L * 60L * 60L, "Maximum long-running intake")
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
    onClose: () -> Unit
) {
    var disableCandidate by remember { mutableStateOf<DropZoneInfo?>(null) }
    var showCreateForm by remember { mutableStateOf(false) }
    var editDraft by remember { mutableStateOf<DzEditDraft?>(null) }

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
                                text = "No Drop Zones yet. Open the create form above when you want to receive files from outsiders.",
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

    disableCandidate?.let { zone ->
        AlertDialog(
            onDismissRequest = { disableCandidate = null },
            containerColor = DzPanel,
            titleContentColor = DzText,
            textContentColor = DzMuted,
            title = { Text("Disable Drop Zone?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(zone.name.ifBlank { "Drop Zone" })
                    Text("This will stop the public upload link from accepting more files.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        disableCandidate = null
                        onDisable(zone.id)
                    }
                ) {
                    Text("Disable", color = DzBad)
                }
            },
            dismissButton = {
                TextButton(onClick = { disableCandidate = null }) {
                    Text("Cancel", color = DzMuted)
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
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = DzText
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "DNA-NEXUS SERVER",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = DzOrange
            )

            Text(
                text = "Drop Zone",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = DzText
            )

            Text(
                text = "Secure one-way upload links for outsiders.",
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )
        }
    }
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
                text = "Create branded upload link",
                style = MaterialTheme.typography.titleMedium,
                color = DzText
            )

            Text(
                text = "Open the form only when you want to create a new branded public upload page.",
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )

            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create branded upload link")
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
                text = "Create branded upload link",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DzText
            )

            Text(
                text = "Uploaders can send files only. They cannot browse, download, rename, or delete anything.",
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )

            DzSectionTitle("Basics")

            DzTextField(
                value = name,
                onValueChange = onNameChange,
                label = "Name",
                placeholder = "Drop Zone"
            )

            DzTextField(
                value = destination,
                onValueChange = onDestinationChange,
                label = "Destination folder",
                placeholder = "Incoming/Drop Zones/Drop Zone"
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Password, optional") },
                placeholder = { Text("Leave empty for no password") },
                visualTransformation = PasswordVisualTransformation(),
                colors = dzTextFieldColors()
            )

            DzSectionTitle("Validity")

            DzExpiryOptions.forEach { option ->
                DzSelectionRow(
                    title = option.label,
                    subtitle = option.description,
                    selected = expiresInSeconds == option.seconds,
                    onClick = { onExpiresInSecondsChange(option.seconds) }
                )
            }

            DzSectionTitle("Limits")

            DzTextField(
                value = maxFileBytesText,
                onValueChange = onMaxFileBytesTextChange,
                label = "Max file size, optional",
                placeholder = "Example: 500 MB, 2 GB"
            )

            DzTextField(
                value = maxTotalBytesText,
                onValueChange = onMaxTotalBytesTextChange,
                label = "Max total upload size, optional",
                placeholder = "Example: 5 GB"
            )

            DzSectionTitle("Duplicate filenames")

            DzSelectionRow(
                title = "Version existing file",
                subtitle = "Preserve the old live file as a File Manager version, then replace live file.",
                selected = duplicatePolicy == "version",
                onClick = { onDuplicatePolicyChange("version") }
            )

            DzSelectionRow(
                title = "Keep both",
                subtitle = "Save the new upload as name (1).ext when a file already exists.",
                selected = duplicatePolicy == "keep_both",
                onClick = { onDuplicatePolicyChange("keep_both") }
            )

            DzSelectionRow(
                title = "Reject duplicate",
                subtitle = "Reject uploads that would overwrite an existing filename.",
                selected = duplicatePolicy == "reject",
                onClick = { onDuplicatePolicyChange("reject") }
            )

            DzSectionTitle("Branded public page")

            DzTextField(
                value = brandingCompanyName,
                onValueChange = onBrandingCompanyNameChange,
                label = "Company / page brand",
                placeholder = "Pohjola Cloud Oy"
            )

            DzTextField(
                value = brandingKicker,
                onValueChange = onBrandingKickerChange,
                label = "Kicker",
                placeholder = "Secure upload"
            )

            DzTextField(
                value = brandingTitle,
                onValueChange = onBrandingTitleChange,
                label = "Public page title",
                placeholder = "Send files securely"
            )

            OutlinedTextField(
                value = brandingDescription,
                onValueChange = onBrandingDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                label = { Text("Public page description") },
                placeholder = { Text("Tell the uploader what this page is for.") },
                colors = dzTextFieldColors()
            )

            DzTextField(
                value = brandingButtonText,
                onValueChange = onBrandingButtonTextChange,
                label = "Upload button text",
                placeholder = "Upload files"
            )

            DzTextField(
                value = brandingFooterText,
                onValueChange = onBrandingFooterTextChange,
                label = "Footer text",
                placeholder = "Powered by DNA-Nexus"
            )

            DzTextField(
                value = brandingLogoUrl,
                onValueChange = onBrandingLogoUrlChange,
                label = "Logo URL, optional",
                placeholder = "https://example.com/logo.png"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DzTextField(
                    value = brandingPrimaryColor,
                    onValueChange = onBrandingPrimaryColorChange,
                    label = "Primary",
                    placeholder = "#ff9f1a",
                    modifier = Modifier.weight(1f)
                )

                DzTextField(
                    value = brandingButtonTextColor,
                    onValueChange = onBrandingButtonTextColorChange,
                    label = "Button text",
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
                    label = "Background",
                    placeholder = "#070a10",
                    modifier = Modifier.weight(1f)
                )

                DzTextField(
                    value = brandingPanelColor,
                    onValueChange = onBrandingPanelColorChange,
                    label = "Panel",
                    placeholder = "#15161d",
                    modifier = Modifier.weight(1f)
                )
            }

            DzTextField(
                value = brandingTextColor,
                onValueChange = onBrandingTextColorChange,
                label = "Text color",
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
                Text(if (creating) "Creating..." else "Create Drop Zone")
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
                text = "New public upload link",
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
                Text("Copy link")
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
                    text = "Existing Drop Zones",
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
                    Text(if (loading) "Loading..." else "Refresh")
                }
            }

            Text(
                text = "For security, public URLs are shown only immediately after creation.",
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )
        }
    }
}

@Composable
private fun DropZoneExistingCard(
    zone: DropZoneInfo,
    onEdit: () -> Unit,
    onDisable: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = zone.name.ifBlank { "Drop Zone" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = DzText
                    )

                    Text(
                        text = zone.destination_path.ifBlank { "No destination" },
                        style = MaterialTheme.typography.bodySmall,
                        color = DzMuted
                    )
                }

                Text(
                    text = if (zone.disabled) "Disabled" else "Active",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (zone.disabled) DzMuted else DzGood
                )
            }

            Text(
                text = buildString {
                    append(zone.upload_count)
                    append(" uploads")
                    if (zone.bytes_uploaded > 0L) {
                        append(" • ")
                        append(formatDzBytes(zone.bytes_uploaded))
                    }
                    if (zone.password_required) {
                        append(" • password")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )

            DropZoneExistingDetails(zone)

            HorizontalDivider(color = DzPanelLine.copy(alpha = 0.8f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onEdit,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = DzOrangeSoft
                    )
                ) {
                    Text("Edit")
                }

                if (!zone.disabled) {
                    TextButton(
                        onClick = onDisable,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = DzOrangeSoft
                        )
                    ) {
                        Text("Disable")
                    }
                }
            }
        }
    }
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

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (zone.has_pending_uploads || zone.pending_upload_count > 0L) {
            Text(
                text = "New uploads waiting: ${zone.pending_upload_count}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = DzOrangeSoft
            )
        }

        Text(
            text = "Duplicate policy: ${duplicatePolicyLabel(zone.duplicate_policy)}",
            style = MaterialTheme.typography.bodySmall,
            color = DzMuted
        )

        if (zone.max_file_bytes > 0L || zone.max_total_bytes > 0L) {
            Text(
                text = buildString {
                    if (zone.max_file_bytes > 0L) {
                        append("Max file ")
                        append(formatDzBytes(zone.max_file_bytes))
                    }
                    if (zone.max_total_bytes > 0L) {
                        if (isNotEmpty()) append(" • ")
                        append("Max total ")
                        append(formatDzBytes(zone.max_total_bytes))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )
        }

        if (brandingSummary.isNotBlank()) {
            Text(
                text = "Branding: $brandingSummary",
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
        title = { Text("Edit Drop Zone") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "This edits the owner-side settings. It does not change the destination folder, password, or public token.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DzMuted
                )

                DzSectionTitle("Basics")

                DzTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    label = "Name",
                    placeholder = "Drop Zone"
                )

                DzSectionTitle("Limits")

                DzTextField(
                    value = draft.maxFileBytesText,
                    onValueChange = { onDraftChange(draft.copy(maxFileBytesText = it)) },
                    label = "Max file size, optional",
                    placeholder = "Example: 500 MB, 2 GB"
                )

                DzTextField(
                    value = draft.maxTotalBytesText,
                    onValueChange = { onDraftChange(draft.copy(maxTotalBytesText = it)) },
                    label = "Max total upload size, optional",
                    placeholder = "Example: 5 GB"
                )

                DzSectionTitle("Duplicate filenames")

                DzSelectionRow(
                    title = "Version existing file",
                    subtitle = "Preserve the old live file as a File Manager version, then replace live file.",
                    selected = draft.duplicatePolicy == "version",
                    onClick = { onDraftChange(draft.copy(duplicatePolicy = "version")) }
                )

                DzSelectionRow(
                    title = "Keep both",
                    subtitle = "Save the new upload as name (1).ext when a file already exists.",
                    selected = draft.duplicatePolicy == "keep_both",
                    onClick = { onDraftChange(draft.copy(duplicatePolicy = "keep_both")) }
                )

                DzSelectionRow(
                    title = "Reject duplicate",
                    subtitle = "Reject uploads that would overwrite an existing filename.",
                    selected = draft.duplicatePolicy == "reject",
                    onClick = { onDraftChange(draft.copy(duplicatePolicy = "reject")) }
                )

                DzSectionTitle("Branded public page")

                DzTextField(
                    value = draft.brandingCompanyName,
                    onValueChange = { onDraftChange(draft.copy(brandingCompanyName = it)) },
                    label = "Company / page brand",
                    placeholder = "Pohjola Cloud Oy"
                )

                DzTextField(
                    value = draft.brandingKicker,
                    onValueChange = { onDraftChange(draft.copy(brandingKicker = it)) },
                    label = "Kicker",
                    placeholder = "Secure upload"
                )

                DzTextField(
                    value = draft.brandingTitle,
                    onValueChange = { onDraftChange(draft.copy(brandingTitle = it)) },
                    label = "Public page title",
                    placeholder = "Send files securely"
                )

                OutlinedTextField(
                    value = draft.brandingDescription,
                    onValueChange = { onDraftChange(draft.copy(brandingDescription = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    label = { Text("Public page description") },
                    placeholder = { Text("Tell the uploader what this page is for.") },
                    colors = dzTextFieldColors()
                )

                DzTextField(
                    value = draft.brandingButtonText,
                    onValueChange = { onDraftChange(draft.copy(brandingButtonText = it)) },
                    label = "Upload button text",
                    placeholder = "Upload files"
                )

                DzTextField(
                    value = draft.brandingFooterText,
                    onValueChange = { onDraftChange(draft.copy(brandingFooterText = it)) },
                    label = "Footer text",
                    placeholder = "Powered by DNA-Nexus"
                )

                DzTextField(
                    value = draft.brandingLogoUrl,
                    onValueChange = { onDraftChange(draft.copy(brandingLogoUrl = it)) },
                    label = "Logo URL, optional",
                    placeholder = "https://example.com/logo.png"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DzTextField(
                        value = draft.brandingPrimaryColor,
                        onValueChange = { onDraftChange(draft.copy(brandingPrimaryColor = it)) },
                        label = "Primary",
                        placeholder = "#ff9f1a",
                        modifier = Modifier.weight(1f)
                    )

                    DzTextField(
                        value = draft.brandingButtonTextColor,
                        onValueChange = { onDraftChange(draft.copy(brandingButtonTextColor = it)) },
                        label = "Button text",
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
                        label = "Background",
                        placeholder = "#070a10",
                        modifier = Modifier.weight(1f)
                    )

                    DzTextField(
                        value = draft.brandingPanelColor,
                        onValueChange = { onDraftChange(draft.copy(brandingPanelColor = it)) },
                        label = "Panel",
                        placeholder = "#15161d",
                        modifier = Modifier.weight(1f)
                    )
                }

                DzTextField(
                    value = draft.brandingTextColor,
                    onValueChange = { onDraftChange(draft.copy(brandingTextColor = it)) },
                    label = "Text color",
                    placeholder = "#f4f4f6"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text("Save", color = DzOrangeSoft)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DzMuted)
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

private fun duplicatePolicyLabel(policy: String): String =
    when (policy.lowercase(Locale.US)) {
        "keep_both" -> "Keep both"
        "reject" -> "Reject duplicates"
        else -> "Version existing file"
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
