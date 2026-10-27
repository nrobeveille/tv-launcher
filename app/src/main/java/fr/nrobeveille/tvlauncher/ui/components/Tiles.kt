package fr.nrobeveille.tvlauncher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.nrobeveille.tvlauncher.data.AppInfo
import fr.nrobeveille.tvlauncher.data.LabelMode
import fr.nrobeveille.tvlauncher.ui.theme.Accent
import fr.nrobeveille.tvlauncher.ui.theme.SubtleText
import fr.nrobeveille.tvlauncher.ui.theme.TileColor

private val TileShape = RoundedCornerShape(12.dp)
private val LabelHeight = 30.dp

/**
 * Tuile 16:9 d'une appli : bannière TV si elle existe, sinon icône + nom.
 * [modifier] s'applique à la partie focusable (pour y brancher un FocusRequester).
 */
@Composable
fun AppTile(
    app: AppInfo,
    width: Dp,
    labelMode: LabelMode,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    moving: Boolean = false,
    showBadge: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val visual = rememberBanner(app)
    val hasBanner = visual?.isBanner == true

    Column(
        modifier = Modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TileSurface(
            width = width,
            onClick = onClick,
            onLongClick = onLongClick,
            highlight = moving,
            modifier = modifier.onFocusChanged { focused = it.isFocused || it.hasFocus },
        ) {
            if (visual != null && hasBanner) {
                val bitmap = remember(visual) { visual.bitmap.asImageBitmap() }
                Image(
                    bitmap = bitmap,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (visual != null) {
                        val bitmap = remember(visual) { visual.bitmap.asImageBitmap() }
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.size(width * 0.24f),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        text = app.label,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (showBadge) CheckBadge(Modifier.align(Alignment.TopEnd))

            if (moving) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("◀", fontSize = 26.sp, color = Color.White)
                    Text("▶", fontSize = 26.sp, color = Color.White)
                }
            }
        }

        // Nom sous la tuile (les tuiles sans bannière l'affichent déjà dedans).
        if (labelMode != LabelMode.JAMAIS) {
            val visible = hasBanner &&
                (labelMode == LabelMode.TOUJOURS || focused || moving)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LabelHeight),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (visible) {
                    Text(
                        text = app.label,
                        fontSize = 14.sp,
                        color = if (focused) Color.White else SubtleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Dernière tuile de l'accueil, qui ouvre la liste de toutes les applis. */
@Composable
fun AllAppsTile(
    width: Dp,
    labelMode: LabelMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TileSurface(
            width = width,
            onClick = onClick,
            onLongClick = null,
            highlight = false,
            containerColor = Color.White.copy(alpha = 0.08f),
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                GridIcon(size = width * 0.16f, color = Color.White)
                Spacer(Modifier.height(10.dp))
                Text("Toutes les applis", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (labelMode != LabelMode.JAMAIS) Spacer(Modifier.height(LabelHeight))
    }
}

@Composable
private fun TileSurface(
    width: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    highlight: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = TileColor,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .width(width)
            .height(width * 9f / 16f),
        shape = ClickableSurfaceDefaults.shape(shape = TileShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = Color.White,
            focusedContainerColor = containerColor,
            focusedContentColor = Color.White,
            pressedContainerColor = containerColor,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, if (highlight) Accent else Color.White),
                shape = TileShape,
            ),
        ),
        content = content,
    )
}
