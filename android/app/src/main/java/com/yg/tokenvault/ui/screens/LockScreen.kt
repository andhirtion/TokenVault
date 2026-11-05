package com.yg.tokenvault.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.tokenvault.ui.components.AppEase
import com.yg.tokenvault.ui.components.AppLogo
import com.yg.tokenvault.ui.components.PinDots
import com.yg.tokenvault.ui.components.PinKeypad
import com.yg.tokenvault.ui.components.riseIn
import com.yg.tokenvault.ui.components.staggerIn
import com.yg.tokenvault.ui.theme.LocalPalette
import kotlinx.coroutines.launch

private const val PIN_LEN = 6

/**
 * 锁屏：首次进入走「设置 → 确认」，之后走「输入解锁」。
 */
@Composable
fun LockScreen(
    hasVault: Boolean,
    onCreate: (String) -> Boolean,
    onUnlock: (String) -> Boolean,
    modifier: Modifier = Modifier,
    onBiometric: (() -> Unit)? = null
) {
    val pal = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }

    var input by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf(if (hasVault) Stage.UNLOCK else Stage.SETUP) }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }

    fun fail(msg: String) {
        error = msg
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            shake.animateTo(-1f, tween(52))
            shake.animateTo(1f, tween(64))
            shake.animateTo(-0.6f, tween(56))
            shake.animateTo(0.6f, tween(56))
            shake.animateTo(0f, spring(stiffness = 600f))
            input = ""
        }
    }

    fun submit(pin: String) {
        when (stage) {
            Stage.SETUP -> {
                firstPin = pin
                error = null
                input = ""
                stage = Stage.CONFIRM
            }
            Stage.CONFIRM -> {
                if (pin == firstPin) {
                    if (!onCreate(pin)) {
                        stage = Stage.SETUP
                        firstPin = null
                        fail("创建失败，请重试")
                    }
                } else {
                    firstPin = null
                    stage = Stage.SETUP
                    fail("两次输入不一致，请重新设置")
                }
            }
            Stage.UNLOCK -> {
                if (!onUnlock(pin)) {
                    attempts += 1
                    fail(if (attempts >= 3) "PIN 不正确（已试 $attempts 次）" else "PIN 不正确")
                }
            }
        }
    }

    fun push(c: Char) {
        if (input.length >= PIN_LEN) return
        error = null
        input += c
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (input.length == PIN_LEN) {
            val pin = input
            // 让最后一位圆点先画出来再提交
            scope.launch {
                kotlinx.coroutines.delay(90)
                submit(pin)
            }
        }
    }

    // 进入确认阶段时清掉提示
    LaunchedEffect(stage) {
        if (stage == Stage.CONFIRM) error = null
    }

    // 呼吸光晕：锁屏静止时也有生命感
    val glow = rememberInfiniteTransition(label = "lockGlow")
    val glowV by glow.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = AppEase), RepeatMode.Reverse),
        label = "lockGlowV"
    )
    val logoIn = remember { Animatable(0.82f) }
    LaunchedEffect(Unit) {
        logoIn.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 240f))
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val r1 = size.width * 0.95f
            val c1 = Offset(size.width * 0.10f, size.height * 0.08f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(pal.accent.copy(alpha = 0.20f * glowV), Color.Transparent),
                    center = c1,
                    radius = r1
                ),
                radius = r1,
                center = c1
            )
            val r2 = size.width * 0.85f
            val c2 = Offset(size.width * 0.95f, size.height * 0.72f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(pal.accent2.copy(alpha = 0.13f * glowV), Color.Transparent),
                    center = c2,
                    radius = r2
                ),
                radius = r2,
                center = c2
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.9f))

            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = logoIn.value
                    scaleY = logoIn.value
                    alpha = ((logoIn.value - 0.82f) / 0.18f).coerceIn(0f, 1f)
                }
            ) {
                AppLogo(size = 62.dp)
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "钥匣",
                style = MaterialTheme.typography.headlineMedium,
                color = pal.text,
                modifier = Modifier.staggerIn(1, stepMs = 55)
            )

            Spacer(Modifier.height(8.dp))

            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (fadeIn(tween(220, easing = AppEase)) +
                        androidx.compose.animation.slideInVertically(tween(240, easing = AppEase)) { it / 3 })
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "stageHint"
            ) { st ->
                Text(
                    text = when (st) {
                        Stage.SETUP -> "设置 6 位访问 PIN"
                        Stage.CONFIRM -> "再次输入以确认"
                        Stage.UNLOCK -> if (onBiometric != null) "指纹一键进入，或输入 PIN"
                        else "输入 PIN 解锁保险箱"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = pal.text2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.staggerIn(2, stepMs = 55)
                )
            }

        Spacer(Modifier.height(38.dp))

        Box(modifier = Modifier.offset(x = (shake.value * 11).dp)) {
            PinDots(
                length = PIN_LEN,
                filled = input.length,
                error = error != null
            )
        }

        Spacer(Modifier.height(14.dp))

        Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = error != null,
                enter = fadeIn(tween(180, easing = AppEase)) +
                    androidx.compose.animation.slideInVertically(tween(200)) { -it / 2 },
                exit = fadeOut(tween(120))
            ) {
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = pal.danger,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.weight(0.45f))

        PinKeypad(
            onDigit = ::push,
            onBackspace = {
                if (input.isNotEmpty()) {
                    input = input.dropLast(1)
                    error = null
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            onBiometric = if (stage == Stage.UNLOCK) onBiometric else null,
            enabled = true
        )

        Spacer(Modifier.height(30.dp))

        Text(
            text = "所有密钥仅加密保存在本机",
            style = MaterialTheme.typography.labelSmall,
            color = pal.text3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(22.dp))
        }
    }
}

private enum class Stage { SETUP, CONFIRM, UNLOCK }