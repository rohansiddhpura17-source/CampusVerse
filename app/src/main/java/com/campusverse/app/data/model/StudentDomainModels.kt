package com.campusverse.app.data.model

import java.io.Serializable

data class CourseItem(
    val id: String,
    val code: String,
    val name: String,
    val credits: Int,
    val department: String? = null
) : Serializable

data class AcademicSummary(
    val institutionName: String,
    val degree: String,
    val major: String,
    val semester: Int,
    val cgpa: Double,
    val studentIdNumber: String,
    val courses: List<CourseItem> = emptyList(),
    val totalCredits: Int = courses.sumOf { it.credits }
) : Serializable

data class NoteItem(
    val id: String,
    val userId: String,
    val authorName: String,
    val courseCode: String? = null,
    val courseName: String? = null,
    val title: String,
    val description: String? = null,
    val fileUrl: String,
    val tags: List<String> = emptyList(),
    val downloadsCount: Int = 0,
    val createdAt: String? = null,
    val isOwnedByCurrentUser: Boolean = false,
    val status: String = "PUBLISHED",
    val rejectionReason: String? = null,
    val removalReason: String? = null,
    val reviewedBy: String? = null,
    val reviewedAt: String? = null,
    val removedBy: String? = null,
    val removedAt: String? = null
) : Serializable

enum class ResourceCategory {
    ALL, PDF, EBOOK, VIDEO, LINK
}

data class LibraryResource(
    val id: String,
    val title: String,
    val author: String,
    val isbn: String? = null,
    val category: String, // PDF, EBOOK, VIDEO, LINK
    val location: String? = null,
    val totalCopies: Int = 1,
    val availableCopies: Int = 1,
    val fileUrl: String = "https://docs.campusverse.edu/library/sample_resource.pdf"
) : Serializable

data class CampusEvent(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val location: String,
    val isOnline: Boolean = false,
    val meetingUrl: String? = null,
    val startTime: String,
    val endTime: String? = null,
    val capacity: Int = 100,
    val registeredCount: Int = 0,
    val isRegistered: Boolean = false
) : Serializable

data class CommunityItem(
    val id: String,
    val name: String,
    val description: String,
    val coverImage: String? = null,
    val memberCount: Int = 0,
    val postsCount: Int = 0,
    val isMember: Boolean = false
) : Serializable

data class CommunityCommentItem(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String? = null,
    val content: String,
    val createdAt: String? = null
) : Serializable

data class CommunityPostItem(
    val id: String,
    val communityId: String,
    val authorId: String,
    val authorName: String,
    val authorRole: String = "STUDENT",
    val authorAvatar: String? = null,
    val title: String,
    val content: String,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val createdAt: String? = null,
    val comments: List<CommunityCommentItem> = emptyList(),
    val isOwnedByCurrentUser: Boolean = false
) : Serializable

data class MarketplaceProduct(
    val id: String,
    val sellerId: String,
    val sellerName: String,
    val sellerRole: String = "STUDENT",
    val sellerPhone: String? = null,
    val title: String,
    val description: String,
    val price: Double,
    val category: String, // TEXTBOOK, ELECTRONICS, NOTES, FURNITURE, OTHER
    val condition: String, // NEW, LIKE_NEW, GOOD, FAIR
    val images: List<String> = emptyList(),
    val status: String = "AVAILABLE", // AVAILABLE, RESERVED, SOLD
    val createdAt: String? = null,
    val isOwnedByCurrentUser: Boolean = false
) : Serializable

data class AiStudyQueryResponse(
    val available: Boolean,
    val query: String,
    val mode: String,
    val message: String? = null,
    val response: String? = null,
    val suggestedTopics: List<String> = emptyList(),
    val timestamp: String? = null
) : Serializable

data class StudentProfileData(
    val userId: String,
    val fullName: String,
    val email: String,
    val role: String = "STUDENT",
    val bio: String? = null,
    val avatarUrl: String? = null,
    val location: String? = null,
    val phone: String? = null,
    val university: String = "National Institute of Technology",
    val degree: String = "B.Tech",
    val branch: String = "Computer Science and Engineering",
    val semester: Int = 6,
    val cgpa: Double = 8.75,
    val graduationYear: Int = 2026,
    val skills: List<String> = emptyList()
) : Serializable
