package com.yg.tokenvault.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** 统一缓动：先快后慢，收尾干净（与 Web 端同一根曲线） */
val AppEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * 有触感的点击：按下缩到 [scaleTo]，松开用弹簧回弹。
 *
 * 和 [androidClickable] 的区别是它有反馈 —— 列表点起来"实"，
 * 但又不带 Material 的涟漪（涟漪在深色卡片上很脏）。
 */
@Composable
fun Modifier.tactile(
    onClick: () -> Unit,
    scaleTo: Float = 0.97f,
    enabled: Boolean = true
): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) scaleTo else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tactile"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = src,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * 入场：按序号错峰，淡入 + 上移 22dp。
 *
 * 用在列表和首页卡片上，滚动到哪儿就"长"到哪儿，
 * 比整块一起出现更像在加载内容。
 */
@Composable
fun Modifier.staggerIn(index: Int, stepMs: Int = 34, durationMs: Int = 300): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.toLong().coerceAtMost(18) * stepMs)
        shown = true
    }
    val p by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMs, easing = AppEase),
        label = "stagger"
    )
    return this.graphicsLayer {
        alpha = p
        translationY = (1f - p) * 22.dp.toPx()
    }
}

/** 轻微上浮：用于弹层内容刚出现时 */
@Composable
fun Modifier.riseIn(durationMs: Int = 260): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val p by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMs, easing = AppEase),
        label = "rise"
    )
    return this.graphicsLayer {
        alpha = p
        translationY = (1f - p) * 14.dp.toPx()
    }
}
