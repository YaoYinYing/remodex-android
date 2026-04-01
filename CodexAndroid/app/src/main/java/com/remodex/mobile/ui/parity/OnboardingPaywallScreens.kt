package com.remodex.mobile.ui.parity

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import kotlinx.coroutines.launch

private data class OnboardingFeature(
    val title: String,
    val detail: String,
    val accent: Color
)

private data class OnboardingStep(
    val number: Int,
    val badge: String,
    val title: String,
    val description: String,
    val command: String
)

private val onboardingFeatures = listOf(
    OnboardingFeature("Fast mode", "Lower-latency turns for quick interactions.", Color(0xFFF6C453)),
    OnboardingFeature("Git from Android", "Commit, push, pull, and switch branches.", Color(0xFF67D88A)),
    OnboardingFeature("End-to-end encrypted", "The relay never sees your prompts or code.", Color(0xFF5DD7E7)),
    OnboardingFeature("Voice mode", "Talk to Codex with speech-to-text.", Color(0xFFB58BFF)),
    OnboardingFeature("Subagents, skills and /commands", "Spawn and monitor parallel agents from your phone.", Color(0xFFFFA24D))
)

private val onboardingSteps = listOf(
    OnboardingStep(
        number = 1,
        badge = "CLI",
        title = "Install Codex CLI",
        description = "The AI coding agent that lives in your terminal. Remodex connects to it from your Android.",
        command = "npm install -g @openai/codex@latest"
    ),
    OnboardingStep(
        number = 2,
        badge = "BR",
        title = "Install the Bridge",
        description = "A lightweight relay that securely connects your Mac to your Android.",
        command = "npm install -g remodex@latest"
    ),
    OnboardingStep(
        number = 3,
        badge = "QR",
        title = "Start Pairing",
        description = "Run this on your Mac. A QR code will appear in your terminal and you scan it next.",
        command = "remodex up"
    )
)

private data class PaywallFeature(
    val title: String,
    val subtitle: String,
    val accent: Color,
    val glyph: String
)

private val paywallFeatures = listOf(
    PaywallFeature("Fast mode", "Lower-latency turns for quick interactions.", Color(0xFFF6C453), "FM"),
    PaywallFeature("Git from Android", "Commit, push, pull, and switch branches.", Color(0xFF67D88A), "GT"),
    PaywallFeature("E2EE", "The relay never sees your prompts or code.", Color(0xFF5DD7E7), "LK"),
    PaywallFeature("Voice mode", "Speech-to-text transcription for your messages.", Color(0xFFB58BFF), "VC"),
    PaywallFeature("Subagents", "Delegate complex tasks to specialized sub-agents.", Color(0xFFFFA24D), "SA"),
    PaywallFeature("@files /commands", "Invoke skills, run slash commands, and mention files inline.", Color(0xFF7AA7FF), "@"),
    PaywallFeature("Local-first bridge", "Your Mac stays the runtime even when the phone is remote.", Color(0xFF9FD16E), "LF"),
    PaywallFeature("Support development", "Help keep Remodex independent and open source.", Color(0xFFFF8E9A), "OS")
)

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = 0) { 5 }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> FeaturesPage()
                    else -> StepPage(step = onboardingSteps[page - 2])
                }
            }

            OnboardingBottomBar(
                currentPage = pagerState.currentPage,
                pageCount = 5,
                onContinue = {
                    if (pagerState.currentPage < 4) {
                        scope.launch { pagerState.scrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onContinue()
                    }
                }
            )
        }
    }
}

@Composable
fun PaywallScreen(
    onUnlock: () -> Unit,
    onRestore: () -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF06070B),
            Color(0xFF10131A),
            Color(0xFF06070B)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 40.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.original_author_icon),
                        contentDescription = "Remodex",
                        modifier = Modifier
                            .size(78.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Remodex Pro Required",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Silent development gate enabled. Public pricing stays hidden while the Android parity pass is in development.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.66f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "What you get",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        paywallFeatures.forEach { feature ->
                            PaywallFeatureCard(feature)
                        }
                    }
                }
            }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Development access",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                        Text(
                            text = "Continue into pairing with the current local development gate. Public pricing remains intentionally unavailable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.66f)
                        )
                        Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
                            Text("Enter")
                        }
                        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                            Text("Restore Access")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.onboarding_hero_three),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentScale = ContentScale.FillWidth
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            0.60f to Color.Black.copy(alpha = 0.5f),
                            0.72f to Color.Black
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.original_author_icon),
                contentDescription = "Remodex",
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Remodex",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "Control Codex from your Android.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.56f)
                )
            }
            Surface(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(999.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF5DD7E7))
                    )
                    Text(
                        text = "End-to-end encrypted",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturesPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(38.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "What you get",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "Everything runs on your Mac.\nYour Android is the remote.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                onboardingFeatures.forEach { feature ->
                    OnboardingFeatureRow(feature)
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StepPage(step: OnboardingStep) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF3D6CF9).copy(alpha = 0.14f), Color.Transparent),
                    radius = 820f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(34.dp)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = step.badge,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                            color = Color.White
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "STEP ${step.number}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF88A9FF)
                    )
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.48f),
                        textAlign = TextAlign.Center
                    )
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.07f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = step.command,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    currentPage: Int,
    pageCount: Int,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(pageCount) { index ->
                Box(
                    modifier = Modifier
                        .weight(if (index == currentPage) 3f else 1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (index == currentPage) Color.White else Color.White.copy(alpha = 0.18f)
                        )
                )
            }
        }
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = when (currentPage) {
                    0 -> "Get Started"
                    1 -> "Set Up"
                    pageCount - 1 -> "Scan QR Code"
                    else -> "Continue"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
        Text(
            text = "Open source on GitHub",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OnboardingFeatureRow(feature: OnboardingFeature) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(feature.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(feature.accent)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            Text(
                text = feature.detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.42f)
            )
        }
    }
}

@Composable
private fun PaywallFeatureCard(feature: PaywallFeature) {
    Surface(
        modifier = Modifier.width(200.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.06f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(feature.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = feature.glyph,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = feature.accent
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
                Text(
                    text = feature.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}
