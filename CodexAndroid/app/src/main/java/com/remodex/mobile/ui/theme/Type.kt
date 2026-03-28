package com.remodex.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.remodex.mobile.R

enum class AppFontStyle(val storageValue: String, val title: String, val subtitle: String) {
    SYSTEM(
        storageValue = "system",
        title = "System",
        subtitle = "Use native Android text while keeping code monospaced."
    ),
    GEIST(
        storageValue = "geist",
        title = "Geist",
        subtitle = "Use Geist for prose and JetBrains Mono for code."
    ),
    GEIST_MONO(
        storageValue = "geistMono",
        title = "Geist Mono",
        subtitle = "Use Geist Mono for both prose and code."
    ),
    JETBRAINS_MONO(
        storageValue = "jetBrainsMono",
        title = "JetBrains Mono",
        subtitle = "Use JetBrains Mono for both prose and code."
    );

    companion object {
        fun fromStorage(raw: String?): AppFontStyle {
            return entries.firstOrNull { it.storageValue.equals(raw, ignoreCase = true) } ?: SYSTEM
        }
    }
}

@Immutable
data class RemodexFontSet(
    val prose: FontFamily,
    val mono: FontFamily
)

private val geistFamily = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold)
)

private val geistMonoFamily = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
    Font(R.font.geist_mono_bold, FontWeight.Bold)
)

private val jetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

fun remodexFontSet(style: AppFontStyle): RemodexFontSet {
    return when (style) {
        AppFontStyle.SYSTEM -> RemodexFontSet(
            prose = FontFamily.Default,
            mono = FontFamily.Monospace
        )
        AppFontStyle.GEIST -> RemodexFontSet(
            prose = geistFamily,
            mono = jetBrainsMonoFamily
        )
        AppFontStyle.GEIST_MONO -> RemodexFontSet(
            prose = geistMonoFamily,
            mono = geistMonoFamily
        )
        AppFontStyle.JETBRAINS_MONO -> RemodexFontSet(
            prose = jetBrainsMonoFamily,
            mono = jetBrainsMonoFamily
        )
    }
}

fun remodexTypography(style: AppFontStyle): Typography {
    val fonts = remodexFontSet(style)
    val prose = fonts.prose
    val mono = fonts.mono

    return Typography(
        headlineLarge = TextStyle(
            fontFamily = prose,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            lineHeight = 34.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = prose,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 28.sp
        ),
        titleLarge = TextStyle(
            fontFamily = prose,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 24.sp
        ),
        titleMedium = TextStyle(
            fontFamily = prose,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 20.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = prose,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = prose,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        bodySmall = TextStyle(
            fontFamily = prose,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 18.sp
        ),
        labelMedium = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 0.4.sp
        ),
        labelSmall = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 10.sp,
            letterSpacing = 0.3.sp
        )
    )
}
