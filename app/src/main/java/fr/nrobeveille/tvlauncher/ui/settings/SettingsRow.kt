package fr.nrobeveille.tvlauncher.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.nrobeveille.tvlauncher.ui.theme.Accent
import fr.nrobeveille.tvlauncher.ui.theme.SubtleText

private val RowShape = RoundedCornerShape(12.dp)

/** Ligne de réglage focusable : titre, sous-titre, valeur à droite. */
@Composable
fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = false,
    highlighted: Boolean = false,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RowShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = if (highlighted) 0.14f else 0.05f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF0B1020),
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (highlighted) Border(BorderStroke(2.dp, Accent), shape = RowShape) else Border.None,
            focusedBorder = if (highlighted) Border(BorderStroke(3.dp, Accent), shape = RowShape) else Border.None,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            leading?.invoke(this)
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (value != null) {
                Text(
                    text = value,
                    fontSize = 16.sp,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                )
            }
            trailing?.invoke(this)
            if (chevron) {
                Text(text = "›", fontSize = 24.sp)
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = SubtleText,
        modifier = modifier.padding(start = 8.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** Case à cocher dessinée (suit la couleur du texte de la ligne). */
@Composable
fun CheckMark(checked: Boolean) {
    val color = LocalContentColor.current
    Box(
        modifier = Modifier
            .size(26.dp)
            .then(
                if (checked) {
                    Modifier.background(Accent, RoundedCornerShape(6.dp))
                } else {
                    Modifier.border(2.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Image(
                imageVector = Icons.Filled.Check,
                contentDescription = "Sélectionnée",
                colorFilter = ColorFilter.tint(Color(0xFF0B1020)),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
