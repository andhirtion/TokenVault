package com.yg.tokenvault.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.tokenvault.ui.theme.LocalPalette
import com.yg.tokenvault.util.QrDecoder

/**
 * 「获取密钥」方式选择器。
 *
 * 四条路：相机扫码、相册图片识别、内置浏览器登录自动抓取、剪贴板粘贴。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(
    onDismiss: () -> Unit,
    onText: (String) -> Unit,
    onScan: () -> Unit,
    onWebLogin: () -> Unit,
    onToast: (String) -> Unit
) {
    val pal = LocalPalette.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?.let { QrDecoder.fromBytes(it) }
        } catch (e: Throwable) {
            null
        }
        if (text.isNullOrBlank()) {
            onToast("这张图片里没识别到二维码")
        } else {
            onText(text)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = pal.surface,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 20.dp, bottom = 26.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(pal.accent.copy(alpha = 0.9f), pal.accent2.copy(alpha = 0.9f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "获取密钥",
                        style = MaterialTheme.typography.titleLarge,
                        color = pal.text
                    )
                    Text(
                        text = "四条路，任选一条",
                        style = MaterialTheme.typography.labelSmall,
                        color = pal.text3
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            OptionLine(
                icon = Icons.Outlined.QrCodeScanner,
                title = "扫二维码",
                subtitle = "对准账号里的验证器二维码，支持 Google / Microsoft / GitHub / Steam",
                highlight = true
            ) {
                onDismiss()
                onScan()
            }

            OptionLine(
                icon = Icons.Outlined.Language,
                title = "登录账号自动获取",
                subtitle = "在应用内登录，走到「启用验证器」那步，自动抓下密钥",
                highlight = true
            ) {
                onDismiss()
                onWebLogin()
            }

            OptionLine(
                icon = Icons.Outlined.Image,
                title = "从图片识别",
                subtitle = "把二维码截图或保存成图片，选图自动识别"
            ) {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }

            OptionLine(
                icon = Icons.Outlined.ContentPaste,
                title = "从剪贴板粘贴",
                subtitle = "复制 otpauth:// 链接、Google 导出码或 Base32 密钥后点这里"
            ) {
                val t = clipboard.getText()?.text
                if (t.isNullOrBlank()) {
                    onToast("剪贴板是空的")
                } else {
                    onText(t)
                    onDismiss()
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "密钥只在本机解密使用，不会上传到任何服务器",
                style = MaterialTheme.typography.labelSmall,
                color = pal.text3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OptionLine(
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    val pal = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) pal.accent.copy(alpha = 0.09f) else pal.surfaceHi.copy(alpha = 0.55f))
            .androidClickable(onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(pal.accent.copy(alpha = if (highlight) 0.2f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = pal.accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = pal.text
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                color = pal.text3
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = pal.text3,
            modifier = Modifier.size(18.dp)
        )
    }
    Spacer(Modifier.height(9.dp))
}