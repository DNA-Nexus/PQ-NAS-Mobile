package com.pqnas.mobile.ui.screens

import androidx.core.net.toUri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.R
import com.pqnas.mobile.api.ContactDto
import com.pqnas.mobile.api.ContactUpsertRequest
import com.pqnas.mobile.contacts.ContactsRepository
import kotlinx.coroutines.launch
import java.util.Locale

private data class ContactFormState(
    val subjectFingerprint: String = "",
    val subjectKind: String = "manual_contact",
    val contactType: String = "person",
    val displayName: String = "",
    val company: String = "",
    val title: String = "",
    val nickname: String = "",
    val status: String = "active",
    val tags: String = "",
    val email: String = "",
    val phone: String = "",
    val mobile: String = "",
    val website: String = "",
    val street: String = "",
    val postalCode: String = "",
    val city: String = "",
    val country: String = "",
    val deliveryName: String = "",
    val deliveryStreet: String = "",
    val deliveryPostalCode: String = "",
    val deliveryCity: String = "",
    val deliveryCountry: String = "",
    val notes: String = ""
) {
    companion object {
        fun fromContact(c: ContactDto): ContactFormState =
            ContactFormState(
                subjectFingerprint = c.subject_fingerprint,
                subjectKind = c.subject_kind.ifBlank { "manual_contact" },
                contactType = c.contact_type.ifBlank { "person" },
                displayName = c.display_name,
                company = c.company,
                title = c.title,
                nickname = c.nickname,
                status = c.status.ifBlank { "active" },
                tags = c.tags,
                email = c.email,
                phone = c.phone,
                mobile = c.mobile,
                website = c.website,
                street = c.street,
                postalCode = c.postal_code,
                city = c.city,
                country = c.country,
                deliveryName = c.delivery_name,
                deliveryStreet = c.delivery_street,
                deliveryPostalCode = c.delivery_postal_code,
                deliveryCity = c.delivery_city,
                deliveryCountry = c.delivery_country,
                notes = c.notes
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    repository: ContactsRepository,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var contacts by remember { mutableStateOf<List<ContactDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(context.getString(R.string.contacts_loading)) }
    var search by remember { mutableStateOf("") }
    var selectedFingerprint by remember { mutableStateOf("") }
    var form by remember {
        mutableStateOf(
            ContactFormState(
                subjectFingerprint = repository.allocateManualAnchor(emptyList())
            )
        )
    }

    var pendingSave by remember { mutableStateOf<ContactUpsertRequest?>(null) }
    val pendingDuplicateLines = remember { mutableStateListOf<String>() }
    var pendingDelete by remember { mutableStateOf<ContactDto?>(null) }

    fun normalizedSelected(): String =
        ContactsRepository.normalizeFingerprint(selectedFingerprint)

    fun selectedContact(): ContactDto? {
        val selected = normalizedSelected()
        if (selected.isBlank()) return null

        return contacts.firstOrNull {
            ContactsRepository.normalizeFingerprint(it.subject_fingerprint) == selected
        }
    }

    fun clearEditor() {
        selectedFingerprint = ""
        form = ContactFormState(
            subjectFingerprint = repository.allocateManualAnchor(
                contacts.map { it.subject_fingerprint }
            )
        )
    }

    fun fillEditor(c: ContactDto) {
        selectedFingerprint = ContactsRepository.normalizeFingerprint(c.subject_fingerprint)
        form = ContactFormState.fromContact(c)
    }

    fun reloadContacts(selectFingerprint: String? = null) {
        scope.launch {
            loading = true
            status = context.getString(R.string.contacts_loading)

            runCatching {
                repository.listContacts()
            }.onSuccess { loaded ->
                contacts = loaded.sortedWith(
                    compareBy<ContactDto> {
                        (it.display_name.ifBlank { it.company }).lowercase(Locale.getDefault())
                    }
                )
                loading = false
                status = "OK"

                val nextFp = ContactsRepository.normalizeFingerprint(selectFingerprint)
                if (nextFp.isNotBlank()) {
                    contacts.firstOrNull {
                        ContactsRepository.normalizeFingerprint(it.subject_fingerprint) == nextFp
                    }?.let { fillEditor(it) }
                }
            }.onFailure { e ->
                loading = false
                status = e.message ?: context.getString(R.string.contacts_load_failed)
            }
        }
    }

    fun buildPayload(): ContactUpsertRequest {
        val existing = selectedContact()
        val fp = ContactsRepository.normalizeFingerprint(
            form.subjectFingerprint.ifBlank {
                repository.allocateManualAnchor(contacts.map { it.subject_fingerprint })
            }
        )

        return ContactUpsertRequest(
            subject_fingerprint = fp,
            subject_kind = existing?.subject_kind?.ifBlank { form.subjectKind } ?: "manual_contact",
            contact_type = form.contactType.ifBlank { "person" },
            display_name = form.displayName.trim(),
            company = form.company.trim(),
            title = form.title.trim(),
            nickname = form.nickname.trim(),
            status = form.status.ifBlank { "active" }.trim(),
            tags = form.tags.trim(),
            email = form.email.trim(),
            phone = form.phone.trim(),
            mobile = form.mobile.trim(),
            website = form.website.trim(),
            street = form.street.trim(),
            postal_code = form.postalCode.trim(),
            city = form.city.trim(),
            country = form.country.trim(),
            delivery_name = form.deliveryName.trim(),
            delivery_street = form.deliveryStreet.trim(),
            delivery_postal_code = form.deliveryPostalCode.trim(),
            delivery_city = form.deliveryCity.trim(),
            delivery_country = form.deliveryCountry.trim(),
            notes = form.notes.trim()
        )
    }

    fun duplicateCandidates(payload: ContactUpsertRequest): List<String> {
        val fp = ContactsRepository.normalizeFingerprint(payload.subject_fingerprint)
        val email = ContactsRepository.normalizeEmail(payload.email)
        val phone = ContactsRepository.normalizePhone(payload.phone)
        val mobile = ContactsRepository.normalizePhone(payload.mobile)
        val name = ContactsRepository.normalizeNameKey(payload.display_name)
        val company = ContactsRepository.normalizeNameKey(payload.company)

        return contacts.mapNotNull { c ->
            val cfp = ContactsRepository.normalizeFingerprint(c.subject_fingerprint)
            if (cfp.isNotBlank() && cfp == fp) return@mapNotNull null

            val reasons = mutableListOf<String>()

            if (email.isNotBlank() && ContactsRepository.normalizeEmail(c.email) == email) {
                reasons.add(context.getString(R.string.contacts_duplicate_same_email))
            }
            if (phone.isNotBlank() && ContactsRepository.normalizePhone(c.phone) == phone) {
                reasons.add(context.getString(R.string.contacts_duplicate_same_phone))
            }
            if (mobile.isNotBlank() && ContactsRepository.normalizePhone(c.mobile) == mobile) {
                reasons.add(context.getString(R.string.contacts_duplicate_same_mobile))
            }

            val cName = ContactsRepository.normalizeNameKey(c.display_name)
            val cCompany = ContactsRepository.normalizeNameKey(c.company)

            if (name.isNotBlank() && name == cName && company.isNotBlank() && company == cCompany) {
                reasons.add(context.getString(R.string.contacts_duplicate_same_name_company))
            } else if (name.isNotBlank() && name == cName && company.isBlank() && cCompany.isBlank()) {
                reasons.add(context.getString(R.string.contacts_duplicate_same_name))
            }

            if (reasons.isEmpty()) {
                null
            } else {
                val label = contactLabel(c)
                "$label: ${reasons.joinToString(", ")}"
            }
        }.take(5)
    }

    fun commitSave(payload: ContactUpsertRequest) {
        scope.launch {
            saving = true
            status = context.getString(R.string.contacts_saving)

            runCatching {
                repository.upsertContact(payload)
            }.onSuccess { saved ->
                status = context.getString(R.string.contacts_saved)
                reloadContacts(saved.subject_fingerprint.ifBlank { payload.subject_fingerprint })
            }.onFailure { e ->
                status = e.message ?: context.getString(R.string.contacts_save_failed)
            }

            saving = false
        }
    }

    fun requestSave() {
        val payload = buildPayload()

        if (payload.subject_fingerprint.isBlank()) {
            status = context.getString(R.string.contacts_identity_missing)
            return
        }

        if (payload.display_name.isBlank()) {
            status = context.getString(R.string.contacts_display_name_required)
            return
        }

        val dupes = duplicateCandidates(payload)
        if (dupes.isNotEmpty()) {
            pendingSave = payload
            pendingDuplicateLines.clear()
            pendingDuplicateLines.addAll(dupes)
            return
        }

        commitSave(payload)
    }

    fun deleteSelected(c: ContactDto) {
        val fp = ContactsRepository.normalizeFingerprint(c.subject_fingerprint)
        if (fp.isBlank()) return

        scope.launch {
            status = context.getString(R.string.contacts_deleting)

            runCatching {
                repository.deleteContact(fp)
            }.onSuccess {
                status = context.getString(R.string.contacts_deleted)
                clearEditor()
                reloadContacts()
            }.onFailure { e ->
                status = e.message ?: context.getString(R.string.contacts_delete_failed)
            }
        }
    }

    fun copyText(label: String, value: String, success: String) {
        val clean = value.trim()
        if (clean.isBlank()) {
            status = context.getString(R.string.contacts_nothing_to_copy)
            return
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, clean))
        status = success
    }

    fun currentContactLike(): ContactDto =
        buildPayload().let {
            ContactDto(
                subject_fingerprint = it.subject_fingerprint,
                subject_kind = it.subject_kind,
                contact_type = it.contact_type,
                display_name = it.display_name,
                company = it.company,
                title = it.title,
                nickname = it.nickname,
                status = it.status,
                tags = it.tags,
                email = it.email,
                phone = it.phone,
                mobile = it.mobile,
                website = it.website,
                street = it.street,
                postal_code = it.postal_code,
                city = it.city,
                country = it.country,
                delivery_name = it.delivery_name,
                delivery_street = it.delivery_street,
                delivery_postal_code = it.delivery_postal_code,
                delivery_city = it.delivery_city,
                delivery_country = it.delivery_country,
                notes = it.notes
            )
        }

    fun openWebsite() {
        var url = currentContactLike().website.trim()
        if (url.isBlank()) {
            status = context.getString(R.string.contacts_no_website)
            return
        }

        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            url = "https://$url"
        }

        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }.onFailure {
            status = context.getString(R.string.contacts_open_website_failed)
        }
    }

    LaunchedEffect(Unit) {
        reloadContacts()
    }

    val filteredContacts = contacts.filter { c ->
        val q = search.trim().lowercase(Locale.getDefault())
        if (q.isBlank()) {
            true
        } else {
            listOf(
                c.display_name,
                c.company,
                c.title,
                c.nickname,
                c.email,
                c.phone,
                c.mobile,
                c.website,
                c.street,
                c.postal_code,
                c.city,
                c.country,
                c.tags,
                c.notes
            ).any { it.lowercase(Locale.getDefault()).contains(q) }
        }
    }

    pendingSave?.let { payload ->
        AlertDialog(
            onDismissRequest = {
                pendingSave = null
                pendingDuplicateLines.clear()
            },
            title = { Text(stringResource(R.string.contacts_duplicate_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.contacts_duplicate_message))
                    pendingDuplicateLines.forEach { line ->
                        Text(
                            text = "• $line",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(stringResource(R.string.contacts_duplicate_save_anyway_question))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSave = null
                        pendingDuplicateLines.clear()
                        commitSave(payload)
                    }
                ) {
                    Text(stringResource(R.string.contacts_save_anyway))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingSave = null
                        pendingDuplicateLines.clear()
                        status = context.getString(R.string.contacts_duplicate_cancelled)
                    }
                ) {
                    Text(stringResource(R.string.contacts_review))
                }
            }
        )
    }

    pendingDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.contacts_delete_title)) },
            text = {
                Text(stringResource(R.string.contacts_delete_confirm, contactLabel(contact, stringResource(R.string.contacts_fallback_contact))))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        deleteSelected(contact)
                    }
                ) {
                    Text(stringResource(R.string.contacts_delete_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contacts)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.contacts_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.contacts),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.contacts_shown_total, filteredContacts.size, contacts.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(stringResource(R.string.contacts_search_label)) },
                    placeholder = { Text(stringResource(R.string.contacts_search_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { reloadContacts() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.refresh))
                    }

                    Button(
                        onClick = { clearEditor() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.contacts_new_contact))
                    }
                }
            }

            item {
                StatusCard(status = status)
            }

            item {
                Text(
                    text = stringResource(R.string.contacts_address_book),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (filteredContacts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (loading) stringResource(R.string.contacts_loading) else stringResource(R.string.contacts_no_contacts_found),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (search.isBlank()) {
                                    stringResource(R.string.contacts_empty_desc)
                                } else {
                                    stringResource(R.string.contacts_try_another_search)
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredContacts,
                    key = { ContactsRepository.normalizeFingerprint(it.subject_fingerprint) }
                ) { contact ->
                    ContactRow(
                        contact = contact,
                        selected = ContactsRepository.normalizeFingerprint(contact.subject_fingerprint) == normalizedSelected(),
                        onClick = { fillEditor(contact) }
                    )
                }
            }

            item {
                ContactEditorSection(
                    form = form,
                    selected = selectedContact(),
                    saving = saving,
                    onFormChange = { form = it },
                    onSave = { requestSave() },
                    onClear = { clearEditor() },
                    onDelete = { selectedContact()?.let { pendingDelete = it } },
                    onCopyCard = {
                        val c = currentContactLike()
                        copyText(context.getString(R.string.contacts_card_clip_label), formatContactCard(c), context.getString(R.string.contacts_card_copied))
                    },
                    onCopyAddress = {
                        val c = currentContactLike()
                        copyText(context.getString(R.string.contacts_address_clip_label), formatAddress(c), context.getString(R.string.contacts_address_copied))
                    },
                    onCopyEmail = {
                        copyText(context.getString(R.string.contacts_email_clip_label), currentContactLike().email, context.getString(R.string.contacts_email_copied))
                    },
                    onCopyPhone = {
                        val c = currentContactLike()
                        copyText(context.getString(R.string.contacts_phone_clip_label), c.phone.ifBlank { c.mobile }, context.getString(R.string.contacts_phone_copied))
                    },
                    onOpenWebsite = { openWebsite() }
                )
            }

            item {
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun StatusCard(status: String) {
    if (status.isBlank()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                status == "OK" -> MaterialTheme.colorScheme.secondaryContainer
                status.contains("failed", ignoreCase = true) ||
                    status.contains("missing", ignoreCase = true) ||
                    status.contains("required", ignoreCase = true) ||
                    status.contains("could not", ignoreCase = true) ->
                    MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ContactRow(
    contact: ContactDto,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contactLabel(contact, stringResource(R.string.contacts_fallback_contact)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = stringResource(typeLabelRes(contact.contact_type)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val meta = listOf(
                contact.company,
                contact.city,
                contact.email,
                contact.phone.ifBlank { contact.mobile }
            ).filter { it.isNotBlank() }.joinToString(" • ")

            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (contact.title.isNotBlank()) {
                Text(
                    text = contact.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ContactEditorSection(
    form: ContactFormState,
    selected: ContactDto?,
    saving: Boolean,
    onFormChange: (ContactFormState) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onCopyCard: () -> Unit,
    onCopyAddress: () -> Unit,
    onCopyEmail: () -> Unit,
    onCopyPhone: () -> Unit,
    onOpenWebsite: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (selected == null) stringResource(R.string.contacts_new_contact) else stringResource(R.string.contacts_edit_contact),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.contacts_editor_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(stringResource(R.string.contacts_basic), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            FormField(
                label = stringResource(R.string.contacts_display_name),
                value = form.displayName,
                onChange = { onFormChange(form.copy(displayName = it)) }
            )

            FormField(
                label = stringResource(R.string.contacts_company),
                value = form.company,
                onChange = { onFormChange(form.copy(company = it)) }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(
                    label = stringResource(R.string.contacts_title_role),
                    value = form.title,
                    onChange = { onFormChange(form.copy(title = it)) },
                    modifier = Modifier.weight(1f)
                )

                FormField(
                    label = stringResource(R.string.contacts_nickname),
                    value = form.nickname,
                    onChange = { onFormChange(form.copy(nickname = it)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(
                    label = stringResource(R.string.contacts_contact_type),
                    value = form.contactType,
                    onChange = { onFormChange(form.copy(contactType = it)) },
                    modifier = Modifier.weight(1f)
                )

                FormField(
                    label = stringResource(R.string.contacts_status),
                    value = form.status,
                    onChange = { onFormChange(form.copy(status = it)) },
                    modifier = Modifier.weight(1f)
                )
            }

            FormField(
                label = stringResource(R.string.contacts_tags),
                value = form.tags,
                onChange = { onFormChange(form.copy(tags = it)) },
                placeholder = stringResource(R.string.contacts_tags_placeholder)
            )

            HorizontalDivider()

            Text(stringResource(R.string.contacts_contact_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(
                    label = stringResource(R.string.contacts_email),
                    value = form.email,
                    onChange = { onFormChange(form.copy(email = it)) },
                    modifier = Modifier.weight(1f)
                )

                FormField(
                    label = stringResource(R.string.contacts_phone),
                    value = form.phone,
                    onChange = { onFormChange(form.copy(phone = it)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(
                    label = stringResource(R.string.contacts_mobile),
                    value = form.mobile,
                    onChange = { onFormChange(form.copy(mobile = it)) },
                    modifier = Modifier.weight(1f)
                )

                FormField(
                    label = stringResource(R.string.contacts_website),
                    value = form.website,
                    onChange = { onFormChange(form.copy(website = it)) },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            Text(stringResource(R.string.contacts_address), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            FormField(
                label = stringResource(R.string.contacts_street_address),
                value = form.street,
                onChange = { onFormChange(form.copy(street = it)) }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(
                    label = stringResource(R.string.contacts_postal_code),
                    value = form.postalCode,
                    onChange = { onFormChange(form.copy(postalCode = it)) },
                    modifier = Modifier.weight(1f)
                )

                FormField(
                    label = stringResource(R.string.contacts_city),
                    value = form.city,
                    onChange = { onFormChange(form.copy(city = it)) },
                    modifier = Modifier.weight(1f)
                )
            }

            FormField(
                label = stringResource(R.string.contacts_country),
                value = form.country,
                onChange = { onFormChange(form.copy(country = it)) }
            )

            HorizontalDivider()

            Text(stringResource(R.string.contacts_notes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = form.notes,
                onValueChange = { onFormChange(form.copy(notes = it)) },
                label = { Text(stringResource(R.string.contacts_private_notes)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Text(stringResource(R.string.contacts_quick_actions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCopyCard, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.contacts_copy_card))
                }
                OutlinedButton(onClick = onCopyAddress, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.contacts_copy_address))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCopyEmail, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.contacts_copy_email))
                }
                OutlinedButton(onClick = onCopyPhone, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.contacts_copy_phone))
                }
            }

            OutlinedButton(onClick = onOpenWebsite, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.contacts_open_website))
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.contacts_identity_line, ContactsRepository.shortFingerprint(form.subjectFingerprint), form.subjectKind.ifBlank { "manual_contact" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saving) stringResource(R.string.contacts_saving_short) else stringResource(R.string.contacts_save_contact))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.contacts_clear))
                }

                OutlinedButton(
                    onClick = onDelete,
                    enabled = selected != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.contacts_delete_button))
                }
            }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder) }
        } else {
            null
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

private fun contactLabel(c: ContactDto, fallback: String = "Contact"): String =
    c.display_name.ifBlank {
        c.company.ifBlank {
            c.email.ifBlank {
                c.phone.ifBlank {
                    c.mobile.ifBlank {
                        ContactsRepository.shortFingerprint(c.subject_fingerprint).ifBlank { fallback }
                    }
                }
            }
        }
    }

private fun typeLabelRes(type: String): Int =
    when (type) {
        "company" -> R.string.contacts_type_company
        "customer" -> R.string.contacts_type_customer
        "supplier" -> R.string.contacts_type_supplier
        "family" -> R.string.contacts_type_family
        "other" -> R.string.contacts_type_other
        else -> R.string.contacts_type_person
    }

private fun formatAddress(c: ContactDto): String =
    listOf(
        c.street,
        listOf(c.postal_code, c.city).filter { it.isNotBlank() }.joinToString(" "),
        c.country
    ).filter { it.isNotBlank() }.joinToString("\n")

private fun formatContactCard(c: ContactDto): String =
    listOf(
        "[DNA-NEXUS-CONTACT]",
        "Name: ${c.display_name}",
        "Company: ${c.company}",
        "Title: ${c.title}",
        "Email: ${c.email}",
        "Phone: ${c.phone}",
        "Mobile: ${c.mobile}",
        "Website: ${c.website}",
        "Address: ${formatAddress(c).replace("\n", ", ")}",
        "Tags: ${c.tags}",
        "Identity: ${ContactsRepository.normalizeFingerprint(c.subject_fingerprint)}",
        "[/DNA-NEXUS-CONTACT]"
    ).filterNot { it.endsWith(": ") }.joinToString("\n")
