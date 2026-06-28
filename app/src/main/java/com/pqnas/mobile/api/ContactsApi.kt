package com.pqnas.mobile.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

data class ContactDto(
    val subject_fingerprint: String = "",
    val subject_kind: String = "manual_contact",
    val contact_type: String = "person",
    val display_name: String = "",
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
    val postal_code: String = "",
    val city: String = "",
    val country: String = "",
    val delivery_name: String = "",
    val delivery_street: String = "",
    val delivery_postal_code: String = "",
    val delivery_city: String = "",
    val delivery_country: String = "",
    val notes: String = ""
)

data class ContactUpsertRequest(
    val subject_fingerprint: String,
    val subject_kind: String = "manual_contact",
    val contact_type: String = "person",
    val display_name: String = "",
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
    val postal_code: String = "",
    val city: String = "",
    val country: String = "",
    val delivery_name: String = "",
    val delivery_street: String = "",
    val delivery_postal_code: String = "",
    val delivery_city: String = "",
    val delivery_country: String = "",
    val notes: String = ""
)

data class ContactsListResponse(
    val ok: Boolean = false,
    val contacts: List<ContactDto> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

data class ContactUpsertResponse(
    val ok: Boolean = false,
    val contact: ContactDto? = null,
    val error: String? = null,
    val message: String? = null
)

data class ContactDeleteRequest(
    val subject_fingerprint: String
)

data class ContactDeleteResponse(
    val ok: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

data class ContactLocalUserDto(
    val fingerprint: String = "",
    val subject_fingerprint: String = "",
    val display_name: String = "",
    val name: String = "",
    val role: String = "",
    val email: String = ""
)

data class ContactLocalUsersResponse(
    val ok: Boolean = false,
    val candidates: List<ContactLocalUserDto> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

interface ContactsApi {
    @GET("/api/v4/people/list")
    suspend fun listContacts(): ContactsListResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/people/upsert")
    suspend fun upsertContact(
        @Body request: ContactUpsertRequest
    ): ContactUpsertResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/people/delete")
    suspend fun deleteContact(
        @Body request: ContactDeleteRequest
    ): ContactDeleteResponse

    @GET("/api/v4/people/local-users")
    suspend fun listLocalUsers(): ContactLocalUsersResponse
}
