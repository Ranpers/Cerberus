package com.yiran.cerberus.util

import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import org.apache.commons.codec.binary.Base32
import java.util.concurrent.TimeUnit

object TotpUtil {

    private val base32 = Base32()

    fun generateTOTP(secret: String, algorithm: OtpAlgorithm = OtpAlgorithm.SHA1): String {
        return try {
            val cleanSecret = secret.trim().uppercase().replace(" ", "")
            if (!isValidSecret(cleanSecret)) return "ERROR"

            val config = TimeBasedOneTimePasswordConfig(
                codeDigits = 6,
                hmacAlgorithm = algorithm.libAlgo,
                timeStep = 30,
                timeStepUnit = TimeUnit.SECONDS
            )

            val secretBytes = base32.decode(cleanSecret)
            val generator = TimeBasedOneTimePasswordGenerator(secretBytes, config)
            generator.generate(System.currentTimeMillis())
        } catch (e: Exception) {
            e.printStackTrace()
            "ERROR"
        }
    }

    fun isValidSecret(secret: String): Boolean {
        if (secret.isBlank()) return false
        val cleanSecret = secret.trim().uppercase().replace(" ", "")
        return base32.isInAlphabet(cleanSecret)
    }

    fun getProgress(): Float {
        val time = System.currentTimeMillis() / 1000
        val remaining = 30 - (time % 30)
        return remaining / 30f
    }

    /**
     * 获取当前周期剩余秒数 (Long 类型，方便逻辑判断)
     */
    fun getRemainingSeconds(): Long {
        val time = System.currentTimeMillis() / 1000
        return 30 - (time % 30)
    }
}
