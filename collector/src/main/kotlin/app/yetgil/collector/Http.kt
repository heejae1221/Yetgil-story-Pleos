package app.yetgil.collector

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** 테스트에서는 저장해 둔 응답을 돌려주는 가짜로 바꾼다. */
fun interface HttpGet {
    fun get(url: String): String
}

class JdkHttpGet(
    private val delayMs: Long = 300,
    private val retries: Int = 2,
) : HttpGet {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override fun get(url: String): String {
        var last: Exception? = null
        repeat(retries + 1) { attempt ->
            // 공공 API 에 부담을 주지 않도록 요청 사이를 띄운다
            if (delayMs > 0) Thread.sleep(delayMs * (attempt + 1))
            try {
                val req = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(30)).GET().build()
                val res = client.send(req, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
                if (res.statusCode() == 200) return res.body()
                last = IOException("HTTP ${res.statusCode()}: ${redact(url)}")
            } catch (e: IOException) {
                last = e
            }
        }
        throw IOException("요청 실패: ${redact(url)}", last)
    }

    companion object {
        /** 로그와 오류 메시지에 서비스 키가 남지 않게 한다. */
        fun redact(url: String): String = url.replace(Regex("(?i)(serviceKey=)[^&]*"), "$1***")
    }
}
