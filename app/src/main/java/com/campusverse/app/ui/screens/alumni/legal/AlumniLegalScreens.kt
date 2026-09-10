package com.campusverse.app.ui.screens.alumni.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportScreen(onBack: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    LegalPageScaffold(title = "Help & Support", onBack = onBack) {
        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Need Immediate Assistance?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Our dedicated Alumni Relations & Support Team is available Monday through Friday to assist with career services, recruitment verification, and account settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Button(
                    onClick = {
                        try {
                            uriHandler.openUri("mailto:alumni-support@campusverse.edu")
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Email Support (alumni-support@campusverse.edu)", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        LegalSection(
            heading = "Alumni Network & Career Inquiries",
            body = "For queries regarding mentorship matching, company recruitment partnerships, or referral approvals, please reach out directly to the Campus Relations team at alumni-support@campusverse.edu."
        )
        LegalSection(
            heading = "Technical Support & Bug Reporting",
            body = "If you encounter any discrepancies in application statuses, mock interview telemetry, or messaging delivery, contact our mobile development response team."
        )
        LegalSection(
            heading = "Frequently Asked Questions",
            body = "Q: How do I verify my alumni status?\nA: Go to Alumni Settings > Security & Account to review your verified graduation credentials.\n\nQ: How do mentorship requests work?\nA: When a student or peer requests mentorship, you will receive a notification in the Mentorship Hub where you can accept and schedule a 1:1 session.\n\nQ: How do I offer employee referrals?\nA: Toggle 'Willing to Refer' in your Career Preferences, and student candidates will be able to request internal referrals through the Referrals Hub."
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    LegalPageScaffold(title = "About CampusVerse", onBack = onBack) {
        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp)),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(
                            text = "CV",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "CampusVerse",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Version 2.4.0 (Build 2026.08)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Empowering campus communities and verified alumni networks worldwide with intelligent career acceleration and peer mentorship.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LegalSection(
            heading = "Platform Mission",
            body = "CampusVerse bridges the gap between active campus learners and global alumni leaders. We empower verified university communities through persistent data, career acceleration, transparent mentorship, and peer networking."
        )
        LegalSection(
            heading = "Core Capabilities",
            body = "• Verified Alumni & Peer Directory\n• 1:1 Mentorship Sessions & Video Calls\n• Employee Referrals & Career Opportunities\n• AI-Powered Career Roadmap & Mock Interviews\n• Campus Event Discovery & Real-Time RSVP Tracking"
        )
        LegalSection(
            heading = "Architecture & Engineering",
            body = "Built with Modern Android Jetpack Compose, Material 3 Design Tokens, Node.js backend microservices, Prisma ORM, and PostgreSQL enterprise data persistence."
        )
        LegalSection(
            heading = "Official Channels & Support",
            body = "Official Website: https://campusverse.edu\nDeveloper Inquiries: dev@campusverse.edu\nAlumni Support: alumni-support@campusverse.edu"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(onBack: () -> Unit) {
    LegalPageScaffold(title = "Terms & Conditions", onBack = onBack) {
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text(
                text = "Last updated: August 2026 • Version 2.4",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LegalSection(
            heading = "1. Acceptance of Terms",
            body = "By accessing or using the CampusVerse platform, alumni and students agree to comply with and be bound by these Terms and Conditions, all applicable university regulations, and our Community Guidelines."
        )
        LegalSection(
            heading = "2. Verified Alumni Eligibility",
            body = "Access to the alumni module requires valid university graduation credentials or official administrative verification. Unauthorized access or falsification of alumni credentials is strictly prohibited."
        )
        LegalSection(
            heading = "3. Mentorship & Referral Conduct",
            body = "Alumni agree to provide honest feedback and mentorship guidance free of harassment, discriminatory recruitment practices, financial compensation demands, or commercial exploitation."
        )
        LegalSection(
            heading = "4. User Accounts & Security",
            body = "Users are responsible for maintaining the confidentiality of their credentials and session tokens, and for all activities that occur under their authenticated accounts."
        )
        LegalSection(
            heading = "5. Intellectual Property & Privacy",
            body = "All platform content, trade dress, and features are protected under applicable intellectual property laws. User telemetry and profile records are safeguarded in accordance with our Privacy Policy."
        )
        LegalSection(
            heading = "6. Termination & Suspension",
            body = "CampusVerse reserves the right to suspend or terminate accounts that violate platform policies, academic integrity standards, or code of conduct."
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LegalPageScaffold(title = "Privacy Policy", onBack = onBack) {
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text(
                text = "Last updated: August 2026 • Version 2.4",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LegalSection(
            heading = "1. Information We Collect",
            body = "We collect verified student and alumni profile details, university graduation credentials, career telemetry, application resumes, direct messaging transcripts, and device authentication tokens."
        )
        LegalSection(
            heading = "2. How We Use Your Information",
            body = "Data is used exclusively to facilitate peer networking, verify alumni identity, power AI career acceleration roadmaps, deliver mentorship scheduling, and improve platform performance."
        )
        LegalSection(
            heading = "3. Granular Privacy & Visibility Controls",
            body = "Alumni maintain complete sovereignty over their email, phone number, and career visibility. You can configure public, peer-only, or hidden profile states via Privacy Settings at any time."
        )
        LegalSection(
            heading = "4. Data Sharing & Third Parties",
            body = "We never sell personal user data. Aggregated analytics and recruitment telemetry may be shared with verified partner institutions only under strict institutional data agreements."
        )
        LegalSection(
            heading = "5. Data Retention & Deletion Rights",
            body = "You have the right to request full export or permanent erasure of your profile records, applications, and messaging transcripts by submitting a request to privacy@campusverse.edu."
        )
        LegalSection(
            heading = "6. Security Protocols",
            body = "All transmissions are secured via TLS 1.3 encryption and stored in encrypted PostgreSQL database relations with strict role-based access control (RBAC)."
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityGuidelinesScreen(onBack: () -> Unit) {
    LegalPageScaffold(title = "Community Guidelines", onBack = onBack) {
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text(
                text = "CampusVerse Community Standards • Version 2.4",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LegalSection(
            heading = "1. Professional Decorum & Respect",
            body = "All alumni, mentors, recruiters, and students must interact with mutual courtesy, respect, and professional integrity across messaging, forums, and 1:1 mentorship sessions."
        )
        LegalSection(
            heading = "2. Honest Mentorship & Referral Conduct",
            body = "Mentors agree to offer constructive, actionable guidance. Demanding financial compensation, academic ghostwriting, or unethical favors in exchange for referrals is strictly prohibited."
        )
        LegalSection(
            heading = "3. Authentic Identity & Verification",
            body = "Impersonation of university faculty, corporate recruiters, or fellow alumni is a critical violation resulting in permanent platform expulsion."
        )
        LegalSection(
            heading = "4. Anti-Harassment & Non-Discrimination",
            body = "CampusVerse enforces a zero-tolerance policy against hate speech, harassment, bullying, sexual harassment, or discrimination based on race, gender, nationality, or background."
        )
        LegalSection(
            heading = "5. Academic Integrity & Confidentiality",
            body = "Respect proprietary company interview information and university honor codes. Do not share leaked exam contents, non-disclosure agreement (NDA) protected materials, or private student records."
        )
        LegalSection(
            heading = "6. Reporting & Enforcement Escalation",
            body = "If you encounter abusive behavior or policy violations, report it immediately via the Help & Support center or by emailing compliance@campusverse.edu. Our moderation team reviews all flagged incidents within 24 hours."
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                content()
            }
        }
    }
}

@Composable
private fun LegalSection(heading: String, body: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
        }
    }
}
