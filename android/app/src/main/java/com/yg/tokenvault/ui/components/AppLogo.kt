package com.yg.tokenvault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 应用图标：深紫底 + 紫青双光 + 白色钥匙（环 / 柄 / 齿）+ 匣底托弧。
 *
 * 图形与 `res/drawable/ic_launcher_foreground.xml` 是同一套 108 视口坐标，
 * 所以桌面图标、启动页、关于页三处视觉一致。
 */
@Composable
fun AppLogo(size: Dp = 58.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.29f))
            .background(Color(0xFF1C1730))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val u = s / 108f

            // 底色：左上偏亮，右下压暗
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF35215C), Color(0xFF1C1730), Color(0xFF0A0B14)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                )
            )
            // 左上紫光
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color(0x668B5CF6), Color(0x1F8B5CF6), Color(0x008B5CF6)),
                    center = Offset(30f * u, 24f * u),
                    radius = 74f * u
                )
            )
            // 右下青光
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color(0x4D38BDF8), Color(0x0038BDF8)),
                    center = Offset(92f * u, 96f * u),
                    radius = 62f * u
                )
            )

            // 钥匙环
            drawCircle(
                color = Color.White,
                radius = 12.5f * u,
                center = Offset(54f * u, 46f * u),
                style = Stroke(width = 6.5f * u, cap = StrokeCap.Round)
            )
            // 钥匙柄
            drawLine(
                color = Color(0xFFEFE7FF),
                start = Offset(54f * u, 58.5f * u),
                end = Offset(54f * u, 76f * u),
                strokeWidth = 6.5f * u,
                cap = StrokeCap.Round
            )
            // 钥匙齿
            drawLine(
                color = Color(0xFFEFE7FF),
                start = Offset(54f * u, 68f * u),
                end = Offset(65f * u, 68f * u),
                strokeWidth = 6f * u,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFEFE7FF),
                start = Offset(54f * u, 76f * u),
                end = Offset(61f * u, 76f * u),
                strokeWidth = 5.5f * u,
                cap = StrokeCap.Round
            )
            // 匣底托弧
            val tray = Path().apply {
                moveTo(39f * u, 82.5f * u)
                quadraticBezierTo(54f * u, 88.5f * u, 69f * u, 82.5f * u)
            }
            drawPath(
                path = tray,
                color = Color(0x8CFFFFFF),
                style = Stroke(width = 3.6f * u, cap = StrokeCap.Round)
            )
        }
    }
}
