package footballbooking

import org.http4k.client.ApacheClient
import org.http4k.core.*
import org.http4k.core.Method.*
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookies
import org.http4k.filter.ClientFilters
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.cookie.BasicCookieStorage
import org.http4k.filter.cookie.CookieStorage
import org.http4k.filter.cookie.LocalCookie
import java.time.Clock
import java.time.LocalDateTime

fun main(args: Array<String>) {
    // home page: https://hsp.kingscross.co.uk
    // login page: https://hsp.kingscross.co.uk/Accounts/Login.aspx
    // booking page: https://hsp.kingscross.co.uk/tools/commercial/muga/addsinglebooking.aspx

    // username: anuratransfersdev2@gmail.com
    // password: ...

    val httpClient: HttpHandler =
        ClientFilters.SetHostFrom(Uri.of("https://hsp.kingscross.co.uk"))
            .then(Cookies())
            .then(DebuggingFilters.PrintRequestAndResponse())
            .then(ApacheClient())

    val homePageRequest = Request(GET, "/")
    httpClient.invoke(homePageRequest)

    val loginPageRequest = Request(GET, "/Accounts/Login.aspx")
    httpClient.invoke(loginPageRequest)

    val signInRequest = Request(POST, "/Services/Commercial/api/security/validatelogin.json")
        .body("{Email: \"anuratransfersdev2@gmail.com\", Password: \"...\", PersistCookie: false}")
    httpClient.invoke(signInRequest)

    val addBookingPageRequest = Request(GET, "/tools/commercial/muga/addsinglebooking.aspx")
    httpClient.invoke(addBookingPageRequest)
}

object Cookies {
    operator fun invoke(clock: Clock = Clock.systemDefaultZone(),
                        storage: CookieStorage = BasicCookieStorage()): Filter = Filter { next ->
        { request ->
            val now = clock.now()
            removeExpired(now, storage)
            val response = next(request.withLocalCookies(storage))
            storage.store(response.cookies().map { LocalCookie(it, now) })
            response
        }
    }

    private fun Request.withLocalCookies(storage: CookieStorage) = storage.retrieve()
        .map { it.cookie }
        .fold(this, { r, cookie -> r.cookie(cookie.name, cookie.value) })

    private fun removeExpired(now: LocalDateTime, storage: CookieStorage)
        = storage.retrieve().filter { it.isExpired(now) }.forEach { storage.remove(it.cookie.name) }

    private fun Clock.now() = LocalDateTime.ofInstant(instant(), zone)
}

fun Request.cookie(name: String, value: String): Request = replaceHeader("Cookie", cookies().plus(Cookie(name, value)).toCookieString())

private fun List<Cookie>.toCookieString() = map { "${it.name}=${it.value}" }.joinToString("; ")

fun <T> T.printed(): T = this.apply { println(this) }
