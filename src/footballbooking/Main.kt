package footballbooking

import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.xml.XppDriver
import org.http4k.client.ApacheClient
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.cookie.cookies

fun main(args: Array<String>) {
    // home page: https://hsp.kingscross.co.uk
    // login page: https://hsp.kingscross.co.uk/Accounts/Login.aspx
    // booking page: https://hsp.kingscross.co.uk/tools/commercial/muga/addsinglebooking.aspx

    // username: anuratransfersdev2@gmail.com
    // password: ...

    val xStream = XStream(XppDriver())
    val httpClient = ApacheClient()

    val homePageRequest = Request(GET, "https://hsp.kingscross.co.uk").printed()

    httpClient(homePageRequest).let { response ->
        response.headers.forEach { it.printed() }
        response.cookies()
    }
}

fun <T> T.printed(): T = this.apply { println(this) }
