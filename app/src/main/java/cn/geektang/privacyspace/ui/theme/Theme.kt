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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
@RequiresApi(Build.VERSION_CODES.S)
private fun dynamicColors(context: Context, darkTheme: Boolean): Colors {
    fun c(id: Int): Color = Color(context.getColor(id))
    return if (darkTheme) {
        darkColors(
            primary = c(android.R.color.system_accent1_200),
            primaryVariant = c(android.R.color.system_accent1_400),
            secondary = c(android.R.color.system_accent2_200),
            background = c(android.R.color.system_neutral1_900),
            surface = c(android.R.color.system_neutral1_900),
            onPrimary = c(android.R.color.system_neutral1_900),
            onSecondary = c(android.R.color.system_neutral1_900),
            onBackground = c(android.R.color.system_neutral1_100),
            onSurface = c(android.R.color.system_neutral1_100)
        )
    } else {
        lightColors(
            primary = c(android.R.color.system_accent1_600),
            primaryVariant = c(android.R.color.system_accent1_700),
            secondary = c(android.R.color.system_accent2_600),
            background = c(android.R.color.system_neutral1_50),
            surface = c(android.R.color.system_neutral1_50),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = c(android.R.color.system_neutral1_900),
            onSurface = c(android.R.color.system_neutral1_900)
        )
    }
}