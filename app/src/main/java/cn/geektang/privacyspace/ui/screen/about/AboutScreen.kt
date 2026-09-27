package cn.geektang.privacyspace.ui.screen.about

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.geektang.privacyspace.BuildConfig
import cn.geektang.privacyspace.R
import cn.geektang.privacyspace.ui.widget.TopBar
import cn.geektang.privacyspace.util.LocalNavHostController
import cn.geektang.privacyspace.util.openUrl
import coil.compose.AsyncImage

@Preview(showSystemUi = true)
@Composable
fun AboutScreen() {
    val navController = LocalNavHostController.current
    val context = LocalContext.current
    val appIcon = remember {
        context.applicationInfo.loadIcon(context.packageManager)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = stringResource(R.string.about), onNavigationIconClick = {
            navController.popBackStack()
        })

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val appName = stringResource(id = R.string.app_name)
            AsyncImage(
                model = appIcon,
                contentDescription = appName
            )
            Text(
                modifier = Modifier.padding(top = 5.dp),
                text = appName,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Version: ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium
            )
        }

        GroupTitle(text = "What's this")
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 10.dp),
            text = stringResource(R.string.about_page_app_intro),
            fontSize = 14.sp
        )

        GroupTitle(text = "Developers")
        val developers = remember {
            listOf(
                CardItem(
                    R.drawable.ic_avatar,
                    "GeekTR",
                    "Developer & Designer",
                    "https://github.com/GeekTR"
                ),
                CardItem(
                    R.drawable.ic_github,
                    "Source Code",
                    "https://github.com/GeekTR/PrivacySpace",
                    "https://github.com/GeekTR/PrivacySpace"
                ),
            )
        }
        developers.forEach {
            Card(cardItem = it)
        }

        val telegramAndCoolapk = remember {
            listOf(
                CardItem(
                    R.drawable.ic_telegram,
                    "Telegram",
                    context.getString(R.string.about_page_telegram_description),
                    "https://t.me/PrivacySpaceAlpha"
                ),
                CardItem(
                    R.drawable.ic_coolapk,
                    context.getString(R.string.coolapk),
                    context.getString(R.string.about_page_coolapk_description),
                    "coolmarket://u/18765870"
                ),
            )
        }
        GroupTitle(text = "Telegram & Coolapk")
        telegramAndCoolapk.forEach {
            Card(cardItem = it)
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Text(
            modifier = Modifier
                .padding(horizontal = 15.dp, vertical = 10.dp),
            text = text
        )
    }
}

@Composable
private fun Card(cardItem: CardItem) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .clickable {
                try {
                    context.openUrl(cardItem.homePage)
                } catch (e: Exception) {
                }
            }
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .size(38.dp),
            model = cardItem.avatar,
            contentDescription = "avatar",
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(
                text = cardItem.name,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = cardItem.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

class CardItem(
    @DrawableRes val avatar: Int,
    val name: String,
    val description: String,
    val homePage: String
)
