package io.github.xoyzoom.ymmod

import io.github.xoyzoom.ymmod.settings.AppSettings
import io.github.xoyzoom.ymmod.settings.FontPreset
import io.github.xoyzoom.ymmod.settings.FontSpec
import io.github.xoyzoom.ymmod.settings.toFontSpec
import io.github.xoyzoom.ymmod.web.UserAgents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun siteDefaultFontChangesNothing() {
        assertEquals(FontSpec.NONE, AppSettings().toFontSpec())
    }

    @Test
    fun presetFontHasNoStylesheet() {
        val spec = AppSettings(fontPreset = FontPreset.ROBOTO).toFontSpec()
        assertEquals("\"Roboto\", sans-serif", spec.cssFamily)
        assertEquals(null, spec.stylesheetUrl)
    }

    @Test
    fun customFontUsesBunnyFonts() {
        val spec = AppSettings(fontPreset = FontPreset.CUSTOM, customFontName = "JetBrains Mono").toFontSpec()
        assertEquals("\"JetBrains Mono\", sans-serif", spec.cssFamily)
        assertEquals("https://fonts.bunny.net/css?family=JetBrains+Mono:300,400,500,600,700", spec.stylesheetUrl)
    }

    @Test
    fun customFontNameIsSanitized() {
        val spec = AppSettings(fontPreset = FontPreset.CUSTOM, customFontName = "Evil\"; } body{x").toFontSpec()
        assertEquals("\"Evil  bodyx\", sans-serif", spec.cssFamily)
        assertEquals(FontSpec.NONE, AppSettings(fontPreset = FontPreset.CUSTOM, customFontName = "  ").toFontSpec())
    }

    @Test
    fun userAgentDropsWebViewMarkers() {
        val webViewUa = "Mozilla/5.0 (Linux; Android 14; Pixel 8; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/129.0.6668.100 Mobile Safari/537.36"
        val mobile = UserAgents.build(webViewUa, desktop = false)
        assertFalse(mobile.contains("; wv"))
        assertFalse(mobile.contains("Version/4.0"))

        val desktop = UserAgents.build(webViewUa, desktop = true)
        assertTrue(desktop.contains("X11; Linux x86_64"))
        assertTrue(desktop.contains("Chrome/129.0.6668.100"))
        assertFalse(desktop.contains("Mobile"))
    }
}
