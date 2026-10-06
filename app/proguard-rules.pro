# Мост с WebView работает через WebViewCompat.addWebMessageListener,
# поэтому @JavascriptInterface-классов нет и специальных правил не требуется.

# Discord (только в сборке с discord_partner_sdk.aar): JNI ищет методы по именам.
-keep class io.github.xoyzoom.ymmod.presence.discord.DiscordNative { native <methods>; }
-keep interface io.github.xoyzoom.ymmod.presence.discord.DiscordNative$Listener { *; }
-keep class * implements io.github.xoyzoom.ymmod.presence.discord.DiscordNative$Listener { *; }
-keep class com.discord.socialsdk.** { *; }
-dontwarn com.discord.socialsdk.**

# Оставляем номера строк в стектрейсах релизной сборки.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
