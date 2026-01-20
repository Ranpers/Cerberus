# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# --------------------------------------------------------------------------------
# 日志剥离规则 (Release 模式下由 R8 自动移除)
# --------------------------------------------------------------------------------

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# --------------------------------------------------------------------------------
# UniFFI & Rust 相关保留规则
# --------------------------------------------------------------------------------

# 必须保留 JNA 相关的 native 方法，否则会导致 Runtime 找不到符号
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }

# 保留 UniFFI 生成的代码，防止被混淆导致找不到 Rust 映射
-keep class uniffi.** { *; }

# --------------------------------------------------------------------------------
# 通用安全混淆
# --------------------------------------------------------------------------------

# 混淆源码文件名和行号（如果不需要收集崩溃堆栈）
# -renamesourcefileattribute SourceFile
# -keepattributes SourceFile,LineNumberTable

# --------------------------------------------------------------------------------
# 其他库规则
# --------------------------------------------------------------------------------

# 针对 EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }
