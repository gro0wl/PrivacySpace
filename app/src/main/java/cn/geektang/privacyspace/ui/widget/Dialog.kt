@file:OptIn(ExperimentalMaterial3Api::class)

package cn.geektang.privacyspace.ui.widget

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import cn.geektang.privacyspace.R
import kotlin.system.exitProcess

@Composable
fun MessageDialog(
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    positiveButtonText: String = stringResource(id = R.string.confirm),
    negativeButtonText: String = stringResource(id = R.string.cancel),
    onPositiveButtonClick: () -> Unit = onDismissRequest,
    onNegativeButtonClick: () -> Unit = onDismissRequest
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onPositiveButtonClick) {
                Text(
                    text = positiveButtonText,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onNegativeButtonClick) {
                Text(
                    text = negativeButtonText,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        properties = properties,
        text = text,
        title = title
    )
}

@Composable
fun NoticeDialog(
    text: String,
    onDismissRequest: () -> Unit,
    onPositiveButtonClick: () -> Unit = onDismissRequest
) {
    MessageDialog(
        title = {
            Text(text = stringResource(R.string.tips))
        },
        text = {
            Text(text = text)
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        negativeButtonText = stringResource(id = R.string.launcher_notice_cancel),
        positiveButtonText = stringResource(id = R.string.launcher_notice_confirm),
        onNegativeButtonClick = {
            exitProcess(0)
        },
        onPositiveButtonClick = onPositiveButtonClick,
        onDismissRequest = onDismissRequest
    )
}
