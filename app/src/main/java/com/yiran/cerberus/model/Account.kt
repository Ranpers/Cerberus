package com.yiran.cerberus.model

import com.yiran.cerberus.util.OtpAlgorithm

data class Account(
    val id: Int,
    val name: String,          // 服务名称 (如: Google)
    val username: String,      // 账号/用户名 (如: example@gmail.com)
    val password: String = "", // 账号对应的静态密码 (可选)
    val iconInitial: Char,
    val secretKey: String = "", // TOTP 密钥 (可选)
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1, // TOTP 算法
    val hasOtp: Boolean = false // 标志位：是否启用了双重验证功能
)
