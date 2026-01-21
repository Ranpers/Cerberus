package com.yiran.cerberus.ui.home

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yiran.cerberus.util.SecurityUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uniffi.rust_core.Account
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.net.SocketTimeoutException

class HomeViewModel : ViewModel() {
    private val _accounts = mutableStateListOf<Account>()
    val accounts: List<Account> = _accounts

    private var saveJob: Job? = null

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
            val oldAccount = _accounts[index]
            _accounts[index] = oldAccount.copy(password = newPassword)
            scheduleSave(context)
        }
    }

    fun moveAccount(context: Context, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex !in _accounts.indices || toIndex !in _accounts.indices) return
        _accounts.add(toIndex, _accounts.removeAt(fromIndex))
        scheduleSave(context)
    }

    fun exportBackup(password: String): String {
        val json = SecurityUtil.accountsToJson(_accounts.toList())
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
                val importedAccounts = SecurityUtil.jsonToAccounts(json)

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
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "导入失败: 备份文件无效")
            } catch (e: IllegalStateException) {
                onError(e.message ?: "导入失败: 备份版本不兼容或未知错误")
            } catch (_ : Exception) {
                onError("导入失败: 密码错误或文件损坏")
            }
        }
    }

    fun checkUpdate(
        currentVersion: String,
        onResult: (hasUpdate: Boolean, latestVersion: String, downloadUrl: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = URL("https://api.github.com/repos/Ranpers/Cerberus/releases/latest")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(response)
                        val latestTag = json.getString("tag_name").removePrefix("v")
                        val hasUpdate = isVersionNewer(currentVersion, latestTag)
                        
                        var downloadUrl: String? = null
                        val assets = json.optJSONArray("assets")
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.getString("name")
                                if (name.endsWith(".apk")) {
                                    downloadUrl = asset.getString("browser_download_url")
                                    break
                                }
                            }
                        }
                        
                        Triple(hasUpdate, latestTag, downloadUrl)
                    } else {
                        throw Exception("服务器响应异常: ${connection.responseCode}")
                    }
                }
                onResult(result.first, result.second, result.third)
            } catch (_ : UnknownHostException) {
                onError("网络不可用，请检查联网设置")
            } catch (_ : SocketTimeoutException) {
                onError("连接 GitHub 超时，请稍后再试")
            } catch (e: Exception) {
                onError("检查失败: ${e.message ?: "网络请求异常"}")
            }
        }
    }

    private fun isVersionNewer(current: String, latest: String): Boolean {
        val currParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until minOf(currParts.size, latestParts.size)) {
            if (latestParts[i] > currParts[i]) return true
            if (latestParts[i] < currParts[i]) return false
        }
        return latestParts.size > currParts.size
    }
}
