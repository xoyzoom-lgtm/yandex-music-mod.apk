package io.github.xoyzoom.ymmod.web

object UserAgents {
    private val chromeVersion = Regex("""Chrome/([\d.]+)""")

    /**
     * Убирает из стандартного UA WebView признаки встроенного браузера ("; wv", "Version/4.0"),
     * из-за которых некоторые страницы входа отказываются работать. В десктопном режиме
     * подставляет UA Chrome для Linux с той же версией движка.
     */
    fun build(defaultUserAgent: String, desktop: Boolean): String {
        if (desktop) {
            val version = chromeVersion.find(defaultUserAgent)?.groupValues?.get(1) ?: "120.0.0.0"
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/$version Safari/537.36"
        }
        return defaultUserAgent
            .replace("; wv)", ")")
            .replace(" Version/4.0", "")
    }
}
