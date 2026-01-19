package com.yiran.cerberus.ui.home

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yiran.cerberus.model.Account
import com.yiran.cerberus.util.SecurityUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class HomeViewModel : ViewModel() {
    private val _accounts = mutableStateListOf<Account>()
    val accounts: List<Account> = _accounts

    private var saveJob: Job? = null
    private val gson = Gson()

    fun loadAccounts(context: Context) {
        viewModelScope.launch {
            val loadedAccounts = withContext(Dispatchers.IO) {
                SecurityUtil.loadAccounts(context)
            }
            _accounts.clear()
            _accounts.addAll(loadedAccounts)
        }
    }

    private fun scheduleSave(context: Context) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            withContext(Dispatchers.IO) {
                SecurityUtil.saveAccounts(context, _accounts.toList())
            }
        }
    }

    fun addAccount(context: Context, account: Account) {
        _accounts.add(account)
        scheduleSave(context)
    }

    fun deleteAccount(context: Context, account: Account) {
        _accounts.remove(account)
        scheduleSave(context)
    }

    fun updatePassword(context: Context, accountId: Int, newPassword: String) {
        val index = _accounts.indexOfFirst { it.id == accountId }
        if (index != -1) {
            _accounts[index] = _accounts[index].copy(password = newPassword)
            scheduleSave(context)
        }
    }

    // --- 备份导出与导入 (支持二次加密) ---

    fun exportBackup(password: String): String {
        val json = gson.toJson(_accounts.toList())
        return SecurityUtil.encryptBackup(json, password)
    }

    fun importBackup(
        context: Context,
        uri: Uri,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val encryptedContent = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).readText()
                    } ?: throw Exception("无法读取文件")
                }

                val json = SecurityUtil.decryptBackup(encryptedContent, password)
                val type = object : TypeToken<List<Account>>() {}.type
                val importedAccounts: List<Account> = gson.fromJson(json, type)

                if (importedAccounts.isNotEmpty()) {
                    _accounts.clear()
                    _accounts.addAll(importedAccounts)
                    withContext(Dispatchers.IO) {
                        SecurityUtil.saveAccounts(context, _accounts.toList())
                    }
                    onSuccess()
                } else {
                    onError("备份文件内容为空")
                }
            } catch (_: Exception) {
                onError("导入失败: 密码错误或文件损坏")
            }
        }
    }
}
