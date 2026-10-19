package dev.gestrin.hub.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LocalContentColor

@Composable fun Text(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified, style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, translated: Boolean = true
) {
    androidx.compose.material3.Text(if (translated) AppText.render(LocalLanguage.current, text) else text,
        modifier = modifier, color = color, fontSize = fontSize, fontWeight = fontWeight,
        letterSpacing = letterSpacing, style = style, maxLines = maxLines, overflow = overflow)
}

@Composable fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    androidx.compose.material3.Icon(imageVector, contentDescription?.let { AppText.render(LocalLanguage.current, it) }, modifier, tint)
}
