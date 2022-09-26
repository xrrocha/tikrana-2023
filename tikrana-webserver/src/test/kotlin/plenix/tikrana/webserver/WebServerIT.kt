package plenix.tikrana.webserver

import arrow.core.right
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import plenix.tikrana.util.initLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import kotlin.test.assertTrue

class WebServerIT {

    @Test
    fun `Root paths work as advertised`() {

        val greeting = "WebServer rules!"

        webServer.get("/") {
            ExchangeResult(greeting, "text/plain").right()
        }
        webServer.put("/") { exchange ->
            val payload = String(exchange.requestBody.readAllBytes())
            ExchangeResult(payload.reversed(), "text/plain").right()
        }
        webServer.post("/") { exchange ->
            val payload = String(exchange.requestBody.readAllBytes())
            ExchangeResult(payload.uppercase().uppercase(), "text/plain").right()
        }

        webServerTester.get("/") { it == greeting }
        webServerTester.put("/", "This is PUT") { it == "TUP si sihT" }
        webServerTester.post("/", "This is POST") { it == "THIS IS POST" }
    }

    private val host = "localhost"
    private val port = 9876
    private val baseUri = "http://$host:$port"

    private lateinit var webServer: WebServer

    init {
        initLogger()
    }

    @BeforeEach
    fun startWebServer() {
        webServer = WebServer(host, port, SimpleHttpCodec)
        webServer.start()
    }

    @AfterEach
    fun stopWebServer() {
        webServer.stop()
    }

    private val webServerTester = object {
        private val client = HttpClient.newHttpClient()

        fun get(path: String, test: (String) -> Boolean) =
            send(builder(path) { it.GET() }, test)

        fun post(path: String, payload: String, test: (String) -> Boolean) =
            send(builder(path) { it.POST(BodyPublishers.ofString(payload)) }, test)

        fun put(path: String, payload: String, test: (String) -> Boolean) =
            send(builder(path) { it.PUT(BodyPublishers.ofString(payload)) }, test)

        private fun builder(path: String, action: (HttpRequest.Builder) -> Unit) =
            HttpRequest.newBuilder()
                .uri(URI.create("$baseUri$path"))
                .apply(action)
                .build()

        private fun send(request: HttpRequest, test: (String) -> Boolean) {
            client.sendAsync(request, BodyHandlers.ofString())
                .thenApply(HttpResponse<String>::body)
                .thenAccept { assertTrue(test(it)) }
                .join()
        }
    }
}
