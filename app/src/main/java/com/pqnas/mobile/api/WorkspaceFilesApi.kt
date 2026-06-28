package com.pqnas.mobile.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Streaming


// PQNAS_ANDROID_WORKSPACE_MESSAGES_LINKS_V1: workspace message board DTOs used by the Android File Manager.
data class WorkspaceMessageAttachmentRequest(
    val path: String = "",
    val type: String = "file",
    val label: String = ""
)

data class WorkspaceMessageAttachmentDto(
    val path: String = "",
    val type: String = "",
    val label: String = "",
    val name: String = ""
)

data class WorkspaceMessageDto(
    val id: Long = 0L,
    val workspace_id: String = "",
    val author_name: String = "",
    val body: String = "",
    val attachments: List<WorkspaceMessageAttachmentDto> = emptyList(),
    val created_at_epoch: Long = 0L,
    val created_at: String = "",
    val is_own: Boolean = false,
    val can_delete: Boolean = false,
    val can_mute_author: Boolean = false,
    val author_muted: Boolean = false
)

data class WorkspaceMessagesListResponse(
    val ok: Boolean = false,
    val workspace_id: String = "",
    val can_moderate_messages: Boolean = false,
    val actor_muted: Boolean = false,
    val message_board_muted_all: Boolean = false,
    val workspace_message_mute_count: Long = 0L,
    val messages: List<WorkspaceMessageDto> = emptyList(),
    val latest_id: Long = 0L,
    val last_seen_id: Long = 0L,
    val unread_count: Long = 0L,
    val error: String? = null,
    val message: String? = null
)

data class WorkspaceMessagePostRequest(
    val workspace_id: String,
    val body: String,
    val attachments: List<WorkspaceMessageAttachmentRequest> = emptyList()
)

data class WorkspaceMessagePostResponse(
    val ok: Boolean = false,
    val workspace_id: String = "",
    val latest_id: Long = 0L,
    val message: WorkspaceMessageDto? = null,
    val error: String? = null
)

data class WorkspaceMessageDeleteRequest(
    val workspace_id: String,
    val message_id: Long
)

data class WorkspaceMessageDeleteResponse(
    val ok: Boolean = false,
    val workspace_id: String = "",
    val message_id: Long = 0L,
    val error: String? = null,
    val message: String? = null
)

data class WorkspaceMessageMuteRequest(
    val workspace_id: String,
    val message_id: Long = 0L,
    val target_all: Boolean = false,
    val muted: Boolean = true,
    val reason: String = ""
)

data class WorkspaceMessageMuteResponse(
    val ok: Boolean = false,
    val workspace_id: String = "",
    val muted: Boolean = false,
    val target_all: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

interface WorkspaceFilesApi {
    @GET("/api/v4/workspaces")
    suspend fun listWorkspaces(): WorkspacesResponse

    // PQNAS_ANDROID_WORKSPACE_MESSAGES_LINKS_V1: workspace message board routes.
    @GET("/api/v4/workspaces/messages")
    suspend fun listWorkspaceMessages(
        @Query("workspace_id") workspaceId: String,
        @Query("after_id") afterId: Long = 0L,
        @Query("limit") limit: Int = 100
    ): WorkspaceMessagesListResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/workspaces/messages/post")
    suspend fun postWorkspaceMessage(
        @Body request: WorkspaceMessagePostRequest
    ): WorkspaceMessagePostResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/workspaces/messages/delete")
    suspend fun deleteWorkspaceMessage(
        @Body request: WorkspaceMessageDeleteRequest
    ): WorkspaceMessageDeleteResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/workspaces/messages/mute")
    suspend fun muteWorkspaceMessage(
        @Body request: WorkspaceMessageMuteRequest
    ): WorkspaceMessageMuteResponse

    @GET("/api/v4/workspaces/files/list")
    suspend fun listWorkspaceFiles(
        @Query("workspace_id") workspaceId: String,
        @Query("path") path: String? = null
    ): FilesListResponse

    @Streaming
    @GET("/api/v4/workspaces/files/get")
    suspend fun downloadWorkspaceFile(
        @Query("workspace_id") workspaceId: String,
        @Query("path") path: String
    ): ResponseBody

    @GET("/api/v4/workspaces/files/read_text")
    suspend fun readWorkspaceTextFile(
        @Query("workspace_id") workspaceId: String,
        @Query("path") path: String,
        @Query("session_id") sessionId: String
    ): ReadTextResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/workspaces/files/write_text")
    suspend fun writeWorkspaceTextFile(
        @Body request: WorkspaceWriteTextRequest
    ): WriteTextResponse

    @POST("/api/v4/workspaces/files/delete")
    suspend fun deleteWorkspaceFile(
        @Query("workspace_id") workspaceId: String,
        @Query("path") path: String
    ): DeleteFileResponse

    @POST("/api/v4/workspaces/files/move")
    suspend fun moveWorkspaceFile(
        @Query("workspace_id") workspaceId: String,
        @Query("from") from: String,
        @Query("to") to: String
    ): MoveFileResponse

    @POST("/api/v4/workspaces/files/copy")
    suspend fun copyWorkspaceFile(
        @Query("workspace_id") workspaceId: String,
        @Query("from") from: String,
        @Query("to") to: String
    ): MoveFileResponse

    @POST("/api/v4/workspaces/files/mkdir")
    suspend fun mkdirWorkspace(
        @Query("workspace_id") workspaceId: String,
        @Query("path") path: String
    ): MkdirResponse


    @PUT("/api/v4/workspaces/files/put")
    suspend fun uploadWorkspaceFile(
        @Query("workspace_id") workspaceId: String,
        @Query("path") path: String,
        @Query("overwrite") overwrite: Int = 0,
        @Body body: RequestBody
    ): UploadFileResponse

    @GET("/api/v4/shares/list")
    suspend fun listWorkspaceShares(
        @Query("workspace_id") workspaceId: String
    ): SharesListResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/shares/create")
    suspend fun createWorkspaceShare(
        @Body request: WorkspaceCreateShareRequest
    ): CreateShareResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/workspaces/files/edit_lease/acquire")
    suspend fun acquireWorkspaceEditLease(
        @Body request: WorkspaceEditLeaseRequest
    ): WorkspaceEditLeaseResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/workspaces/files/edit_lease/refresh")
    suspend fun refreshWorkspaceEditLease(
        @Body request: WorkspaceEditLeaseRequest
    ): WorkspaceEditLeaseResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/workspaces/files/edit_lease/release")
    suspend fun releaseWorkspaceEditLease(
        @Body request: WorkspaceEditLeaseReleaseRequest
    ): WorkspaceEditLeaseReleaseResponse


    @GET("/api/v4/workspaces/files/versions/read_text")
    suspend fun readWorkspaceFileVersionText(
        @Query("workspace_id") workspaceId: String,
        @Query("path") path: String,
        @Query("version_id") versionId: String
    ): FileVersionTextResponse

    @GET("/api/v4/workspaces/files/versions/list")
    suspend fun listWorkspaceFileVersions(
        @Query("workspace_id") workspaceId: String,
        @Query("path") path: String
    ): FileVersionsListResponse


    @Headers("Content-Type: application/json")
    @POST("/api/v4/workspaces/files/versions/flag")
    suspend fun flagWorkspaceFileVersion(
        @Body request: WorkspaceVersionFlagRequest
    ): VersionFlagResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/workspaces/files/versions/unflag")
    suspend fun unflagWorkspaceFileVersion(
        @Body request: WorkspaceVersionFlagRequest
    ): VersionFlagResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/workspaces/files/restore_version")
    suspend fun restoreWorkspaceFileVersion(
        @Body request: WorkspaceRestoreVersionRequest
    ): RestoreVersionResponse
}