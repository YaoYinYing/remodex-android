package com.remodex.mobile.ui.parity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val pageGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HeroCard(
                    stateLabel = "Welcome",
                    status = "Pair with your Mac and keep coding from anywhere.",
                    indicatorColor = Color(0xFF7AA2F7),
                    subtitle = "Local-first coding on your desktop thread"
                )
            }
            item {
                SectionCard(
                    title = "1. Prepare your Mac",
                    subtitle = "Install the CLI and bridge once."
                ) {
                    Text(
                        text = "npm install -g @openai/codex@latest",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "npm install -g remodex@latest",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            item {
                SectionCard(
                    title = "2. Start a local session",
                    subtitle = "Open a relay on your Mac before pairing."
                ) {
                    Text(
                        text = "remodex up",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            item {
                SectionCard(
                    title = "3. Pair and continue",
                    subtitle = "Scan the QR on the next screen, then open a chat."
                ) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue")
                    }
                }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeroCard(
                stateLabel = "Access",
                status = "This build uses an internal access gate before pairing.",
                indicatorColor = MaterialTheme.colorScheme.secondary,
                subtitle = "Development access"
            )
            SectionCard(
                title = "Continue",
                subtitle = "Pricing is hidden in this development build."
            ) {
                Text(
                    text = "Use your existing development access and move straight into pairing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
