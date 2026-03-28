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
                    stateLabel = "Onboarding",
                    status = "Install local bridge + CLI, then pair with your Mac.",
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    subtitle = "iOS-style setup flow"
                )
            }
            item {
                SectionCard(
                    title = "1. Install Codex CLI",
                    subtitle = "AI coding agent used by local bridge."
                ) {
                    Text(
                        text = "npm install -g @openai/codex@latest",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            item {
                SectionCard(
                    title = "2. Install Remodex bridge",
                    subtitle = "Secure relay for local Mac pairing."
                ) {
                    Text(
                        text = "npm install -g remodex@latest",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            item {
                SectionCard(
                    title = "3. Start pairing on Mac",
                    subtitle = "Run and scan QR from your phone."
                ) {
                    Text(
                        text = "remodex up",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue to Pairing")
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
                stateLabel = "Access Gate",
                status = "Development environment access control is enabled.",
                indicatorColor = MaterialTheme.colorScheme.secondary,
                subtitle = "Internal build gate"
            )
            SectionCard(
                title = "Development Access",
                subtitle = "This gate is for internal testing only. No public pricing is shown."
            ) {
                Text("Use internal access to continue testing Android parity features.")
                Button(
                    onClick = onUnlock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unlock Development Access")
                }
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use Existing Internal Access")
                }
            }
        }
    }
}
