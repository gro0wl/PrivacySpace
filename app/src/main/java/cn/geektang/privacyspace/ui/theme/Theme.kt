package cn.geektang.privacyspace.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// NOTE: libs/android-30.jar is on the compile classpath and shadows the
// platform stubs, so the compiler only sees API 30. Anything above API 30
// must be resolved at runtime (no direct references to VERSION_CODES.S,
// android.R.color.system_*, etc.), otherwise compileReleaseKotlin fails.
private const val ANDROID_S = 31

private val DarkColorPalette = darkColors(
    primary = Purple200,
    primaryVariant = Purple700,
    secondary = Teal200
)

private val LightColorPalette = lightColors(
    primary = Purple500,
    primaryVariant = Purple700,
    secondary = Teal200

    /* Other default colors to override
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    */
)

@Composable
fun PrivacySpaceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = remember(darkTheme) {
        if (Build.VERSION.SDK_INT >= ANDROID_S) {
            dynamicColors(context, darkTheme)
        } else if (darkTheme) {
            DarkColorPalette
        } else {
            LightColorPalette
        }
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

// Material You (Monet) palette from the system on Android 12+,
// mapped onto Material2 roles so no dependency bump is needed.
// Colors are looked up by name at runtime (see NOTE above).
@RequiresApi(ANDROID_S)
private fun dynamicColors(context: Context, darkTheme: Boolean): Colors {
    val fallback = if (darkTheme) DarkColorPalette else LightColorPalette
    fun c(name: String, fallbackColor: Color): Color {
        val id = context.resources.getIdentifier(name, "color", "android")
        if (id == 0) return fallbackColor
        return try {
            Color(context.getColor(id))
        } catch (e: Exception) {
            fallbackColor
        }
    }
    return if (darkTheme) {
        darkColors(
            primary = c("system_accent1_200", fallback.primary),
            primaryVariant = c("system_accent1_400", fallback.primaryVariant),
            secondary = c("system_accent2_200", fallback.secondary),
            background = c("system_neutral1_900", fallback.background),
            surface = c("system_neutral1_900", fallback.surface),
            onPrimary = c("system_neutral1_900", fallback.onPrimary),
            onSecondary = c("system_neutral1_900", fallback.onSecondary),
            onBackground = c("system_neutral1_100", fallback.onBackground),
            onSurface = c("system_neutral1_100", fallback.onSurface)
        )
    } else {
        lightColors(
            primary = c("system_accent1_600", fallback.primary),
            primaryVariant = c("system_accent1_700", fallback.primaryVariant),
            secondary = c("system_accent2_600", fallback.secondary),
            background = c("system_neutral1_50", fallback.background),
            surface = c("system_neutral1_50", fallback.surface),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = c("system_neutral1_900", fallback.onBackground),
            onSurface = c("system_neutral1_900", fallback.onSurface)
        )
    }
}