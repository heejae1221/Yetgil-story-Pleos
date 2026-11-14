package app.yetgil.story.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** docs/design/tokens.css 와 같은 값 */
object Ui {
    val bg = Color(0xFF000000)
    val surface1 = Color(0xFF161618)
    val surface2 = Color(0xFF212124)
    val surface3 = Color(0xFF2C2C30)
    val line = Color(0xFF26262A)
    val lineStrong = Color(0xFF3A3A40)
    val text1 = Color(0xFFF4F4F6)
    val text2 = Color(0xFFA9A9B0)
    val text3 = Color(0xFF707078)
    val accent = Color(0xFF3D8BFF)
    val positive = Color(0xFF38C39A)
    val accentWeak = accent.copy(alpha = 0.16f)
    val accentMid = accent.copy(alpha = 0.36f)
    val positiveWeak = positive.copy(alpha = 0.16f)

    val rSm = RoundedCornerShape(8.dp)
    val rMd = RoundedCornerShape(12.dp)
    val rLg = RoundedCornerShape(16.dp)

    val display = TextStyle(color = text1, fontSize = 72.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp, lineHeight = 80.sp)
    val title1 = TextStyle(color = text1, fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 58.sp)
    val title2 = TextStyle(color = text1, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
    val title3 = TextStyle(color = text1, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
    val bodyLg = TextStyle(color = text1, fontSize = 22.sp, lineHeight = 32.sp)
    val body = TextStyle(color = text1, fontSize = 20.sp, lineHeight = 28.sp)
    val caption = TextStyle(color = text2, fontSize = 17.sp, lineHeight = 24.sp)
    val h2 = TextStyle(color = text2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(Ui.rLg).background(Ui.surface1).padding(padding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

enum class ButtonKind { Default, Primary, Tonal, Outline }

@Composable
fun BigButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Default,
    height: Dp = 72.dp,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
) {
    val (bg, fg) = when (kind) {
        ButtonKind.Default -> Ui.surface2 to Ui.text1
        ButtonKind.Primary -> Ui.accent to Color.White
        ButtonKind.Tonal -> Ui.accentWeak to Ui.accent
        ButtonKind.Outline -> Color.Transparent to Ui.text1
    }
    Row(
        modifier
            .height(height)
            .clip(Ui.rMd)
            .background(if (enabled) bg else Ui.surface2)
            .then(if (kind == ButtonKind.Outline) Modifier.border(BorderStroke(1.dp, Ui.lineStrong), Ui.rMd) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.invoke()
        Text(label, color = if (enabled) fg else Ui.text3, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

enum class ChipKind { Default, Accent, Positive, Solid, Line }

@Composable
fun Chip(label: String, kind: ChipKind = ChipKind.Default, dot: Boolean = false) {
    val (bg, fg) = when (kind) {
        ChipKind.Default -> Ui.surface2 to Ui.text1
        ChipKind.Accent -> Ui.accentWeak to Ui.accent
        ChipKind.Positive -> Ui.positiveWeak to Ui.positive
        ChipKind.Solid -> Ui.accent to Color.White
        ChipKind.Line -> Color.Transparent to Ui.text2
    }
    Row(
        Modifier
            .height(36.dp)
            .clip(Ui.rSm)
            .background(bg)
            .then(if (kind == ChipKind.Line) Modifier.border(1.dp, Ui.lineStrong, Ui.rSm) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dot) Box(Modifier.size(10.dp).clip(CircleShape).background(fg))
        Text(
            label,
            color = fg,
            fontSize = 17.sp,
            fontWeight = if (kind == ChipKind.Default || kind == ChipKind.Line) FontWeight.Medium else FontWeight.Bold,
        )
    }
}

/** 채움 = 들은 만큼, 창 = 이야기 끝, 오른쪽 표시선 = 지나가는 곳 */
@Composable
fun StoryProgress(fill: Float, window: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val bar = RoundedCornerShape(height / 2)
    Box(modifier.height(height + 16.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(height).clip(bar).background(Ui.surface3))
        if (window > 0f) Box(Modifier.fillMaxWidth(window.coerceIn(0f, 1f)).height(height).clip(bar).background(Ui.accentMid))
        if (fill > 0f) Box(Modifier.fillMaxWidth(fill.coerceIn(0f, 1f)).height(height).clip(bar).background(Ui.accent))
        Box(Modifier.align(Alignment.CenterEnd).size(width = 3.dp, height = height + 16.dp).background(Ui.text1))
    }
}

@Composable
fun RowScope.Grow() = Spacer(Modifier.weight(1f))
