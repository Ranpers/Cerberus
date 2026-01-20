package com.yiran.cerberus

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.yiran.cerberus.ui.home.HomeScreen
import com.yiran.cerberus.ui.home.SettingsScreen
import com.yiran.cerberus.ui.theme.CerberusTheme
import com.yiran.cerberus.util.SecurityUtil
import kotlinx.coroutines.CancellationException

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CerberusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val activity = LocalActivity.current as FragmentActivity
                    
                    var isUnlocked by rememberSaveable { mutableStateOf(false) }
                    var showPasswordInput by rememberSaveable { mutableStateOf(false) }
                    var showTerms by rememberSaveable { mutableStateOf(false) }
                    var currentScreen by rememberSaveable { mutableStateOf("home") }
                    var isCheckingAuth by remember { mutableStateOf(true) }

                    // 动画进度状态
                    var backProgress by remember { mutableFloatStateOf(0f) }

                    val triggerBiometric = {
                        showBiometricPrompt(activity,
                            onSuccess = { 
                                isUnlocked = true 
                                isCheckingAuth = false
                                showPasswordInput = false
                            },
                            onCancel = { 
                                showPasswordInput = true 
                                isCheckingAuth = false
                            }
                        )
                    }

                    // 监听应用全局生命周期
                    DisposableEffect(Unit) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_START -> {
                                    // 从后台返回时检查是否需要重新锁定
                                    if (SecurityUtil.isTermsAccepted(context) && 
                                        SecurityUtil.isMasterPasswordSet(context) &&
                                        SecurityUtil.shouldReauthenticate(context)) {
                                        isUnlocked = false
                                        isCheckingAuth = true
                                        if (SecurityUtil.isBiometricEnabled(context) && SecurityUtil.canUseBiometric(context)) {
                                            triggerBiometric()
                                        } else {
                                            showPasswordInput = true
                                            isCheckingAuth = false
                                        }
                                    } else if (isUnlocked) {
                                        // 还没到超时时间，保持解锁
                                        isCheckingAuth = false
                                    }
                                }
                                Lifecycle.Event.ON_STOP -> {
                                    // 进入后台时记录时间
                                    SecurityUtil.markEnterBackground()
                                }
                                else -> {}
                            }
                        }
                        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
                        onDispose {
                            ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
                        }
                    }

                    // 初始检查
                    LaunchedEffect(Unit) {
                        if (!SecurityUtil.isTermsAccepted(context)) {
                            showTerms = true
                            isCheckingAuth = false
                        } else if (!SecurityUtil.isMasterPasswordSet(context)) {
                            showPasswordInput = true
                            isCheckingAuth = false
                        } else if (!isUnlocked) {
                            if (SecurityUtil.isBiometricEnabled(context) && SecurityUtil.canUseBiometric(context)) {
                                triggerBiometric()
                            } else {
                                showPasswordInput = true
                                isCheckingAuth = false
                            }
                        } else {
                            isCheckingAuth = false
                        }
                    }

                    // 处理返回键：只有在已解锁且在设置页面时才拦截
                    if (isUnlocked && currentScreen == "settings") {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            PredictiveBackHandler { progress ->
                                try {
                                    progress.collect { backEvent ->
                                        backProgress = backEvent.progress
                                    }
                                    currentScreen = "home"
                                    backProgress = 0f
                                } catch (_ : CancellationException) {
                                    backProgress = 0f
                                }
                            }
                        } else {
                            BackHandler { currentScreen = "home" }
                        }
                    }

                    Box(modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (backProgress > 0f) {
                                val scale = 1f - (backProgress * 0.05f)
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - (backProgress * 0.2f)
                            }
                        }
                    ) {
                        when {
                            isUnlocked -> {
                                if (currentScreen == "home") {
                                    HomeScreen(onSettingsClick = { currentScreen = "settings" })
                                } else {
                                    SettingsScreen(onBack = { currentScreen = "home" })
                                }
                            }
                            showTerms -> {
                                TermsAndConditionsScreen(onAccepted = {
                                    SecurityUtil.setTermsAccepted(context)
                                    showTerms = false
                                    if (!SecurityUtil.isMasterPasswordSet(context)) {
                                        showPasswordInput = true
                                    } else {
                                        isCheckingAuth = true
                                        triggerBiometric()
                                    }
                                })
                            }
                            isCheckingAuth -> {
                                SplashPlaceholder()
                            }
                            showPasswordInput -> {
                                MasterPasswordScreen(
                                    onUnlockSuccess = { 
                                        val isFirstTime = !SecurityUtil.isMasterPasswordSet(context)
                                        isUnlocked = true
                                        if (isFirstTime && SecurityUtil.canUseBiometric(context)) {
                                            SecurityUtil.setBiometricEnabled(context, true)
                                        }
                                    },
                                    onBiometricRequest = { triggerBiometric() }
                                )
                            }
                            else -> {
                                LockScreen(
                                    onPasswordClick = { showPasswordInput = true },
                                    onBiometricClick = { triggerBiometric() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showBiometricPrompt(
        activity: FragmentActivity, 
        onSuccess: () -> Unit,
        onCancel: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // 用户取消验证（如点击取消按钮或手势返回）
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || 
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onCancel()
                    } else {
                        // 其他错误（如尝试次数过多）也退回到密码输入
                        onCancel()
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Cerberus 安全身份验证")
            .setSubtitle("使用生物识别快速解锁")
            .setNegativeButtonText("使用密码")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

@Composable
fun TermsAndConditionsScreen(onAccepted: () -> Unit) {
    var checked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "欢迎使用 Cerberus",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "在开始保护您的令牌库之前，请阅读并同意以下安全与免责声明：",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                TermItem("零网络通信", "Cerberus 坚持零联网权限设计。所有数据均经 AES 硬件级加密后存储于您的物理设备中，绝不上传云端。")
                TermItem("主密码唯一性", "主密码是访问加密数据库的唯一凭证。应用无法找回或重置主密码。若遗忘主密码，您的所有数据将永久无法读取。")
                TermItem("备份风险提示", "导出的加密备份文件（.cerb）由您设置的备份密码保护。请务必记住此密码。若备份文件丢失、损坏或遗忘备份密码导致无法恢复，作者不承担任何责任。")
                TermItem("免责声明", "由于卸载应用、清除缓存、刷机、设备损坏、系统故障或用户操作失误（如误删、操作不当）导致的数据丢失，属于用户个人风险，与作者及开发者无关。")
                TermItem("责任边界", "本应用作为开源工具提供。严禁对应用进行篡改。因系统环境异常、第三方破解软件或任何形式的非正常操作导致的数据泄露或损坏，作者概不负责。")
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { checked = it }
            )
            Text(
                text = "我已阅读、理解并同意上述安全条款及免责声明，愿自行承担一切数据管理风险。",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Button(
            onClick = onAccepted,
            enabled = checked,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("同意并开始使用", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun TermItem(title: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Info, 
                contentDescription = null, 
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 24.dp, top = 2.dp),
            lineHeight = 16.sp
        )
    }
}

@Composable
fun SplashPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Cerberus",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MasterPasswordScreen(onUnlockSuccess: () -> Unit, onBiometricRequest: () -> Unit) {
    val context = LocalContext.current
    val isFirstTime = remember { !SecurityUtil.isMasterPasswordSet(context) }
    
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isFirstTime) "初始化 Cerberus" else "主密码认证",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = if (isFirstTime) "请设置一个主密码。它将用于加密您的所有令牌。" else "请输入主密码以解锁您的令牌库。",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorText = "" },
            label = { Text("主密码") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            isError = errorText.isNotEmpty(),
            trailingIcon = {
                if (!isFirstTime && SecurityUtil.canUseBiometric(context)) {
                    IconButton(onClick = onBiometricRequest) {
                        Icon(Icons.Default.Fingerprint, contentDescription = "Biometric", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        )

        if (isFirstTime) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; errorText = "" },
                label = { Text("确认密码") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                isError = errorText.isNotEmpty()
            )
        }

        if (errorText.isNotEmpty()) {
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (password.isBlank()) {
                    errorText = "请输入密码"
                    return@Button
                }
                if (isFirstTime) {
                    if (password.length < 6) {
                        errorText = "密码至少需要 6 位"
                    } else if (password != confirmPassword) {
                        errorText = "两次输入的密码不匹配"
                    } else {
                        SecurityUtil.setMasterPassword(context, password)
                        onUnlockSuccess()
                    }
                } else {
                    if (SecurityUtil.verifyMasterPassword(context, password)) {
                        onUnlockSuccess()
                    } else {
                        errorText = "主密码不正确"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isFirstTime) "设置并进入" else "验证解锁")
        }

        if (!isFirstTime && SecurityUtil.canUseBiometric(context)) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onBiometricRequest) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("使用指纹解锁")
            }
        }
    }
}

@Composable
fun LockScreen(onPasswordClick: () -> Unit, onBiometricClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Locked",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "App 已安全锁定", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(48.dp))
        
        if (SecurityUtil.canUseBiometric(context)) {
            Button(onClick = onBiometricClick, modifier = Modifier.fillMaxWidth(0.7f)) {
                Text("指纹解锁")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        Button(onClick = onPasswordClick, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text("密码解锁")
        }
    }
}
