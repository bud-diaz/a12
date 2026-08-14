package com.paperweight.os.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

private const val SESSION_COOKIE_NAME = "pw_dashboard_session"

// The dashboard-session cookie set by POST /api/auth/dashboard/device/redeem
// (see paperweightv1's src/api/auth.js). We only ever talk to one paired
// station at a time, so a single stored value (not a per-host cookie store)
// is sufficient.
class SessionCookieJar(private val sessionStore: SessionStore) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val sessionCookie = cookies.firstOrNull { it.name == SESSION_COOKIE_NAME } ?: return
        sessionStore.sessionCookieValue = sessionCookie.value
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val value = sessionStore.sessionCookieValue ?: return emptyList()
        val cookie = Cookie.Builder()
            .name(SESSION_COOKIE_NAME)
            .value(value)
            .domain(url.host)
            .path("/")
            .build()
        return listOf(cookie)
    }
}
