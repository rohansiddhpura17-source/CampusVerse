package com.campusverse.app.data.model

/**
 * Represents the distinct user roles in CampusVerse.
 */
enum class UserRole(
    val title: String,
    val subtitle: String,
    val description: String
) {
    ASPIRANT(
        title = "Aspirant",
        subtitle = "Prospective Student",
        description = "Explore campus life, ask questions to current students, and discover programs."
    ),
    STUDENT(
        title = "Student",
        subtitle = "Current Enrolled Student",
        description = "Access student community, campus marketplace, study groups, and campus events."
    ),
    ALUMNI(
        title = "Alumni",
        subtitle = "Graduated Member",
        description = "Mentor students, network with fellow alumni, and stay connected with the campus."
    ),
    ADMIN(
        title = "Admin",
        subtitle = "Campus / System Administrator",
        description = "Manage campus operations, review verifications, and supervise community guidelines."
    )
}
