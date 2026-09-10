package com.campusverse.app.ui.screens.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.campusverse.app.data.model.UserRole
import com.campusverse.app.data.repository.AuthRepositoryImpl
import com.campusverse.app.domain.auth.AuthRepository
import com.campusverse.app.navigation.Screen
import kotlinx.coroutines.delay

/**
 * Splash screen performing automatic session restoration on app startup.
 * Routes to the authenticated user's stored role Home, or Welcome if unauthenticated.
 */
@Composable
fun SplashScreen(
    onNavigateToDestination: (String) -> Unit,
    modifier: Modifier = Modifier,
    authRepository: AuthRepository = AuthRepositoryImpl.instance
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
        val startTime = System.currentTimeMillis()
        val sessionResult = authRepository.restoreSession()
        val elapsedTime = System.currentTimeMillis() - startTime
        val remainingDelay = (800L - elapsedTime).coerceAtLeast(0L)
        delay(remainingDelay)

        sessionResult.fold(
            onSuccess = { user ->
                if (user != null) {
                    val destination = when (user.role) {
                        UserRole.ASPIRANT -> Screen.AspirantHome.route
                        UserRole.STUDENT  -> Screen.StudentHome.route
                        UserRole.ALUMNI   -> Screen.AlumniHome.route
                        UserRole.ADMIN    -> if (user.isAdminAuthorized) {
                            Screen.AdminDashboard.route
                        } else {
                            Screen.AdminLogin.route
                        }
                    }
                    onNavigateToDestination(destination)
                } else {
                    onNavigateToDestination(Screen.Welcome.route)
                }
            },
            onFailure = {
                onNavigateToDestination(Screen.Welcome.route)
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                )
            )
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(800)) + slideInVertically(
                animationSpec = tween(800),
                initialOffsetY = { 30 }
            ),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(y = (-32).dp)
                    .padding(horizontal = 24.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(120.dp)
                ) {
                    // Soft ambient glow
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                shape = CircleShape
                            )
                    )
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.School,
                                contentDescription = "CampusVerse Logo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "CampusVerse",
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your Academic & Professional Universe",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 280.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                strokeWidth = 3.dp
            )
        }
    }
}
