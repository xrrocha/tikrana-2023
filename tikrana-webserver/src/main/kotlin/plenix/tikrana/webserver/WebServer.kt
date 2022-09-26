package plenix.tikrana.webserver

import arrow.core.Either
import arrow.core.flatMap
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import plenix.tikrana.util.ApplicationFailure
import plenix.tikrana.util.Failure
import plenix.tikrana.util.SystemFailure
import plenix.tikrana.util.log
import plenix.tikrana.webserver.ContentTypes.TextPlain
import java.io.OutputStream
import java.net.HttpURLConnection.HTTP_OK
import java.net.InetSocketAddress
import java.util.logging.Logger

typealias Path = String
typealias StatusCode = Int
typealias MethodName = String
typealias ContentType = String

data class ExchangeResult<T>(
    val payload: T?,
    val contextType: ContentType = TextPlain,
    val statusCode: Int = HTTP_OK
)

typealias ExchangeHandler<T> = (HttpExchange) -> Either<Failure, ExchangeResult<T>>

// TODO Add HTTPS support
// TODO Add authentication support
class WebServer(host: String, port: Int, private val httpCodec: HttpCodec, backlog: Int = 0) {

    companion object {
        private val logger = Logger.getLogger(WebServer::class.qualifiedName)
    }

    private val pathHandlers: MutableMap<Path, MutableMap<MethodName, ExchangeHandler<*>>> = mutableMapOf()

    private val httpServer = HttpServer.create(InetSocketAddress(host, port), backlog)

    fun <T> delete(path: String, exchangeHandler: ExchangeHandler<T>) = addContext(path, "DELETE", exchangeHandler)
    fun <T> get(path: String, exchangeHandler: ExchangeHandler<T>) = addContext(path, "GET", exchangeHandler)
    fun <T> options(path: String, exchangeHandler: ExchangeHandler<T>) = addContext(path, "OPTIONS", exchangeHandler)
    fun <T> patch(path: String, exchangeHandler: ExchangeHandler<T>) = addContext(path, "PATCH", exchangeHandler)
    fun <T> post(path: String, exchangeHandler: ExchangeHandler<T>) = addContext(path, "POST", exchangeHandler)
    fun <T> put(path: String, exchangeHandler: ExchangeHandler<T>) = addContext(path, "PUT", exchangeHandler)
    fun <T> trace(path: String, exchangeHandler: ExchangeHandler<T>) = addContext(path, "TRACE", exchangeHandler)

    private fun <T> addContext(path: String, method: MethodName, exchangeHandler: ExchangeHandler<T>) {

        pathHandlers.computeIfAbsent(path) { mutableMapOf() }
            .let { it[method] = exchangeHandler }

        httpServer.createContext(path) { exchange ->
            Either.fromNullable(pathHandlers[path])
                .tapLeft { exchange.notFound("No handler for $path") }
                .flatMap { handlers ->
                    @Suppress("UNCHECKED_CAST")
                    Either.fromNullable(handlers[exchange.requestMethod] as ExchangeHandler<T>)
                        .mapLeft { ApplicationFailure("Method not allowed ${exchange.requestMethod}") }
                        .tapLeft { exchange.methodNotAllowed() }
                        .flatMap { handler ->
                            handler(exchange)
                                .tapLeft(exchange::error)
                                .flatMap { result ->
                                    exchange.responseHeaders["Content-Type"] = listOf(result.contextType)
                                    exchange.sendResponseHeaders(result.statusCode, 0L)
                                    httpCodec.encode(result.payload, exchange.responseBody, result.contextType)
                                        .also { disposeOf(exchange.responseBody) }
                                }
                        }
                        .mapLeft { SystemFailure("Encoding and sending result", it) }
                        .tapLeft(logger::log)
                }
        }
    }

    fun start() {
        httpServer.start()
    }

    fun stop(delay: Int = 0) {
        httpServer.stop(delay)
    }

    private fun disposeOf(outputStream: OutputStream) =
        Either.catch { with(outputStream) { flush(); close() } }
            .mapLeft { SystemFailure("Disposing of response body", it) }
            .tapLeft(logger::log)
}
