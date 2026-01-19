@file:Suppress("DEPRECATION")

package com.yiran.cerberus.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yiran.cerberus.model.Account
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SecurityUtil {
    private const val PREF_NAME = "secure_prefs"
    private const val KEY_MASTER_PASSWORD_HASH = "master_password_hash"
    private const val KEY_SALT = "master_password_salt"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_ACCOUNTS = "stored_accounts"
    private const val KEY_TERMS_ACCEPTED = "terms_accepted"

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
    private const val ITERATION_COUNT = 100000 
    private const val KEY_LENGTH = 256
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 128

    private val gson = Gson()

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
        val json = gson.toJson(accounts)
        getEncryptedPrefs(context).edit { putString(KEY_ACCOUNTS, json) }
    }

    fun loadAccounts(context: Context): List<Account> {
        val json = getEncryptedPrefs(context).getString(KEY_ACCOUNTS, null) ?: return emptyList()
        val type = object : TypeToken<List<Account>>() {}.type
        return gson.fromJson(json, type)
    }

    // --- 二次加密 (用于备份) ---

    fun encryptBackup(data: String, password: String): String {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
        
        val secretKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))
        
        val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        
        // 格式: salt(16) + iv(12) + ciphertext
        val combined = salt + iv + encryptedData
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptBackup(encryptedBase64: String, password: String): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        
        val salt = combined.sliceArray(0 until 16)
        val iv = combined.sliceArray(16 until 16 + IV_LENGTH)
        val encryptedData = combined.sliceArray(16 + IV_LENGTH until combined.size)
        
        val secretKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))
        
        val decryptedData = cipher.doFinal(encryptedData)
        return String(decryptedData, Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    // --- 主密码逻辑 ---

    fun isMasterPasswordSet(context: Context): Boolean = getEncryptedPrefs(context).contains(KEY_MASTER_PASSWORD_HASH)

    fun setMasterPassword(context: Context, password: String) {
        val salt = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val hash = hashPassword(password.toCharArray(), salt)
        getEncryptedPrefs(context).edit {
            putString(KEY_MASTER_PASSWORD_HASH, bytesToHex(hash))
            putString(KEY_SALT, bytesToHex(salt))
        }
    }

    fun verifyMasterPassword(context: Context, password: String): Boolean {
        val prefs = getEncryptedPrefs(context)
        val storedHashHex = prefs.getString(KEY_MASTER_PASSWORD_HASH, null) ?: return false
        val saltHex = prefs.getString(KEY_SALT, null) ?: return false
        val currentHash = hashPassword(password.toCharArray(), hexToBytes(saltHex))
        return bytesToHex(currentHash) == storedHashHex
    }

    fun canUseBiometric(context: Context): Boolean {
        return BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) = getEncryptedPrefs(context).edit { putBoolean(KEY_BIOMETRIC_ENABLED, enabled) }
    fun isBiometricEnabled(context: Context): Boolean = getEncryptedPrefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)

    private fun hashPassword(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH)
        return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
    }

    private val HEX_ARRAY = "0123456789abcdef".toCharArray()
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = HEX_ARRAY[v ushr 4]
            hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
        }
        return String(hexChars)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val data = ByteArray(hex.length / 2)
        for (i in 0 until hex.length step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
}
