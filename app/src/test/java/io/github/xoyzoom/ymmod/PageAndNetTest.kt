package io.github.xoyzoom.ymmod

import io.github.xoyzoom.ymmod.net.RegionChecker
import io.github.xoyzoom.ymmod.net.UpdateChecker
import io.github.xoyzoom.ymmod.player.PlayerMessageParser
import io.github.xoyzoom.ymmod.settings.AppSettings
import io.github.xoyzoom.ymmod.settings.coerceUiScale
import io.github.xoyzoom.ymmod.settings.toPageConfig
import io.github.xoyzoom.ymmod.web.LikesProgress
import io.github.xoyzoom.ymmod.web.LinkPolicy
import io.github.xoyzoom.ymmod.web.PageEvent
import io.github.xoyzoom.ymmod.web.PageEventParser
import io.github.xoyzoom.ymmod.web.TrackerBlocklist
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageAndNetTest {
    @Test
    fun parsesCssColors() {
        assertEquals(0xFF121212.toInt(), PageEventParser.parseCssColor("rgb(18, 18, 18)"))
        assertEquals(0xFFFFFFFF.toInt(), PageEventParser.parseCssColor("rgba(255, 255, 255, 1)"))
        assertNull(PageEventParser.parseCssColor("rgba(0, 0, 0, 0)"))
        assertNull(PageEventParser.parseCssColor("red"))
    }

    @Test
    fun parsesPageEvents() {
        assertEquals(PageEvent.OpenModSettings, PageEventParser.parse("""{"type":"openModSettings","payload":{}}"""))
        val likes = PageEventParser.parse(
            """{"type":"likes","payload":{"status":"progress","done":3,"total":10,"message":"m"}}""",
        ) as PageEvent.Likes
        assertEquals(LikesProgress(LikesProgress.Status.PROGRESS, 3, 10, "m"), likes.progress)

        val link = PageEventParser.parse("""{"type":"collectionLink","payload":{"url":"javascript:x"}}""") as PageEvent.CollectionLink
        assertNull(link.url)
        assertNull(PageEventParser.parse("""{"type":"snapshot","payload":{"html":""}}"""))
        assertNull(PageEventParser.parse("""{"type":"state","payload":{}}"""))
        assertNull(PageEventParser.parse("garbage"))
    }

    @Test
    fun likedStateIsOptional() {
        val base = """{"type":"state","payload":{"title":"T","artist":"A","liked":"%s"}}"""
        assertEquals(true, PlayerMessageParser.parse(base.format("yes"), 0)!!.liked)
        assertEquals(false, PlayerMessageParser.parse(base.format("no"), 0)!!.liked)
        assertNull(PlayerMessageParser.parse(base.format("unknown"), 0)!!.liked)
    }

    @Test
    fun pageConfigJson() {
        val json = JSONObject(AppSettings(snowEffect = true, hideAppPromo = false).toPageConfig().toJson())
        assertTrue(json.getBoolean("autoBestQuality"))
        assertFalse(json.getBoolean("hideAppPromo"))
        assertTrue(json.getBoolean("snow"))
        assertEquals(75, 10.coerceUiScale())
        assertEquals(150, 400.coerceUiScale())
    }

    @Test
    fun linkPolicy() {
        assertEquals(LinkPolicy.Target.IN_APP, LinkPolicy.classify("https", "music.yandex.ru"))
        assertEquals(LinkPolicy.Target.IN_APP, LinkPolicy.classify("https", "passport.yandex.ru"))
        assertEquals(LinkPolicy.Target.EXTERNAL, LinkPolicy.classify("https", "play.google.com"))
        assertEquals(LinkPolicy.Target.EXTERNAL, LinkPolicy.classify("https", "yandexmusic.onelink.me"))
        assertEquals(LinkPolicy.Target.EXTERNAL, LinkPolicy.classify("yandexmusic", null))
        assertEquals(LinkPolicy.Target.EXTERNAL, LinkPolicy.classify("intent", null))
        assertEquals(LinkPolicy.Target.BLOCK, LinkPolicy.classify("javascript", null))
        assertEquals(LinkPolicy.Target.BLOCK, LinkPolicy.classify("file", null))
    }

    @Test
    fun trackerBlocklistOnlyCoversAnalytics() {
        assertTrue(TrackerBlocklist.isBlockedHost("mc.yandex.ru"))
        assertTrue(TrackerBlocklist.isBlockedHost("MC.Yandex.com"))
        assertFalse(TrackerBlocklist.isBlockedHost("music.yandex.ru"))
        assertFalse(TrackerBlocklist.isBlockedHost("api.music.yandex.net"))
        assertFalse(TrackerBlocklist.isBlockedHost(null))
    }

    @Test
    fun comparesVersions() {
        assertTrue(UpdateChecker.isNewer("0.3.0", "0.2.9"))
        assertTrue(UpdateChecker.isNewer("v1.0", "0.9.9-debug"))
        assertFalse(UpdateChecker.isNewer("0.2.0", "0.2.0-dev"))
        assertFalse(UpdateChecker.isNewer("0.1.9", "0.2.0"))
        assertFalse(UpdateChecker.isNewer("garbage", "0.2.0"))
    }

    @Test
    fun parsesRelease() {
        val release = UpdateChecker.parse(
            """{"tag_name":"v0.3.0","html_url":"https://github.com/x/y/releases/tag/v0.3.0",
               "assets":[{"name":"notes.txt","browser_download_url":"https://e/notes.txt"},
                         {"name":"YmMod-0.3.0.apk","browser_download_url":"https://e/YmMod-0.3.0.apk"}]}""",
        )!!
        assertEquals("0.3.0", release.version)
        assertEquals("https://e/YmMod-0.3.0.apk", release.apkUrl)
        assertNull(UpdateChecker.parse("{}"))
    }

    @Test
    fun parsesRegion() {
        assertEquals(true, RegionChecker.parse("""{"ip":"1.2.3.4","country":"kz"}""")?.isSupported)
        assertEquals(false, RegionChecker.parse("""{"ip":"1.2.3.4","country":"US"}""")?.isSupported)
        assertNull(RegionChecker.parse("""{"country":""}"""))
    }
}
