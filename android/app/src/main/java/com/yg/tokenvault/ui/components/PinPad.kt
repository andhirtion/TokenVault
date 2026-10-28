package com.yg.tokenvault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.tokenvault.ui.theme.LocalPalette

/**
 * PIN 圆点：已输入的填实并放大，未输入的留空心圈。
 */
@Composable
fun PinDots(
    length: Int,
    filled: Int,
    error: Boolean = false,
    modifier: Modifier = Modifier
) {
    val pal = LocalPalette.current
    val onColor = if (error) pal.danger else pal.accent

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(length) { i ->
            val on = i < filled
            val dotSize by animateDpAsState(
                targetValue = if (on) 13.dp else 10.dp,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 900f),
                label = "dot"
            )
            val bg by animateColorAsState(
                targetValue = if (on) onColor else Color.Transparent,
                animationSpec = spring(stiffness = 900f),
                label = "dotColor"
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(bg)
                    .border(
                        width = if (on) 0.dp else 1.6.dp,
                        color = if (on) Color.Transparent else pal.text3.copy(alpha = 0.55f),
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * 数字键盘。九宫格 + 退格，中间可选生物识别。
 */
@Composable
fun PinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    onBiometric: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(BIOMETRIC, "0", BACKSPACE)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                row.forEach { key ->
                    when (key) {
                        BACKSPACE -> KeyCell(
                            modifier = Modifier.weight(1f),
                            onClick = onBackspace,
                            enabled = enabled,
                            icon = Icons.AutoMirrored.Outlined.Backspace
                        )
                        BIOMETRIC -> KeyCell(
                            modifier = Modifier.weight(1f),
                            onClick = { onBiometric?.invoke() },
                            enabled = enabled && onBiometric != null,
                            icon = if (onBiometric != null) Icons.Outlined.Fingerprint else null
                        )
                        else -> NumCell(
                            modifier = Modifier.weight(1f),
                            digit = key,
                            enabled = enabled,
                            onClick = onDigit
                        )
                    }
                }
            }
        }
    }
}

private const val BIOMETRIC = "bio"
private const val BACKSPACE = "del"

@Composable
private fun NumCell(
    digit: String,
    enabled: Boolean,
    onClick: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = LocalPalette.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = spring(stiffness = 1200f),
        label = "numScale"
    )

    Box(
        modifier = modifier
            .height(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(if (pressed) pal.surfaceHi else pal.surface)
            .border(1.dp, pal.border, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .then(
                if (enabled) Modifier.clickableNoRipple(source) { onClick(digit[0]) }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 23.sp),
            color = if (enabled) pal.text else pal.text3
        )
    }
}

@Composable
private fun KeyCell(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val pal = LocalPalette.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(58.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(if (pressed) pal.surfaceHi else Color.Transparent)
            .then(if (enabled) Modifier.clickableNoRipple(source, onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) pal.text2 else pal.text3.copy(alpha = 0.35f),
                modifier = Modifier.size(21.dp)
            )
        } else {
            Spacer(Modifier.width(1.dp))
        }
    }
}

/** 无涟漪点击：键盘上涟漪会很脏 */
private fun Modifier.clickableNoRipple(
    source: MutableInteractionSource,
    onClick: () -> Unit
): Modifier = this.clickable(
    interactionSource = source,
    indication = null,
    onClick = onClick
)