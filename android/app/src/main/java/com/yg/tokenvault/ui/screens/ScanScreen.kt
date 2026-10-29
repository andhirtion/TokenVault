package com.yg.tokenvault.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.yg.tokenvault.ui.theme.LocalPalette
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 相机扫码页。
 *
 * 识别 otpauth:// 、otpauth-migration:// 与任意含密钥的二维码，直接把原文回传上层解析。
 */
@Composable
fun ScanScreen(
    onDismiss: () -> Unit,
    onDecoded: (String) -> Unit,
    onPickAlbum: () -> Unit
) {
    val pal = LocalPalette.current
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        asked = true
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraPreview(onDecoded = onDecoded)
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = null,
                    tint = pal.accent,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = if (asked) "没有相机权限，无法扫码" else "需要相机权限才能扫二维码",
                    style = MaterialTheme.typography.titleMedium,
                    color = pal.text,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "也可以从相册选一张二维码截图",
                    style = MaterialTheme.typography.bodyMedium,
                    color = pal.text3,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "重新授权",
                        style = MaterialTheme.typography.labelLarge,
                        color = pal.accent,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(pal.accent.copy(alpha = 0.16f))
                            .androidClickable { launcher.launch(Manifest.permission.CAMERA) }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "从相册选择",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(pal.accent, pal.accent2)))
                            .androidClickable { onPickAlbum() }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // 取景框 + 扫描线
        ScanOverlay(modifier = Modifier.fillMaxSize())

        // 顶部说明与关闭
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .androidClickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "对准二维码",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "支持 otpauth:// 链接、Google Authenticator 导出码、以及任意含密钥的二维码",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f)
            )
        }

        // 底部：相册兜底 + 提示
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 26.dp)
                .padding(bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(pal.accent, pal.accent2)))
                    .androidClickable { onPickAlbum() }
                    .padding(horizontal = 24.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "从相册选择",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(13.dp))
            Text(
                text = "识别成功会自动填充密钥并返回",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ScanOverlay(modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "scanline")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val side = size.minDimension * 0.68f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val strokeW = 3.dp.toPx()
        val armLen = side * 0.22f

        // 四周压暗
        val scrim = Color.Black.copy(alpha = 0.55f)
        drawRect(scrim, size = Size(size.width, top))
        drawRect(scrim, topLeft = Offset(0f, top + side), size = Size(size.width, size.height - top - side))
        drawRect(scrim, topLeft = Offset(0f, top), size = Size(left, side))
        drawRect(scrim, topLeft = Offset(left + side, top), size = Size(size.width - left - side, side))

        // 四个角
        val c = pal.accent
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(c, Offset(x, y), Offset(x + dx * armLen, y), strokeWidth = strokeW)
            drawLine(c, Offset(x, y), Offset(x, y + dy * armLen), strokeWidth = strokeW)
        }
        corner(left, top, 1f, 1f)
        corner(left + side, top, -1f, 1f)
        corner(left, top + side, 1f, -1f)
        corner(left + side, top + side, -1f, -1f)

        // 细边框
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(left, top),
            size = Size(side, side),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )

        // 扫描线
        val lineY = top + side * phase
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, c.copy(alpha = 0.75f), Color.Transparent),
                startY = lineY - 26.dp.toPx(),
                endY = lineY + 26.dp.toPx()
            ),
            topLeft = Offset(left + 2f, lineY - 26.dp.toPx()),
            size = Size(side - 4f, 52.dp.toPx())
        )
    }
}

@Composable
private fun CameraPreview(onDecoded: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val done = remember { AtomicBoolean(false) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { proxy ->
                        if (done.get()) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        val text = try {
                            decodeQr(proxy)
                        } catch (e: Throwable) {
                            null
                        }
                        proxy.close()
                        if (!text.isNullOrBlank() && done.compareAndSet(false, true)) {
                            previewView.post { onDecoded(text) }
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (e: Throwable) {
                    // 没有可用相机时保持黑屏，用户可返回手动输入
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            done.set(true)
            executor.shutdown()
        }
    }
}

/** 把 ImageProxy 的 Y 平面喂给 ZXing，只认二维码 */
private fun decodeQr(image: ImageProxy): String? {
    val media = image.image ?: return null
    val plane = media.planes.firstOrNull() ?: return null
    val w = media.width
    val h = media.height
    if (w <= 0 || h <= 0) return null

    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val data = ByteArray(w * h)

    if (pixelStride == 1 && rowStride == w) {
        val n = minOf(buffer.remaining(), data.size)
        buffer.get(data, 0, n)
    } else {
        val rowBuf = ByteArray(rowStride)
        for (y in 0 until h) {
            val remain = buffer.remaining()
            if (remain <= 0) break
            val toRead = minOf(rowStride, remain)
            buffer.get(rowBuf, 0, toRead)
            System.arraycopy(rowBuf, 0, data, y * w, minOf(w, toRead))
        }
    }

    val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    // 先原样试；失败再按 90° 转置试一次（部分机型 sensor 方向与 buffer 不一致）
    decodeLuma(data, w, h, hints, false)?.let { return it }
    return decodeLuma(data, w, h, hints, true)
}

private fun decodeLuma(
    data: ByteArray,
    w: Int,
    h: Int,
    hints: Map<DecodeHintType, Any>,
    rotate: Boolean
): String? {
    val (srcW, srcH, src) = if (!rotate) {
        Triple(w, h, data)
    } else {
        val out = ByteArray(data.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                out[x * h + y] = data[y * w + x]
            }
        }
        Triple(h, w, out)
    }
    return try {
        val source = PlanarYUVLuminanceSource(src, srcW, srcH, 0, 0, srcW, srcH, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        MultiFormatReader().apply { setHints(hints) }.decodeWithState(bitmap).text
    } catch (e: Throwable) {
        null
    }
}