package com.yiran.cerberus.ui.home

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yiran.cerberus.util.SecurityUtil
import java.io.OutputStreamWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, homeViewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    
    val versionName = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } ?: "1.1.0"
        } catch (_ : Exception) {
            "1.1.0"
        }
    }

    var isBiometricEnabled by remember {
        mutableStateOf(SecurityUtil.isBiometricEnabled(context))
    }
    val canUseBiometric = remember { SecurityUtil.canUseBiometric(context) }
    
    var autoLockTime by remember {
        mutableLongStateOf(SecurityUtil.getAutoLockTime(context))
    }
    var showTimeMenu by remember { mutableStateOf(false) }

    // 使用 .value 显式访问，解决警告并修复逻辑错误
    val showExportDialog = remember { mutableStateOf(false) }
    val showImportDialog = remember { mutableStateOf(false) }
    val backupPassword = remember { mutableStateOf("") }
    val pendingImportUri = remember { mutableStateOf<android.net.Uri?>(null) }
    
    var isUpdateCheckAllowed by remember { mutableStateOf(SecurityUtil.isUpdateCheckAllowed(context)) }
    val showConsentDialog = remember { mutableStateOf(false) }
    val isCheckingUpdate = remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            try {
                val encryptedData = homeViewModel.exportBackup(backupPassword.value)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(encryptedData)
                    }
                }
                Toast.makeText(context, "加密备份导出成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                backupPassword.value = ""
            }
        } else {
            backupPassword.value = ""
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri.value = it
            showImportDialog.value = true
        }
    }

    if (showExportDialog.value) {
        StyledDialog(
            onDismissRequest = {
                showExportDialog.value = false
                backupPassword.value = ""
            },
            title = "设置备份密码",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("请输入用于加密备份文件的密码，恢复时需要此密码。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StyledTextField(
                        value = backupPassword.value,
                        onValueChange = { backupPassword.value = it },
                        label = "密码"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (backupPassword.value.isNotBlank()) {
                            val fileName = "Cerberus_Backup_${System.currentTimeMillis()}.cerb"
                            createDocumentLauncher.launch(fileName)
                            showExportDialog.value = false
                        }
                    }
                ) { Text("确定", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportDialog.value = false
                    backupPassword.value = ""
                }) { Text("取消") }
            }
        )
    }

    if (showImportDialog.value) {
        StyledDialog(
            onDismissRequest = {
                showImportDialog.value = false
                backupPassword.value = ""
            },
            title = "输入备份密码",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("该备份文件已加密，请输入正确的密码进行解密并恢复数据。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StyledTextField(
                        value = backupPassword.value,
                        onValueChange = { backupPassword.value = it },
                        label = "密码"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportUri.value?.let { uri ->
                            homeViewModel.importBackup(
                                context = context,
                                uri = uri,
                                password = backupPassword.value,
                                onSuccess = {
                                    showImportDialog.value = false
                                    backupPassword.value = ""
                                    Toast.makeText(context, "数据恢复成功", Toast.LENGTH_SHORT).show()
                                },
                                onError = { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                ) { Text("恢复", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportDialog.value = false
                    backupPassword.value = ""
                }) { Text("取消") }
            }
        )
    }

    if (showConsentDialog.value) {
        StyledDialog(
            onDismissRequest = { showConsentDialog.value = false },
            title = "软件更新偏好说明",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Cerberus 默认不请求任何联网权限。为了方便您获取最新功能和安全补丁，您可以选择开启\"检查更新\"功能：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• 开启后，仅在您手动点击时访问 GitHub API\n• 我们绝不会收集个人信息或上传您的令牌数据\n• 未经您的明确允许，应用绝不会在后台静默联网",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    SecurityUtil.setUpdateCheckAllowed(context, true)
                    isUpdateCheckAllowed = true
                    showConsentDialog.value = false
                }) { Text("同意开启", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    SecurityUtil.setUpdateCheckAllowed(context, false)
                    isUpdateCheckAllowed = false
                    showConsentDialog.value = false
                }) { Text("保持离线") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "安全",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AboutItem(
                            icon = Icons.Default.Timer,
                            label = "自动锁定超时",
                            value = when(autoLockTime) {
                                15000L -> "15 秒"
                                30000L -> "30 秒"
                                60000L -> "60 秒"
                                else -> "${autoLockTime / 1000} 秒"
                            },
                            onClick = { showTimeMenu = true }
                        )
                        
                        DropdownMenu(
                            expanded = showTimeMenu,
                            onDismissRequest = { showTimeMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            listOf(15000L, 30000L, 60000L).forEach { time ->
                                DropdownMenuItem(
                                    text = { Text("${time / 1000} 秒", fontWeight = FontWeight.Medium) },
                                    onClick = {
                                        SecurityUtil.setAutoLockTime(context, time)
                                        autoLockTime = time
                                        showTimeMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "指纹/生物识别解锁", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                text = if (canUseBiometric) "使用设备生物识别快速解锁应用" else "您的设备不支持生物识别",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { enabled ->
                                SecurityUtil.setBiometricEnabled(context, enabled)
                                isBiometricEnabled = enabled
                            },
                            enabled = canUseBiometric
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "数据管理",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    AboutItem(
                        icon = Icons.Default.FileDownload,
                        label = "导出加密备份",
                        value = "导出",
                        onClick = { showExportDialog.value = true }
                    )

                    AboutItem(
                        icon = Icons.Default.FileUpload,
                        label = "导入加密备份",
                        value = "恢复",
                        onClick = { openDocumentLauncher.launch(arrayOf("*/*")) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "关于 Cerberus",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Cerberus 是一款专注于本地隐私的身份验证工具。我们坚持零联网权限，所有加密数据均存储在您的物理设备中。",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    AboutItem(
                        icon = if (isUpdateCheckAllowed) Icons.Default.Update else Icons.Default.CloudOff,
                        label = "检查更新",
                        value = if (isCheckingUpdate.value) "检查中..." else if (isUpdateCheckAllowed) "获取最新版" else "已禁用",
                        onClick = if (isUpdateCheckAllowed && !isCheckingUpdate.value) {
                            {
                                isCheckingUpdate.value = true
                                homeViewModel.checkUpdate(
                                    currentVersion = versionName,
                                    onResult = { hasUpdate, latest ->
                                        isCheckingUpdate.value = false
                                        if (hasUpdate) {
                                            val intent = Intent(Intent.ACTION_VIEW, "https://github.com/Ranpers/Cerberus/releases/latest".toUri())
                                            context.startActivity(intent)
                                            Toast.makeText(context, "发现新版本: v$latest，正在跳转 GitHub...", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onError = { error ->
                                        isCheckingUpdate.value = false
                                        Toast.makeText(context, "检查失败: $error", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        } else null
                    )
                    
                    if (!isUpdateCheckAllowed) {
                        TextButton(
                            onClick = { showConsentDialog.value = true },
                            modifier = Modifier.padding(start = 48.dp)
                        ) {
                            Text(
                                "为何禁用？查看条款并开启",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        TextButton(
                            onClick = { 
                                SecurityUtil.setUpdateCheckAllowed(context, false)
                                isUpdateCheckAllowed = false
                                Toast.makeText(context, "已取消联网授权，恢复离线状态", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.padding(start = 48.dp)
                        ) {
                            Text(
                                "取消联网授权并恢复离线",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    AboutItem(
                        icon = Icons.Default.Person, 
                        label = "作者", 
                        value = "Yiran",
                        highlightValue = true
                    )
                    AboutItem(Icons.Default.Email, "反馈邮箱", "yi_ran@aliyun.com") {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = "mailto:yi_ran@aliyun.com".toUri()
                            putExtra(Intent.EXTRA_SUBJECT, "Cerberus 意见反馈")
                        }
                        context.startActivity(Intent.createChooser(intent, "发送邮件"))
                    }
                    AboutItem(Icons.Default.Link, "GitHub", "项目仓库") {
                        val intent = Intent(Intent.ACTION_VIEW, "https://github.com/Ranpers/Cerberus".toUri())
                        context.startActivity(intent)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Version $versionName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Made with ❤️ for Privacy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AboutItem(
    icon: ImageVector, 
    label: String, 
    value: String, 
    highlightValue: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label, 
            style = MaterialTheme.typography.bodyLarge, 
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null || highlightValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (onClick != null || highlightValue) FontWeight.Bold else FontWeight.Normal
        )
    }
}
