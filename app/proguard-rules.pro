# Add project specific ProGuard rules here.
# Retrofit / OkHttp / Gson 默认规则已足够，比赛演示不开启混淆。
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class com.example.smartirrigation.model.** { *; }
