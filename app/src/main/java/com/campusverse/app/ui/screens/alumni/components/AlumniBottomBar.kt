package com.campusverse.app.ui.screens.alumni.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.campusverse.app.navigation.Screen
import androidx.compose.material3.MaterialTheme

enum class AlumniNavTab(val label: String, val icon: ImageVector, val route: String) {
    HOME("Home", Icons.Default.Home, Screen.AlumniHome.route),
    NETWORK("Network", Icons.Default.People, Screen.AlumniNetwork.route),
    CAREERS("Careers", Icons.Default.BusinessCenter, Screen.AlumniCareers.route),
    MENTORSHIP("Mentorship", Icons.Default.School, Screen.AlumniMentorship.route),
    PROFILE("Profile", Icons.Default.Person, Screen.AlumniProfile.route)
}

@Composable
fun AlumniBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        AlumniNavTab.entries.forEach { tab ->
            val isSelected = currentRoute == tab.route || currentRoute.startsWith(tab.route.substringBefore("/"))
            NavigationBarItem(
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        onNavigate(tab.route)
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            )
        }
    }
}
