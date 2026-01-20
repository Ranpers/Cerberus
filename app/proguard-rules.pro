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
# UniFFI & Rust 核心保留规则 (精细化处理)
# --------------------------------------------------------------------------------

# 1. 保留 UniFFI 生成的具体包名下的类，而不是整个 uniffi.**
-keep class uniffi.rust_core.** { *; }

# 2. 针对 JNA 的精细化规则，解决 "Overly broad keep rule" 警告
# 仅保留 JNA 运行所必需的核心类和接口
-keep class com.sun.jna.Native { *; }
-keep class com.sun.jna.NativeLibrary { *; }
-keep class com.sun.jna.Library { *; }
-keep class com.sun.jna.Callback { *; }
-keep class com.sun.jna.Pointer { *; }
-keep class com.sun.jna.Structure { *; }
-keep class com.sun.jna.Structure$ByReference { *; }
-keep class com.sun.jna.Structure$ByValue { *; }
-keep class com.sun.jna.Union { *; }
-keep class com.sun.jna.PointerType { *; }
-keep class com.sun.jna.ptr.** { *; }

# 3. 保留所有实现了 JNA Library 或 Structure 接口的类（包括 UniFFI 生成的内部映射）
-keep class * implements com.sun.jna.Library { *; }
-keep class * extends com.sun.jna.Structure { *; }

# 4. 忽略 JNA 引用的一些可能在 Android 上不存在的 AWT/Swing 符号警告
-dontwarn com.sun.jna.**

# --------------------------------------------------------------------------------
# 安全与优化
# --------------------------------------------------------------------------------

# 混淆源码文件名和行号，增加逆向难度
-renamesourcefileattribute SourceFile
-keepattributes !SourceFile,!LineNumberTable

# 针对 EncryptedSharedPreferences 的保留规则
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
