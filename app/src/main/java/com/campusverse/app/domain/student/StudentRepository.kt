package com.campusverse.app.domain.student

import com.campusverse.app.data.model.AcademicSummary
import com.campusverse.app.data.model.AiStudyQueryResponse
import com.campusverse.app.data.model.CampusEvent
import com.campusverse.app.data.model.CommunityCommentItem
import com.campusverse.app.data.model.CommunityItem
import com.campusverse.app.data.model.CommunityPostItem
import com.campusverse.app.data.model.CourseItem
import com.campusverse.app.data.model.LibraryResource
import com.campusverse.app.data.model.MarketplaceProduct
import com.campusverse.app.data.model.NoteItem
import com.campusverse.app.data.model.StudentProfileData

interface StudentRepository {
    // 1. Academics
    suspend fun getAcademicSummary(): Result<AcademicSummary>
    suspend fun getCourses(semester: Int? = null, search: String? = null): Result<List<CourseItem>>

    // 2. Notes Hub
    suspend fun getNotes(search: String? = null, courseId: String? = null, tag: String? = null): Result<List<NoteItem>>
    suspend fun getMyNotes(): Result<List<NoteItem>>
    suspend fun getNoteById(id: String): Result<NoteItem>
    suspend fun createNote(title: String, description: String?, fileUrl: String, courseId: String?, tags: String?): Result<NoteItem>
    suspend fun updateNote(id: String, title: String?, description: String?, tags: String?): Result<NoteItem>
    suspend fun deleteNote(id: String): Result<Boolean>
    suspend fun requestNoteRemoval(noteId: String, reason: String): Result<NoteItem>
    suspend fun reportNote(noteId: String, reason: String): Result<Boolean>

    // 3. Library
    suspend fun getLibraryResources(search: String? = null, category: String? = null): Result<List<LibraryResource>>
    suspend fun getLibraryResourceById(id: String): Result<LibraryResource>

    // 4. AI Study Assistant
    suspend fun askAiStudyAssistant(query: String, mode: String = "EXPLAIN", topic: String? = null): Result<AiStudyQueryResponse>

    // 5. Events
    suspend fun getEvents(search: String? = null, category: String? = null): Result<List<CampusEvent>>
    suspend fun getEventById(id: String): Result<CampusEvent>
    suspend fun registerForEvent(eventId: String): Result<Boolean>
    suspend fun unregisterFromEvent(eventId: String): Result<Boolean>

    // 6. Community & Discussions
    suspend fun getCommunities(search: String? = null): Result<List<CommunityItem>>
    suspend fun getCommunityById(id: String): Result<Pair<CommunityItem, List<CommunityPostItem>>>
    suspend fun joinCommunity(communityId: String): Result<Boolean>
    suspend fun leaveCommunity(communityId: String): Result<Boolean>
    suspend fun createCommunityPost(communityId: String, title: String, content: String): Result<CommunityPostItem>
    suspend fun updateCommunityPost(postId: String, title: String?, content: String?): Result<CommunityPostItem>
    suspend fun deleteCommunityPost(postId: String): Result<Boolean>
    suspend fun likeCommunityPost(postId: String): Result<Int>
    suspend fun createComment(postId: String, content: String): Result<CommunityCommentItem>
    suspend fun reportContent(targetType: String, targetId: String, reason: String): Result<Boolean>

    // 7. Marketplace
    suspend fun getMarketplaceListings(search: String? = null, category: String? = null, condition: String? = null): Result<List<MarketplaceProduct>>
    suspend fun getMarketplaceProductById(id: String): Result<MarketplaceProduct>
    suspend fun createMarketplaceListing(title: String, description: String, price: Double, category: String, condition: String): Result<MarketplaceProduct>
    suspend fun updateMarketplaceListing(id: String, price: Double?, status: String?): Result<MarketplaceProduct>
    suspend fun deleteMarketplaceListing(id: String): Result<Boolean>

    // 8. Student Profile
    suspend fun getStudentProfile(): Result<StudentProfileData>
    suspend fun updateStudentProfile(data: StudentProfileData): Result<StudentProfileData>
}
