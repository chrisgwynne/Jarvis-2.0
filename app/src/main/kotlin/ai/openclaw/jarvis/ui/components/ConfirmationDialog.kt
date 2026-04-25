package ai.openclaw.jarvis.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openclaw.jarvis.ui.theme.*

/**
 * Blueprint-styled confirmation dialog for sensitive actions
 * (SMS, WhatsApp, calls, location sharing, etc.).
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    dismissLabel: String = "Cancel",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BlueprintCard,
        shape            = RoundedCornerShape(16.dp),
        tonalElevation   = 0.dp,
        icon = {
            Icon(
                imageVector        = Icons.Default.Warning,
                contentDescription = null,
                tint               = if (isDestructive) StatusOffline else StatusQueued,
                modifier           = Modifier.size(28.dp),
            )
        },
        title = {
            Text(
                text       = title,
                color      = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize   = 16.sp,
            )
        },
        text = {
            Text(
                text       = message,
                color      = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize   = 14.sp,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) StatusOffline else CobaltPrimary,
                    contentColor   = TextPrimary,
                ),
            ) {
                Text(confirmLabel, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border  = BorderStroke(1.dp, BlueprintBorder),
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
            ) {
                Text(dismissLabel, fontFamily = FontFamily.Monospace)
            }
        },
    )
}
