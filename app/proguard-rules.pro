# Мост с WebView работает через WebViewCompat.addWebMessageListener,
# поэтому @JavascriptInterface-классов нет и специальных правил не требуется.

# Оставляем номера строк в стектрейсах релизной сборки.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
