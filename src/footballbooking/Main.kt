package footballbooking

import org.http4k.client.ApacheClient
import org.http4k.core.Body
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookies
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ClientFilters
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.TrafficFilters
import org.http4k.filter.cookie.BasicCookieStorage
import org.http4k.filter.cookie.CookieStorage
import org.http4k.filter.cookie.LocalCookie
import org.http4k.format.Gson.auto
import org.http4k.traffic.ReadWriteCache
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class Login(val Email: String, val Password: String, val PersistCookie: Boolean = true)

data class ListSessions(val BookingDate: String, val ActivityTypeGuid: String)

data class BookSlot(val ActivityTypeGuid: String, val SessionGuid: String, val Date: String)

data class BookingSessions(val Code: Int, val Data: List<Slot>) {
    fun findSlot() = Data.find { it.Availability == 0 }
}

data class Slot(
    val Guid: String,
    val Name: String,
    val StartDateTime: String,
    val EndDateTime: String,
    val Availability: Int
) {
    fun book(guid: String) = BookSlot(guid, Guid, StartDateTime)
}


fun main(args: Array<String>) {
    // home page: https://hsp.kingscross.co.uk
    // login page: https://hsp.kingscross.co.uk/Accounts/Login.aspx
    // booking page: https://hsp.kingscross.co.uk/tools/commercial/muga/addsinglebooking.aspx
    // post to list sessions: https://hsp.kingscross.co.uk/Services/Commercial/api/muga/ListAvailableSessions.json
    // post to add booking: https://hsp.kingscross.co.uk/Services/Commercial/api/muga/AddBooking.json

    // username: anuratransfersdev2@gmail.com
    // password: ...

    val cache = ReadWriteCache.Disk("./traffic")
    val httpClient: HttpHandler =
        TrafficFilters.ServeCachedFrom(cache)
            .then(TrafficFilters.RecordTo(cache))
            .then(ClientFilters.SetHostFrom(Uri.of("https://hsp.kingscross.co.uk")))
            .then(Cookies())
            .then(DebuggingFilters.PrintRequestAndResponse())
            .then(ApacheClient())

    val homePageRequest = Request(GET, "/")
    httpClient(homePageRequest)

    val loginPageRequest = Request(GET, "/Accounts/Login.aspx")
    httpClient(loginPageRequest)

    val loginLens = Body.auto<Login>().toLens()
    val listSessionsLens = Body.auto<ListSessions>().toLens()
    val bookingSessionsLens = Body.auto<BookingSessions>().toLens()
    val bookSlotLens = Body.auto<BookSlot>().toLens()

    val signInRequest = Request(POST, "/Services/Commercial/api/security/validatelogin.json")
        .with(loginLens of Login("anuratransfersdev2@gmail.com", "Anura123"))
    httpClient(signInRequest)

    val addBookingPageRequest = Request(GET, "/tools/commercial/muga/addsinglebooking.aspx")
    httpClient(addBookingPageRequest)

    val nextThursday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.THURSDAY))

    val nextThursdayString = nextThursday.atStartOfDay(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    val footballGUID = "50ba1b7a-67f4-4c8d-a575-7dc8b5a43a30"
    val listSessionsRequest = Request(POST, "/Services/Commercial/api/muga/ListAvailableSessions.json")
        .with(listSessionsLens of ListSessions(nextThursdayString, footballGUID))
    val response = httpClient(listSessionsRequest)

    val bookingSessions = bookingSessionsLens.extract(response)
    val session = bookingSessions.findSlot().printed()
    session ?: error("no session")

    val bookSessionRequest = Request(POST, "/Services/Commercial/api/muga/AddBooking.json")
        .with(bookSlotLens of session.book(footballGUID))
//    httpClient(bookSessionRequest)

    // relied with
    // {"Code":200,"Data":{"Guid":"65295e82-1373-43eb-bda3-31e0ac8e6635"}}
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

fun <T : Any?> T.printed(): T? = this?.apply(::println)
