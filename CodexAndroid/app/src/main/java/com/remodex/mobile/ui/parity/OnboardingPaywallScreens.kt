package com.remodex.mobile.ui.parity

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val command: String? = null
)

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardingPage(
                title = "Control Codex from your iPhone.",
                subtitle = "Open-source iPhone bridge for Codex. Local-first, end-to-end encrypted."
            ),
            OnboardingPage(
                title = "Built for focus.",
                subtitle = "Live control, Git from iPhone, secure pairing, and @files/\$skills//commands."
            ),
            OnboardingPage(
                title = "1. Install Codex CLI",
                subtitle = "The AI coding agent that lives in your terminal.",
                command = "npm install -g @openai/codex@latest"
            ),
            OnboardingPage(
                title = "2. Install the bridge",
                subtitle = "A lightweight relay that securely connects your Mac and phone.",
                command = "npm install -g remodex@latest"
            ),
            OnboardingPage(
                title = "3. Start pairing",
                subtitle = "Run this on your Mac and scan the QR code on the next screen.",
                command = "remodex up"
            )
        )
    }
    val pagerState = rememberPagerState(initialPage = 0) { pages.size }
    val scope = rememberCoroutineScope()
    val pageGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF050608),
            Color(0xFF0A0B0E),
            Color(0xFF050608)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { index ->
                val page = pages[index]
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Image(
                            painter = painterResource(id = R.drawable.original_author_icon),
                            contentDescription = "Remodex",
                            modifier = Modifier.size(88.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    item {
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Text(
                            text = page.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (index == 1) {
                        item {
                            OnboardingFeatureTile(
                                title = "Live control",
                                detail = "Fast mode, Plan mode, steer active runs, and queue follow-up prompts."
                            )
                        }
                        item {
                            OnboardingFeatureTile(
                                title = "Git from iPhone",
                                detail = "Commit, push, pull, branch, stash, and inspect diffs."
                            )
                        }
                        item {
                            OnboardingFeatureTile(
                                title = "Secure pairing",
                                detail = "QR bootstrap with E2E encryption and trusted auto-reconnect."
                            )
                        }
                    }
                    if (!page.command.isNullOrBlank()) {
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = page.command,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(pages.size) { i ->
                        Box(
                            modifier = Modifier
                                .weight(if (i == pagerState.currentPage) 2f else 1f)
                                .height(8.dp)
                                .background(
                                    if (i == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.18f),
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                    }
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage < pages.lastIndex) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            onContinue()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text(
                        text = when (pagerState.currentPage) {
                            0 -> "Get Started"
                            1 -> "Set Up"
                            pages.lastIndex -> "Scan QR Code"
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
    }
}

@Composable
fun PaywallScreen(
    onUnlock: () -> Unit,
    onRestore: () -> Unit
) {
    val pageGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageGradient)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.original_author_icon),
                        contentDescription = "Remodex",
                        modifier = Modifier.size(78.dp),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = "Remodex Pro",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        text = "Silent development gate enabled. Public pricing is intentionally hidden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            item {
                SectionCard(
                    title = "What you get",
                    subtitle = "Website + iOS parity claims mirrored in Android."
                ) {
                    OnboardingFeatureTile(title = "Live control", detail = "Fast mode, Plan mode, steering, queued follow-ups.")
                    OnboardingFeatureTile(title = "Git from iPhone", detail = "Commit, push, pull, branch, stash, and diff inspection.")
                    OnboardingFeatureTile(title = "Secure pairing", detail = "QR bootstrap, E2EE transport, trusted reconnect.")
                    OnboardingFeatureTile(title = "@files, \$skills, /commands", detail = "Inline mentions, command routing, and subagent-oriented flow.")
                }
            }
            item {
                SectionCard(
                    title = "Development access",
                    subtitle = "Continue into pairing with existing local development credentials."
                ) {
                    Button(
                        onClick = onUnlock,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enter")
                    }
                    OutlinedButton(
                        onClick = onRestore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Restore Access")
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingFeatureTile(title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
