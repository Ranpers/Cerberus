package com.yiran.cerberus.util

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm

// 这个枚举必须定义在 util 包下，且不能在 class 内部
enum class OtpAlgorithm(val libAlgo: HmacAlgorithm) {
    SHA1(HmacAlgorithm.SHA1),
    SHA256(HmacAlgorithm.SHA256),
    SHA512(HmacAlgorithm.SHA512)
}