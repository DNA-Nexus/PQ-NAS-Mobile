package com.pqnas.mobile.contacts

import com.pqnas.mobile.api.ContactDeleteRequest
import com.pqnas.mobile.api.ContactDto
import com.pqnas.mobile.api.ContactLocalUserDto
import com.pqnas.mobile.api.ContactUpsertRequest
import com.pqnas.mobile.api.ContactsApi
import retrofit2.HttpException
import java.security.SecureRandom
import java.util.Locale

class ContactsRepository(
    private val api: ContactsApi
) {
    suspend fun listContacts(): List<ContactDto> {
        return try {
            val r = api.listContacts()
            if (!r.ok) throw IllegalStateException(r.message ?: r.error ?: "Could not load contacts.")
            r.contacts
        } catch (e: HttpException) {
            throw IllegalStateException("Contacts load failed: HTTP ${e.code()}")
        }
    }

    suspend fun upsertContact(request: ContactUpsertRequest): ContactDto {
        return try {
            val r = api.upsertContact(request)
            if (!r.ok) throw IllegalStateException(r.message ?: r.error ?: "Could not save contact.")
            r.contact ?: request.toContactDto()
        } catch (e: HttpException) {
            throw IllegalStateException("Contact save failed: HTTP ${e.code()}")
        }
    }

    suspend fun deleteContact(subjectFingerprint: String) {
        try {
            val r = api.deleteContact(ContactDeleteRequest(subjectFingerprint))
            if (!r.ok) throw IllegalStateException(r.message ?: r.error ?: "Could not delete contact.")
        } catch (e: HttpException) {
            throw IllegalStateException("Contact delete failed: HTTP ${e.code()}")
        }
    }

    suspend fun listLocalUsers(): List<ContactLocalUserDto> {
        return try {
            val r = api.listLocalUsers()
            if (!r.ok) emptyList() else r.candidates
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun allocateManualAnchor(existingFingerprints: Collection<String>): String {
        val existing = existingFingerprints.map { normalizeFingerprint(it) }.toSet()

        repeat(12) {
            val candidate = randomHex(32)
            if (candidate.isNotBlank() && !existing.contains(candidate)) return candidate
        }

        return randomHex(32)
    }

    companion object {
        fun normalizeFingerprint(value: String?): String =
            value.orEmpty()
                .trim()
                .replace(Regex("[\\s:-]+"), "")
                .lowercase(Locale.US)

        fun normalizeEmail(value: String?): String =
            value.orEmpty().trim().lowercase(Locale.US)

        fun normalizePhone(value: String?): String =
            value.orEmpty().filter { it.isDigit() || it == '+' }

        fun normalizeNameKey(value: String?): String =
            value.orEmpty()
                .trim()
                .lowercase(Locale.getDefault())
                .replace(Regex("\\s+"), " ")

        fun shortFingerprint(value: String?): String {
            val s = normalizeFingerprint(value)
            if (s.length <= 18) return s
            return "${s.take(10)}…${s.takeLast(6)}"
        }

        private fun randomHex(bytes: Int): String {
            val data = ByteArray(bytes)
            SecureRandom().nextBytes(data)
            return data.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
    }
}

fun ContactUpsertRequest.toContactDto(): ContactDto =
    ContactDto(
        subject_fingerprint = subject_fingerprint,
        subject_kind = subject_kind,
        contact_type = contact_type,
        display_name = display_name,
        company = company,
        title = title,
        nickname = nickname,
        status = status,
        tags = tags,
        email = email,
        phone = phone,
        mobile = mobile,
        website = website,
        street = street,
        postal_code = postal_code,
        city = city,
        country = country,
        delivery_name = delivery_name,
        delivery_street = delivery_street,
        delivery_postal_code = delivery_postal_code,
        delivery_city = delivery_city,
        delivery_country = delivery_country,
        notes = notes
    )
