package com.campusverse.app

import com.campusverse.app.data.model.AlumniProfileData
import com.campusverse.app.data.model.AspirantProfileData
import com.campusverse.app.data.model.StudentProfileData
import com.campusverse.app.data.repository.NetworkAlumniRepository
import com.campusverse.app.data.repository.NetworkAspirantRepository
import com.campusverse.app.data.repository.NetworkStudentRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileSyncTest {

    @Test
    fun testStudentProfile_updateAndHomeSync() = runBlocking {
        val repo = NetworkStudentRepository()
        
        // 1. Get initial profile
        val initialProfile = repo.getStudentProfile().getOrThrow()
        
        // 2. Update Student Profile fields
        val updatedProfile = initialProfile.copy(
            fullName = "John Doe",
            degree = "B.S. Computer Science",
            branch = "Software Engineering",
            semester = 8,
            cgpa = 9.5,
            university = "Stanford University",
            avatarUrl = "https://example.com/avatar.png"
        )
        val updateResult = repo.updateStudentProfile(updatedProfile)
        assertTrue(updateResult.isSuccess)
        
        // 3. Verify getStudentProfile returns updated profile
        val fetchedProfile = repo.getStudentProfile().getOrThrow()
        assertEquals("John Doe", fetchedProfile.fullName)
        assertEquals("B.S. Computer Science", fetchedProfile.degree)
        assertEquals("Software Engineering", fetchedProfile.branch)
        assertEquals(8, fetchedProfile.semester)
        assertEquals(9.5, fetchedProfile.cgpa, 0.001)
        
        // 4. Verify getAcademicSummary syncs updated fields
        val academicSummary = repo.getAcademicSummary().getOrThrow()
        assertEquals("B.S. Computer Science", academicSummary.degree)
        assertEquals("Software Engineering", academicSummary.major)
        assertEquals(8, academicSummary.semester)
        assertEquals(9.5, academicSummary.cgpa, 0.001)
    }

    @Test
    fun testAspirantProfile_updateAndHomeSync() = runBlocking {
        val repo = NetworkAspirantRepository()
        
        // 1. Get initial profile
        val initialProfile = repo.getAspirantProfile().getOrThrow()
        
        // 2. Update Aspirant Profile fields
        val updatedProfile = initialProfile.copy(
            fullName = "Alice Aspirant",
            targetDegree = "M.Tech",
            targetMajor = "Data Science",
            highSchool = "Greenwood High",
            expectedGradYear = 2028,
            avatarUrl = "https://example.com/alice.jpg"
        )
        val updateResult = repo.updateAspirantProfile(updatedProfile)
        assertTrue(updateResult.isSuccess)
        
        // 3. Verify getAspirantProfile returns updated profile
        val fetchedProfile = repo.getAspirantProfile().getOrThrow()
        assertEquals("Alice Aspirant", fetchedProfile.fullName)
        assertEquals("M.Tech", fetchedProfile.targetDegree)
        assertEquals("Data Science", fetchedProfile.targetMajor)
        assertEquals("Greenwood High", fetchedProfile.highSchool)
        assertEquals(2028, fetchedProfile.expectedGradYear)
        
        // 4. Verify getAspirantHomeSummary returns updated profile
        val homeSummary = repo.getAspirantHomeSummary().getOrThrow()
        assertEquals("Alice Aspirant", homeSummary.profile.fullName)
        assertEquals("M.Tech", homeSummary.profile.targetDegree)
        assertEquals("Data Science", homeSummary.profile.targetMajor)
    }

    @Test
    fun testAlumniProfile_updateAndHomeSync() = runBlocking {
        val repo = NetworkAlumniRepository()
        
        // 1. Get initial profile
        val initialProfile = repo.getMyProfile().getOrThrow()
        
        // 2. Update Alumni Profile fields
        val updatedProfile = initialProfile.copy(
            fullName = "Robert Alum",
            company = "Google Inc.",
            designation = "Staff Software Engineer",
            degree = "M.S. Computer Science",
            graduationYear = 2019,
            institution = "MIT",
            avatarUrl = "https://example.com/robert.jpg"
        )
        val updateResult = repo.updateAlumniProfile(updatedProfile)
        assertTrue(updateResult.isSuccess)
        
        // 3. Verify getMyProfile returns updated profile
        val fetchedProfile = repo.getMyProfile().getOrThrow()
        assertEquals("Robert Alum", fetchedProfile.fullName)
        assertEquals("Google Inc.", fetchedProfile.company)
        assertEquals("Staff Software Engineer", fetchedProfile.designation)
        assertEquals("M.S. Computer Science", fetchedProfile.degree)
        assertEquals(2019, fetchedProfile.graduationYear)
        assertEquals("MIT", fetchedProfile.institution)
        
        // 4. Verify getAlumniHomeSummary returns updated profile
        val homeSummary = repo.getAlumniHomeSummary().getOrThrow()
        assertEquals("Robert Alum", homeSummary.profile.fullName)
        assertEquals("Google Inc.", homeSummary.profile.company)
        assertEquals("Staff Software Engineer", homeSummary.profile.designation)
    }

    @Test
    fun testStudentGlobalDataSync_acrossModules() = runBlocking {
        val repo = NetworkStudentRepository()

        // 1. Update Student Profile name to a custom test name
        val initialProfile = repo.getStudentProfile().getOrThrow()
        val updated = initialProfile.copy(fullName = "Rohan Test")
        repo.updateStudentProfile(updated)

        // 2. Creating a Note should reflect the updated author name
        val note = repo.createNote(
            title = "Distributed Systems Cheatsheet",
            description = "Summary of Consensus",
            fileUrl = "https://example.com/note.pdf",
            courseId = "cs301",
            tags = "cse,exam"
        ).getOrThrow()
        assertEquals("Rohan Test", note.authorName)

        // 3. Creating a Community Post should reflect the updated author name
        val post = repo.createCommunityPost(
            communityId = "comm_1",
            title = "Study Group",
            content = "Looking for teammates"
        ).getOrThrow()
        assertEquals("Rohan Test", post.authorName)

        // 4. Creating a Comment should reflect the updated author name
        val comment = repo.createComment(
            postId = "post_1",
            content = "Count me in!"
        ).getOrThrow()
        assertEquals("Rohan Test", comment.authorName)

        // 5. Creating a Marketplace Listing should reflect the updated seller name
        val product = repo.createMarketplaceListing(
            title = "Scientific Calculator",
            description = "Casio FX-991EX",
            price = 40.0,
            category = "ELECTRONICS",
            condition = "LIKE_NEW"
        ).getOrThrow()
        assertEquals("Rohan Test", product.sellerName)
    }
}
