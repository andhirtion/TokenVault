package com.yg.tokenvault

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yg.tokenvault.data.OtpImporter

/**
 * 单 Activity 宿主。全部界面走 Compose。
 *
 * 额外职责：接收从浏览器 / 其他应用点开的 otpauth:// 与 otpauth-migration:// 链接，
 * 直接导入到令牌库（需已解锁）。
 */
class MainActivity : FragmentActivity() {

    private var pendingUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingUri = intent?.data?.toString()
        setContent {
            val vm: VaultViewModel = viewModel()
            TokenVaultApp(vm)
            ConsumePendingUri(vm)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingUri = intent.data?.toString()
    }

    @Composable
    private fun ConsumePendingUri(vm: VaultViewModel) {
        LaunchedEffect(pendingUri) {
            val uri = pendingUri ?: return@LaunchedEffect
            pendingUri = null
            vm.importAnyText(uri, OtpImporter.Origin.LINK)
        }
    }
}