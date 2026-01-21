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
    
    // 获取应用版本号
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
            } ?: "1.0.0"
        } catch (_ : Exception) {
            "1.0.0"
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

    val showExportDialog = remember { mutableStateOf(false) }
    val showImportDialog = remember { mutableStateOf(false) }
    val backupPassword = remember { mutableStateOf("") }
    val pendingImportUri = remember { mutableStateOf<android.net.Uri?>(null) }

    // --- 导出执行 ---
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

    // --- 导入逻辑 ---
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri.value = it
            showImportDialog.value = true
        }
    }

    // 导出密码输入对话框
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
                        label = "密码",
                        trailingIcon = null
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

    // 导入密码输入对话框
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
                        label = "密码",
                        trailingIcon = null
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
            // --- 安全设置 ---
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
                    // 自动锁定时间配置
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

            // --- 数据管理 ---
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

            // --- 关于我们 ---
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
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    AboutItem(Icons.Default.Person, "作者", "Yiran")
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
fun AboutItem(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
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
        Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (onClick != null) FontWeight.Bold else FontWeight.Normal
        )
    }
}
