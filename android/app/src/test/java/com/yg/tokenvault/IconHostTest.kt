package com.yg.tokenvault

import com.yg.tokenvault.util.IconStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「图标域名」输入框的归一化单测。
 *
 * 用户在编辑器里可能填 `github.com`、`https://github.com/login?tab=1`、
 * 甚至直接把邮箱粘进来；抓取前必须统一变成干净的 host，否则请求地址会拼错。
 */
class IconHostTest {

    @Test
    fun bareDomain_passesThrough() {
        assertEquals("github.com", IconStore.parseHost("github.com"))
    }

    @Test
    fun urlsDropSchemePathAndQuery() {
        assertEquals("github.com", IconStore.parseHost("https://github.com/settings/security?x=1"))
        assertEquals("mail.qq.com", IconStore.parseHost("http://mail.qq.com/cgi-bin/frame_html"))
    }

    @Test
    fun uppercase_isLowercased() {
        assertEquals("github.com", IconStore.parseHost("HTTPS://GitHub.COM/Login"))
    }

    @Test
    fun wwwAndPortAndFragmentAreRemoved() {
        assertEquals("qq.com", IconStore.parseHost("www.qq.com"))
        assertEquals("example.com", IconStore.parseHost("example.com:8443/a#top"))
    }

    @Test
    fun email_isReducedToItsDomain() {
        assertEquals("163.com", IconStore.parseHost("15386223616@163.com"))
    }

    @Test
    fun ipLiteral_isAccepted() {
        assertEquals("192.168.1.10", IconStore.parseHost("192.168.1.10:8080"))
    }

    @Test
    fun trailingPunctuation_isTrimmed() {
        assertEquals("github.com", IconStore.parseHost("  github.com。 "))
        assertEquals("github.com", IconStore.parseHost("github.com,"))
    }

    @Test
    fun junkIsRejected() {
        assertNull(IconStore.parseHost(""))
        assertNull(IconStore.parseHost("   "))
        assertNull(IconStore.parseHost("github"))
        assertNull(IconStore.parseHost("https://"))
        assertNull(IconStore.parseHost("a b.com"))
        assertNull(IconStore.parseHost("..."))
    }
}
