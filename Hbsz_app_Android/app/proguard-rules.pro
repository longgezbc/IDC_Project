# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---- 崩溃堆栈可读性 ----
# 保留行号信息，配合 build/outputs/mapping/release/mapping.txt 可将混淆堆栈还原到源码行号
-keepattributes SourceFile,LineNumberTable
# 堆栈中的源文件名统一显示为 "SourceFile"，避免泄露真实类文件名
-renamesourcefileattribute SourceFile

# ---- kotlinx-serialization ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.example.hbsz_app_android.**$$serializer { *; }
-keepclassmembers class com.example.hbsz_app_android.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.hbsz_app_android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Retrofit / OkHttp ----
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-dontwarn javax.annotation.**
-dontwarn kotlinx.serialization.**
