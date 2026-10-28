# Compose / Kotlin 默认规则足够，这里只补充少量保留项
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-keep class com.google.zxing.** { *; }

# 数据模型保留（用于加密备份的 JSON 序列化）
-keep class com.yg.tokenvault.data.** { *; }

# 加密层保留：这些类直接调用 javax.crypto，混淆无益且不利于日后排查
-keep class com.yg.tokenvault.security.** { *; }

# ViewModel 由 ViewModelProvider 反射实例化
-keep class com.yg.tokenvault.VaultViewModel { *; }

# 保留行号信息，线上崩溃栈才看得懂（不影响性能）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile