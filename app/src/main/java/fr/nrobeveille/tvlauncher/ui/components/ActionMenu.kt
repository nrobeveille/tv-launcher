package fr.nrobeveille.tvlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.nrobeveille.tvlauncher.ui.theme.DialogColor
import fr.nrobeveille.tvlauncher.ui.theme.SubtleText

class MenuAction(val label: String, val onClick: () -> Unit)

/** Petit menu contextuel (appui long sur une appli, confirmation…). */
@Composable
fun ActionMenu(
    title: String,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        val firstItem = remember { FocusRequester() }
        Column(
            modifier = Modifier
                .ignoreHeldSelectKey()
                .width(420.dp)
                .background(DialogColor, RoundedCornerShape(18.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(text = subtitle, fontSize = 14.sp, color = SubtleText)
            }
            Spacer(Modifier.height(8.dp))
            actions.forEachIndexed { index, action ->
                MenuButton(
                    label = action.label,
                    modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
                    onClick = {
                        onDismiss()
                        action.onClick()
                    },
                )
            }
        }
        LaunchedEffect(Unit) {
            awaitLayout()
            runCatching { firstItem.requestFocus() }
        }
    }
}

@Composable
private fun MenuButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF0B1020),
        ),
    ) {
        Text(
            text = label,
            fontSize = 17.sp,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .align(androidx.compose.ui.Alignment.CenterStart),
        )
    }
}
