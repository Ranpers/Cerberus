package com.yiran.cerberus.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yiran.cerberus.util.OtpAlgorithm
import com.yiran.cerberus.util.PasswordGenerator
import com.yiran.cerberus.util.TotpUtil
import kotlinx.coroutines.delay
import uniffi.rust_core.Account

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onSettingsClick: () -> Unit, homeViewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val accounts = homeViewModel.accounts
    val lifecycleOwner = LocalLifecycleOwner.current

    val showAddDialog = remember { mutableStateOf(false) }
    val showDeleteDialog = remember { mutableStateOf(false) }
    val showEditPasswordDialog = remember { mutableStateOf(false) }
    val selectedAccount = remember { mutableStateOf<Account?>(null) }

    var currentProgress by remember { mutableFloatStateOf(1f) }
    var currentStep by remember { mutableLongStateOf(System.currentTimeMillis() / 30000) }
    var isAppVisible by remember { mutableStateOf(true) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isAppVisible = event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        homeViewModel.loadAccounts(context)
    }

    LaunchedEffect(isAppVisible) {
        if (isAppVisible) {
            while (true) {
                currentProgress = TotpUtil.getProgress()
                currentStep = System.currentTimeMillis() / 30000
                delay(100)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Cerberus", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog.value = true },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Account")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (accounts.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(accounts, key = { it.id }) { account ->
                        AccountItemCard(
                            account = account,
                            progress = currentProgress,
                            step = currentStep,
                            onEditPasswordClick = {
                                selectedAccount.value = account
                                showEditPasswordDialog.value = true
                            },
                            onDeleteClick = {
                                selectedAccount.value = account
                                showDeleteDialog.value = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog.value) {
        AddAccountDialog(
            onDismiss = { showAddDialog.value = false },
            onConfirm = { newAccount ->
                homeViewModel.addAccount(context, newAccount)
                showAddDialog.value = false
            }
        )
    }

    if (showEditPasswordDialog.value && selectedAccount.value != null) {
        EditPasswordDialog(
            account = selectedAccount.value!!,
            onDismiss = { showEditPasswordDialog.value = false },
            onConfirm = { newPassword ->
                homeViewModel.updatePassword(context, selectedAccount.value!!.id, newPassword)
                showEditPasswordDialog.value = false
            }
        )
    }

    if (showDeleteDialog.value && selectedAccount.value != null) {
        DeleteConfirmDialog(
            accountName = selectedAccount.value!!.name,
            onDismiss = { showDeleteDialog.value = false },
            onConfirm = {
                homeViewModel.deleteAccount(context, selectedAccount.value!!)
                showDeleteDialog.value = false
            }
        )
    }
}

@Composable
fun AccountItemCard(
    account: Account,
    progress: Float,
    step: Long,
    onEditPasswordClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val menuExpanded = remember { mutableStateOf(false) }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = account.iconInitial,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = account.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(32.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (account.username.isNotEmpty() || account.password.isNotEmpty()) {
                    PasswordSection(account)
                }

                if (account.hasOtp && account.secretKey.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OtpSection(account, progress, step)
                }
            }

            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)) {
                IconButton(onClick = { menuExpanded.value = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded.value, 
                    onDismissRequest = { menuExpanded.value = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    DropdownMenuItem(
                        text = { Text("修改密码", fontWeight = FontWeight.Medium) },
                        onClick = { menuExpanded.value = false; onEditPasswordClick() },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("删除记录", fontWeight = FontWeight.Medium) },
                        onClick = { menuExpanded.value = false; onDeleteClick() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error, leadingIconColor = MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
    }
}

@Composable
fun StyledDialog(
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(32.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    content()
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    confirmButton()
                }
            }
        }
    }
}

@Composable
fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String = "",
    trailingIcon: @Composable (() -> Unit)? = null,
    readOnly: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        supportingText = if (supportingText.isNotEmpty()) { { Text(supportingText) } } else null,
        modifier = modifier.fillMaxWidth(),
        trailingIcon = trailingIcon,
        readOnly = readOnly,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            errorContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        )
    )
}

@Composable
fun EditPasswordDialog(
    account: Account,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val newPassword = remember { mutableStateOf(account.password) }
    val passwordError = remember { mutableStateOf("") }
    var includeSpecial by remember { mutableStateOf(true) }

    StyledDialog(
        onDismissRequest = onDismiss,
        title = "修改静态密码",
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "正在为 ${account.name} (${account.username}) 修改登录密码。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StyledTextField(
                    value = newPassword.value,
                    onValueChange = {
                        newPassword.value = it
                        passwordError.value = ""
                    },
                    label = "新密码",
                    isError = passwordError.value.isNotEmpty(),
                    supportingText = passwordError.value,
                    trailingIcon = {
                        IconButton(onClick = {
                            newPassword.value = PasswordGenerator.generate(includeSpecial = includeSpecial)
                            passwordError.value = ""
                        }) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = "随机生成", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { includeSpecial = !includeSpecial }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeSpecial,
                        onCheckedChange = { includeSpecial = it }
                    )
                    Text("包含特殊符号", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newPassword.value.isBlank()) {
                        passwordError.value = "密码不能为空"
                    } else {
                        onConfirm(newPassword.value)
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("保存", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun OtpSection(account: Account, progress: Float, step: Long) {
    val context = LocalContext.current
    val otpCode = remember(step, account.secretKey) {
        TotpUtil.generateTOTP(account.secretKey, account.algorithm)
    }

    val remainingSeconds = TotpUtil.getRemainingSeconds()
    val progressColor by animateColorAsState(
        targetValue = if (remainingSeconds <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "progressColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable {
                copyToClipboard(context, otpCode, "验证码已复制")
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val displayCode = if (otpCode.length == 6) "${otpCode.take(3)} ${otpCode.substring(3)}" else otpCode
        Text(
            text = displayCode,
            style = MaterialTheme.typography.headlineMedium,
            color = progressColor,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "${remainingSeconds}s", style = MaterialTheme.typography.labelMedium, color = progressColor, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(16.dp),
                strokeWidth = 3.dp,
                color = progressColor,
                trackColor = progressColor.copy(alpha = 0.2f),
            )
        }
    }
}

@Composable
fun PasswordSection(account: Account) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(
            onClick = { copyToClipboard(context, account.username, "账号已复制") },
            modifier = Modifier.weight(1f).height(40.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("复制账号", style = MaterialTheme.typography.labelLarge)
        }

        if (account.password.isNotEmpty()) {
            TextButton(
                onClick = { copyToClipboard(context, account.password, "密码已复制") },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("复制密码", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (Account) -> Unit
) {
    val step = remember { mutableIntStateOf(1) } // 1: 基本信息, 2: 2FA 配置

    val name = remember { mutableStateOf("") }
    val username = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    var includeSpecial by remember { mutableStateOf(true) }
    val hasOtp = remember { mutableStateOf(false) }

    val secret = remember { mutableStateOf("") }
    val selectedAlgo = remember { mutableStateOf(OtpAlgorithm.SHA1) }

    val expanded = remember { mutableStateOf(false) }
    val nameError = remember { mutableStateOf("") }
    val usernameError = remember { mutableStateOf("") }
    val passwordError = remember { mutableStateOf("") }
    val secretError = remember { mutableStateOf("") }

    StyledDialog(
        onDismissRequest = onDismiss,
        title = if (step.intValue == 1) "添加新凭据" else "配置 2FA",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step.intValue == 1) {
                    StyledTextField(
                        value = name.value,
                        onValueChange = { name.value = it; nameError.value = "" },
                        label = "服务名称 (如: Github)",
                        isError = nameError.value.isNotEmpty(),
                        supportingText = nameError.value
                    )
                    StyledTextField(
                        value = username.value,
                        onValueChange = { username.value = it; usernameError.value = "" },
                        label = "账号 / 用户名",
                        isError = usernameError.value.isNotEmpty(),
                        supportingText = usernameError.value
                    )
                    Column {
                        StyledTextField(
                            value = password.value,
                            onValueChange = { password.value = it; passwordError.value = "" },
                            label = "登录密码",
                            isError = passwordError.value.isNotEmpty(),
                            supportingText = passwordError.value,
                            trailingIcon = {
                                IconButton(onClick = {
                                    password.value = PasswordGenerator.generate(includeSpecial = includeSpecial)
                                    passwordError.value = ""
                                }) {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = "随机生成", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { includeSpecial = !includeSpecial }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = includeSpecial, onCheckedChange = { includeSpecial = it })
                            Text("包含特殊符号", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("启用双重验证 (2FA)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Switch(checked = hasOtp.value, onCheckedChange = { hasOtp.value = it })
                    }
                } else {
                    Text(
                        text = "配置 ${name.value} 的 TOTP 安全令牌。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StyledTextField(
                        value = secret.value,
                        onValueChange = { secret.value = it.uppercase().replace(" ", ""); secretError.value = "" },
                        label = "TOTP 密钥",
                        isError = secretError.value.isNotEmpty(),
                        supportingText = secretError.value
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        StyledTextField(
                            value = selectedAlgo.value.name,
                            onValueChange = { },
                            readOnly = true,
                            label = "哈希算法",
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
                            modifier = Modifier.clickable { expanded.value = true }
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { expanded.value = true })
                        DropdownMenu(
                            expanded = expanded.value, 
                            onDismissRequest = { expanded.value = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            OtpAlgorithm.entries.forEach { algo ->
                                DropdownMenuItem(
                                    text = { Text(algo.name) }, 
                                    onClick = { selectedAlgo.value = algo; expanded.value = false }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (step.intValue == 1) {
                        var hasError = false
                        if (name.value.isBlank()) { nameError.value = "名称不能为空"; hasError = true }
                        if (username.value.isBlank()) { usernameError.value = "账号不能为空"; hasError = true }
                        if (password.value.isBlank()) { passwordError.value = "密码不能为空"; hasError = true }
                        if (hasError) return@TextButton

                        if (hasOtp.value) {
                            step.intValue = 2
                        } else {
                            onConfirm(createAccount(name.value, username.value, password.value, false, "", OtpAlgorithm.SHA1))
                        }
                    } else {
                        if (!TotpUtil.isValidSecret(secret.value)) { secretError.value = "密钥格式错误"
                            return@TextButton
                        }
                        onConfirm(createAccount(name.value, username.value, password.value, true, secret.value, selectedAlgo.value))
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = if (step.intValue == 1 && hasOtp.value) "下一步" else "保存",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (step.intValue == 2) step.intValue = 1 else onDismiss()
                }
            ) {
                Text(if (step.intValue == 2) "返回" else "取消")
            }
        }
    )
}

// 辅助函数
private fun createAccount(name: String, user: String, pass: String, otp: Boolean, key: String, algo: OtpAlgorithm): Account {
    return Account(
        id = System.currentTimeMillis().toInt(),
        name = name,
        username = user,
        password = pass,
        iconInitial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
        hasOtp = otp,
        secretKey = if (otp) key else "",
        algorithm = algo.toRustAlgo()
    )
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(140.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "安全的令牌库", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(text = "点击下方按钮记录您的第一个账号", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
fun DeleteConfirmDialog(accountName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    StyledDialog(
        onDismissRequest = onDismiss,
        title = "确认删除",
        content = {
            Text(
                text = "确定要永久移除 $accountName 的所有记录吗？此操作无法撤销。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { 
                Text("永久删除", fontWeight = FontWeight.Bold) 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun copyToClipboard(context: Context, text: String, message: String) {
    if (text.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Cerberus Data", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
