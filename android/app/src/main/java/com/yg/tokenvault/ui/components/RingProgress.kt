package com.yg.tokenvault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 剩余时间环：从 12 点方向顺时针走一圈。
 *
 * [progress] 为剩余比例 0..1。剩得越少颜色越急（由调用方传入 active 控制）。
 */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    stroke: Dp = 3.dp,
    active: Color,
    track: Color
) {
    val p = progress.coerceIn(0f, 1f)
    Canvas(modifier = modifier.size(size)) {
        val sw = stroke.toPx()
        val inset = sw / 2f
        val arcSize = Size(this.size.width - sw, this.size.height - sw)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
        if (p > 0.001f) {
            drawArc(
                color = active,
                startAngle = -90f,
                sweepAngle = 360f * p,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
    }
}