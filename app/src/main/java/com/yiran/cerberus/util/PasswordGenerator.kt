package com.yiran.cerberus.util

import java.security.SecureRandom

object PasswordGenerator {
    private val secureRandom = SecureRandom()
    
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SPECIAL = "!@#$%^&*()-_=+[]{}|;:,.<>?"
    
    fun generate(
        length: Int = 16,
        includeUpper: Boolean = true,
        includeLower: Boolean = true,
        includeDigits: Boolean = true,
        includeSpecial: Boolean = true
    ): String {
        val charPool = StringBuilder().apply {
            if (includeUpper) append(UPPER)
            if (includeLower) append(LOWER)
            if (includeDigits) append(DIGITS)
            if (includeSpecial) append(SPECIAL)
        }.toString()

        if (charPool.isEmpty()) return ""

        return (1..length)
            .map { charPool[secureRandom.nextInt(charPool.length)] }
            .joinToString("")
    }
}
