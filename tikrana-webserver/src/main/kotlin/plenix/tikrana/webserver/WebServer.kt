package plenix.tikrana.webserver

import arrow.core.Either
import arrow.core.flatMap
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import plenix.tikrana.util.ApplicationFailure
import plenix.tikrana.util.Failure
import plenix.tikrana.util.SystemFailure
import plenix.tikrana.util.log
import plenix.tikrana.webserver.ContentTypes.ContentTypeHeader
import plenix.tikrana.webserver.ContentTypes.TextPlain
import java.io.OutputStream
import java.net.HttpURLConnection.HTTP_OK
import java.net.InetSocketAddress
import java.util.logging.Logger

typealias Path = String
typealias StatusCode = Int
typealias MethodName = String
typealias ContentType = String

data class ExchangeResult<O>(
    val payload: O?,
    // FIXME Why always specify response content type?
    val contextType: ContentType = TextPlain,
    val statusCode: Int = HTTP_OK
)

typealias ExchangeHandler<I, O> = (I, HttpExchange) -> Either<Failure, ExchangeResult<O>>

// TODO Add HTTPS support
// TODO Add authentication support
class WebServer(host: String, port: Int, private val httpCodec: HttpCodec, backlog: Int = 0) {

    companion object {
        private val logger = Logger.getLogger(WebServer::class.qualifiedName)
    }

    private val pathHandlers: MutableMap<Path, MutableMap<MethodName, ExchangeHandler<*, *>>> = mutableMapOf()

    private val httpServer = HttpServer.create(InetSocketAddress(host, port), backlog)

    // FIXME Remove request payload type from other methods
    fun <I, O> delete(path: String, exchangeHandler: ExchangeHandler<I, O>) =
        addContext(path, "DELETE", exchangeHandler)

    fun <O> get(path: String, exchangeHandler: (HttpExchange) -> Either<Failure, ExchangeResult<O>>) =
        addContext(path, "GET") { _: Any?, exchange -> exchangeHandler(exchange) }

    fun <I, O> options(path: String, exchangeHandler: ExchangeHandler<I, O>) =
        addContext(path, "OPTIONS", exchangeHandler)

    fun <I, O> patch(path: String, exchangeHandler: ExchangeHandler<I, O>) = addContext(path, "PATCH", exchangeHandler)
    fun <I, O> post(path: String, exchangeHandler: ExchangeHandler<I, O>) = addContext(path, "POST", exchangeHandler)
    fun <I, O> put(path: String, exchangeHandler: ExchangeHandler<I, O>) = addContext(path, "PUT", exchangeHandler)
    fun <I, O> trace(path: String, exchangeHandler: ExchangeHandler<I, O>) = addContext(path, "TRACE", exchangeHandler)

    private fun <I, O> addContext(path: String, method: MethodName, exchangeHandler: ExchangeHandler<I, O>) {

        pathHandlers.computeIfAbsent(path) { mutableMapOf() }
            .let { it[method] = exchangeHandler }

        httpServer.createContext(path) { exchange ->
            Either.fromNullable(pathHandlers[path])
                .tapLeft { exchange.notFound("No handler for $path") }
                .flatMap { handlers ->
                    @Suppress("UNCHECKED_CAST")
                    Either.fromNullable(handlers[exchange.requestMethod] as ExchangeHandler<I, O>)
                        .mapLeft { ApplicationFailure("Method not allowed ${exchange.requestMethod}") }
                        .tapLeft { exchange.methodNotAllowed() }
                        .flatMap { handler ->
                            val contentType = exchange.requestHeaders[ContentTypeHeader] ?: listOf(TextPlain)
                            httpCodec.decode(exchange.requestBody, contentType)
                                .flatMap { requestPayload ->
                                    Either.catch { requestPayload as I }
                                        .mapLeft { ApplicationFailure("Request type mismatch", it) }
                                }
                                .flatMap { requestPayload ->
                                    handler(requestPayload, exchange)
                                        .tapLeft(exchange::error)
                                        .flatMap { result ->
                                            exchange.responseHeaders[ContentTypeHeader] = listOf(result.contextType)
                                            exchange.sendResponseHeaders(result.statusCode, 0L)
                                            httpCodec.encode(result.payload, exchange.responseBody, result.contextType)
                                                .also { disposeOf(exchange.responseBody) }
                                        }
                                }
                        }
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
