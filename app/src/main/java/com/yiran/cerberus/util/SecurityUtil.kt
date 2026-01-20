@file:Suppress("DEPRECATION")

package com.yiran.cerberus.util

import android.content.Context
import android.content.SharedPreferences
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

    init {
        loadNativeLibraries()
    }

    private fun loadNativeLibraries() {
        listOf("jnidispatch", "rust_core").forEach { lib ->
            try {
                System.loadLibrary(lib)
                Log.d(TAG, "Native library '$lib' loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Could not load native library '$lib'", e)
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
        return sharedPreferencesInstance ?: synchronized(this) {
            sharedPreferencesInstance ?: createEncryptedPrefs(context).also {
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

    fun isTermsAccepted(context: Context): Boolean = getEncryptedPrefs(context).getBoolean(KEY_TERMS_ACCEPTED, false)
    fun setTermsAccepted(context: Context) = getEncryptedPrefs(context).edit { putBoolean(KEY_TERMS_ACCEPTED, true) }

    fun saveAccounts(context: Context, accounts: List<Account>) {
        try {
            val json = rustAccountsToJson(accounts)
            getEncryptedPrefs(context).edit { putString(KEY_ACCOUNTS, json) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize accounts", e)
        }
    }

    fun loadAccounts(context: Context): List<Account> {
        val json = getEncryptedPrefs(context).getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            rustJsonToAccounts(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize accounts", e)
            emptyList()
        }
    }

    // --- 导出序列化工具供 ViewModel 使用 ---
    fun accountsToJson(accounts: List<Account>): String = rustAccountsToJson(accounts)
    fun jsonToAccounts(json: String): List<Account> = rustJsonToAccounts(json)

    // --- 加密逻辑 ---

    fun encryptBackup(data: String, password: String): String {
        return try {
            rustEncryptBackup(data, password)
        } catch (e: CryptoException.InvalidParameter) {
            throw IllegalArgumentException("备份参数非法")
        } catch (e: CryptoException) {
            throw IllegalStateException("备份加密失败", e)
        }
    }

    fun decryptBackup(encryptedBase64: String, password: String): String {
        return try {
            rustDecryptBackup(encryptedBase64, password)
        } catch (e: CryptoException.UnsupportedVersion) {
            throw IllegalStateException("备份版本不兼容，请升级应用后再试")
        } catch (e: CryptoException.InvalidParameter) {
            throw IllegalArgumentException("备份文件格式无效")
        } catch (e: CryptoException.InvalidData) {
            throw IllegalArgumentException("备份文件格式无效或已损坏")
        } catch (e: CryptoException.DecryptionFailed) {
            throw IllegalArgumentException("密码错误或备份已损坏")
        } catch (e: CryptoException) {
            throw IllegalStateException("解密失败", e)
        }
    }

    // --- 主密码逻辑 ---

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
