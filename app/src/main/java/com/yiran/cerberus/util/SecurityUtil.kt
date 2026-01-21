@file:Suppress("DEPRECATION")

package com.yiran.cerberus.util

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import uniffi.rust_core.CryptoException
import uniffi.rust_core.Account
import uniffi.rust_core.encryptBackup as rustEncryptBackup
import uniffi.rust_core.decryptBackup as rustDecryptBackup
import uniffi.rust_core.hashMasterPassword as rustHashMasterPassword
import uniffi.rust_core.verifyMasterPassword as rustVerifyMasterPassword
import uniffi.rust_core.accountsToJson as rustAccountsToJson
import uniffi.rust_core.jsonToAccounts as rustJsonToAccounts

object SecurityUtil {
    private const val TAG = "SecurityUtil"
    
    // 自动锁定阈值配置
    private const val KEY_AUTO_LOCK_TIME = "auto_lock_time"
    private const val DEFAULT_AUTO_LOCK_TIME = 30 * 1000L // 默认 30 秒
    private const val KEY_LAST_BACKGROUND_TIME = "last_background_time"

    init {
        loadNativeLibraries()
    }

    private fun loadNativeLibraries() {
        listOf("jnidispatch", "rust_core").forEach { lib ->
            try {
                System.loadLibrary(lib)
            } catch (_: UnsatisfiedLinkError) {
                Log.e(TAG, "Security module init failed")
            }
        }
    }

    private const val PREF_NAME = "secure_prefs"
    private const val KEY_MASTER_PASSWORD_HASH = "master_password_hash"
    private const val KEY_SALT = "master_password_salt"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_ACCOUNTS = "stored_accounts"
    private const val KEY_TERMS_ACCEPTED = "terms_accepted"

    @Volatile
    private var sharedPreferencesInstance: SharedPreferences? = null

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        return sharedPreferencesInstance ?: synchronized(this) {
            sharedPreferencesInstance ?: createEncryptedPrefs(appContext).also {
                sharedPreferencesInstance = it
            }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun markEnterBackground(context: Context) {
        // 使用 SystemClock.elapsedRealtime 防止用户通过修改系统时间绕过锁定
        getEncryptedPrefs(context).edit {
            putLong(KEY_LAST_BACKGROUND_TIME, SystemClock.elapsedRealtime())
        }
    }

    fun markAuthenticated(context: Context) {
        // 标记已成功验证，清除后台计时
        getEncryptedPrefs(context).edit {
            putLong(KEY_LAST_BACKGROUND_TIME, 0L)
        }
    }

    fun shouldReauthenticate(context: Context): Boolean {
        val lastTime = getEncryptedPrefs(context).getLong(KEY_LAST_BACKGROUND_TIME, 0L)
        // 如果 lastTime 为 0，表示应用处于活跃状态或刚解锁
        if (lastTime == 0L) return false
        
        val elapsed = SystemClock.elapsedRealtime() - lastTime
        return elapsed > getAutoLockTime(context)
    }

    fun getAutoLockTime(context: Context): Long {
        return getEncryptedPrefs(context).getLong(KEY_AUTO_LOCK_TIME, DEFAULT_AUTO_LOCK_TIME)
    }

    fun setAutoLockTime(context: Context, timeMs: Long) {
        getEncryptedPrefs(context).edit { putLong(KEY_AUTO_LOCK_TIME, timeMs) }
    }

    fun isTermsAccepted(context: Context): Boolean = getEncryptedPrefs(context).getBoolean(KEY_TERMS_ACCEPTED, false)
    fun setTermsAccepted(context: Context) = getEncryptedPrefs(context).edit { putBoolean(KEY_TERMS_ACCEPTED, true) }

    fun saveAccounts(context: Context, accounts: List<Account>) {
        try {
            val json = rustAccountsToJson(accounts)
            getEncryptedPrefs(context).edit { putString(KEY_ACCOUNTS, json) }
        } catch (_: Exception) {
            Log.e(TAG, "Serialization failed")
        }
    }

    fun loadAccounts(context: Context): List<Account> {
        val json = getEncryptedPrefs(context).getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            rustJsonToAccounts(json)
        } catch (_: Exception) {
            Log.e(TAG, "Deserialization failed")
            emptyList()
        }
    }

    fun accountsToJson(accounts: List<Account>): String = rustAccountsToJson(accounts)
    fun jsonToAccounts(json: String): List<Account> = rustJsonToAccounts(json)

    fun encryptBackup(data: String, password: String): String {
        return try {
            rustEncryptBackup(data, password)
        } catch (_: CryptoException.InvalidParameter) {
            throw IllegalArgumentException("备份参数非法")
        } catch (e: CryptoException) {
            throw IllegalStateException("备份加密失败", e)
        }
    }

    fun decryptBackup(encryptedBase64: String, password: String): String {
        return try {
            rustDecryptBackup(encryptedBase64, password)
        } catch (_: CryptoException.UnsupportedVersion) {
            throw IllegalStateException("备份版本不兼容，请升级应用后再试")
        } catch (_: CryptoException.InvalidParameter) {
            throw IllegalArgumentException("备份文件格式无效")
        } catch (_: CryptoException.InvalidData) {
            throw IllegalArgumentException("备份文件格式无效或已损坏")
        } catch (_: CryptoException.DecryptionFailed) {
            throw IllegalArgumentException("密码错误或备份已损坏")
        } catch (e: CryptoException) {
            throw IllegalStateException("解密失败", e)
        }
    }

    fun isMasterPasswordSet(context: Context): Boolean = getEncryptedPrefs(context).contains(KEY_MASTER_PASSWORD_HASH)

    fun setMasterPassword(context: Context, password: String) {
        val result = rustHashMasterPassword(password)
        getEncryptedPrefs(context).edit {
            putString(KEY_MASTER_PASSWORD_HASH, result.hash)
            putString(KEY_SALT, result.salt)
        }
    }

    fun verifyMasterPassword(context: Context, password: String): Boolean {
        val prefs = getEncryptedPrefs(context)
        val storedHash = prefs.getString(KEY_MASTER_PASSWORD_HASH, null) ?: return false
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        return rustVerifyMasterPassword(password, storedHash, salt)
    }

    fun canUseBiometric(context: Context): Boolean {
        return BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) = getEncryptedPrefs(context).edit { putBoolean(KEY_BIOMETRIC_ENABLED, enabled) }
    fun isBiometricEnabled(context: Context): Boolean = getEncryptedPrefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)
}
