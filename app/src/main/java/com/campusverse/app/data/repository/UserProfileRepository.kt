package com.campusverse.app.data.repository

import com.campusverse.app.data.model.AlumniProfileData
import com.campusverse.app.data.model.AspirantProfileData
import com.campusverse.app.data.model.StudentProfileData
import com.campusverse.app.data.model.UserRole
import com.campusverse.app.data.preferences.ModulePreferencesManager
import com.campusverse.app.domain.alumni.AlumniRepository
import com.campusverse.app.domain.aspirant.AspirantRepository
import com.campusverse.app.domain.session.InMemorySessionManager
import com.campusverse.app.domain.session.SessionManager
import com.campusverse.app.domain.student.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Unified representation of the currently logged-in user's display profile.
 * Acts as the single source of truth across all modules and navigation destinations.
 */
data class CurrentUserProfile(
    val userId: String,
    val name: String,
    val email: String,
    val photoUrl: String? = null,
    val role: UserRole,
    val profileData: Any? = null
)

/**
 * Unified observable repository for logged-in user profile state.
 * Synchronizes with role-specific repositories, session storage, and module preferences.
 */
class UserProfileRepository(
    private val sessionManager: SessionManager = InMemorySessionManager(),
    private val studentRepository: StudentRepository = NetworkStudentRepository.instance,
    private val alumniRepository: AlumniRepository = NetworkAlumniRepository.instance,
    private val aspirantRepository: AspirantRepository = NetworkAspirantRepository.instance,
    private val modulePreferencesManager: ModulePreferencesManager? = null
) {
    companion object {
        @Volatile
        var instance: UserProfileRepository = UserProfileRepository()
    }

    private val mutex = Mutex()
    private val _profile = MutableStateFlow<CurrentUserProfile?>(null)
    val profile: StateFlow<CurrentUserProfile?> = _profile.asStateFlow()

    suspend fun loadProfile(userId: String? = null, preferredRole: UserRole? = null): CurrentUserProfile? {
        val session = sessionManager.getSession()
        val resolvedUserId = userId?.takeIf { it.isNotBlank() } ?: session?.user?.userId ?: "current_user"
        val resolvedRole = preferredRole ?: session?.user?.role ?: UserRole.STUDENT
        val resolvedEmail = session?.user?.email ?: "${resolvedRole.name.lowercase()}@campusverse.edu"
        val sessionName = session?.user?.name ?: "CampusVerse User"

        return mutex.withLock {
            val loadedProfile = when (resolvedRole) {
                UserRole.STUDENT -> {
                    val result = studentRepository.getStudentProfile()
                    val p = result.getOrNull()
                    val studentName = p?.fullName?.takeIf { it.isNotBlank() } ?: sessionName
                    val studentEmail = p?.email?.takeIf { it.isNotBlank() } ?: resolvedEmail
                    CurrentUserProfile(
                        userId = resolvedUserId,
                        name = studentName,
                        email = studentEmail,
                        photoUrl = p?.avatarUrl,
                        role = UserRole.STUDENT,
                        profileData = p
                    )
                }
                UserRole.ALUMNI -> {
                    val result = alumniRepository.getMyProfile()
                    val p = result.getOrNull()
                    val alumniName = p?.fullName?.takeIf { it.isNotBlank() } ?: sessionName
                    val alumniEmail = p?.email?.takeIf { it.isNotBlank() } ?: resolvedEmail
                    CurrentUserProfile(
                        userId = resolvedUserId,
                        name = alumniName,
                        email = alumniEmail,
                        photoUrl = p?.avatarUrl,
                        role = UserRole.ALUMNI,
                        profileData = p
                    )
                }
                UserRole.ASPIRANT -> {
                    val result = aspirantRepository.getAspirantProfile()
                    val p = result.getOrNull()
                    val aspirantName = p?.fullName?.takeIf { it.isNotBlank() } ?: sessionName
                    val aspirantEmail = p?.email?.takeIf { it.isNotBlank() } ?: resolvedEmail
                    CurrentUserProfile(
                        userId = resolvedUserId,
                        name = aspirantName,
                        email = aspirantEmail,
                        photoUrl = p?.avatarUrl,
                        role = UserRole.ASPIRANT,
                        profileData = p
                    )
                }
                UserRole.ADMIN -> {
                    CurrentUserProfile(
                        userId = resolvedUserId,
                        name = sessionName,
                        email = resolvedEmail,
                        photoUrl = null,
                        role = UserRole.ADMIN,
                        profileData = null
                    )
                }
            }

            _profile.value = loadedProfile
            loadedProfile
        }
    }

    suspend fun updateProfile(updated: CurrentUserProfile): Result<CurrentUserProfile> {
        return mutex.withLock {
            try {
                when (updated.role) {
                    UserRole.STUDENT -> {
                        val studentData = updated.profileData as? StudentProfileData
                            ?: StudentProfileData(
                                userId = updated.userId,
                                fullName = updated.name,
                                email = updated.email,
                                avatarUrl = updated.photoUrl
                            )
                        val saveResult = studentRepository.updateStudentProfile(studentData)
                        if (saveResult.isFailure) {
                            return@withLock Result.failure(saveResult.exceptionOrNull() ?: Exception("Failed to update student profile"))
                        }
                        // Synchronize with ModulePreferencesManager
                        modulePreferencesManager?.updateAcademicPreferences(
                            userId = updated.userId,
                            degree = studentData.degree,
                            semester = studentData.semester,
                            branch = studentData.branch,
                            recentGpa = studentData.cgpa
                        )
                    }
                    UserRole.ALUMNI -> {
                        val alumniData = updated.profileData as? AlumniProfileData
                            ?: AlumniProfileData(
                                userId = updated.userId,
                                fullName = updated.name,
                                email = updated.email,
                                avatarUrl = updated.photoUrl
                            )
                        val saveResult = alumniRepository.updateAlumniProfile(alumniData)
                        if (saveResult.isFailure) {
                            return@withLock Result.failure(saveResult.exceptionOrNull() ?: Exception("Failed to update alumni profile"))
                        }
                    }
                    UserRole.ASPIRANT -> {
                        val aspirantData = updated.profileData as? AspirantProfileData
                            ?: AspirantProfileData(
                                userId = updated.userId,
                                fullName = updated.name,
                                email = updated.email,
                                avatarUrl = updated.photoUrl
                            )
                        val saveResult = aspirantRepository.updateAspirantProfile(aspirantData)
                        if (saveResult.isFailure) {
                            return@withLock Result.failure(saveResult.exceptionOrNull() ?: Exception("Failed to update aspirant profile"))
                        }
                    }
                    UserRole.ADMIN -> {
                        // Admin has no mutable remote profile document
                    }
                }

                // Synchronize persistent session name, email, and photo
                sessionManager.updateUserData(name = updated.name, email = updated.email)
                sessionManager.updatePhoto(photoUrl = updated.photoUrl)

                _profile.value = updated
                Result.success(updated)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun clear() {
        _profile.value = null
    }
}
