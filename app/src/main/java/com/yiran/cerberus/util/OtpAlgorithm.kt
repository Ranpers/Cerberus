package com.yiran.cerberus.util

import uniffi.rust_core.OtpHashAlgorithm

/**
 * TOTP 哈希算法枚举。
 * 已解耦第三方库，现在作为 Rust 核心层算法的映射。
 */
enum class OtpAlgorithm {
    SHA1,
    SHA256,
    SHA512;

    // 辅助转换方法，方便传参给 Rust 函数
    fun toRustAlgo(): OtpHashAlgorithm = when (this) {
        SHA1 -> OtpHashAlgorithm.SHA1
        SHA256 -> OtpHashAlgorithm.SHA256
        SHA512 -> OtpHashAlgorithm.SHA512
    }
}
