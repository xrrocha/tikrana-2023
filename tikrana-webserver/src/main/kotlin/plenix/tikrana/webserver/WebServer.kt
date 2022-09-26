package plenix.tikrana.webserver

import arrow.core.Either
import arrow.core.flatMap
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import plenix.tikrana.util.ApplicationFailure
import plenix.tikrana.util.Failure
import plenix.tikrana.util.log
import java.net.InetSocketAddress
import java.util.logging.Logger

typealias Path = String
typealias StatusCode = Int
typealias MethodName = String
typealias ContentType = String
typealias ExchangeHandler = (HttpExchange) -> Either<Failure, ExchangeResult>

data class ExchangeResult(val payload: String = "", val contextType: ContentType = "text/plain")

class WebServer(host: String, port: Int, backlog: Int = 0) {

    companion object {
        private val logger = Logger.getLogger(WebServer::class.qualifiedName)
    }

    private val pathHandlers: MutableMap<Path, MutableMap<MethodName, ExchangeHandler>> = mutableMapOf()

    private val httpServer = HttpServer.create(InetSocketAddress(host, port), backlog)

    fun delete(path: String, exchangeHandler: ExchangeHandler) = addContext(path, "DELETE", exchangeHandler)
    fun get(path: String, exchangeHandler: ExchangeHandler) = addContext(path, "GET", exchangeHandler)
    fun options(path: String, exchangeHandler: ExchangeHandler) = addContext(path, "OPTIONS", exchangeHandler)
    fun patch(path: String, exchangeHandler: ExchangeHandler) = addContext(path, "PATCH", exchangeHandler)
    fun post(path: String, exchangeHandler: ExchangeHandler) = addContext(path, "POST", exchangeHandler)
    fun put(path: String, exchangeHandler: ExchangeHandler) = addContext(path, "PUT", exchangeHandler)
    fun trace(path: String, exchangeHandler: ExchangeHandler) = addContext(path, "TRACE", exchangeHandler)

    private fun addContext(path: String, method: MethodName, exchangeHandler: ExchangeHandler) {

        pathHandlers.computeIfAbsent(path) { mutableMapOf() }
            .let { it[method] = exchangeHandler }

        httpServer.createContext(path) { exchange ->
            Either.fromNullable(pathHandlers[path])
                .tapLeft { exchange.notFound("No handler for $path") }
                .flatMap { handlers ->
                    Either.fromNullable(handlers[exchange.requestMethod])
                        .mapLeft { ApplicationFailure("Method not allowed ${exchange.requestMethod}") }
                        .tapLeft { exchange.methodNotAllowed() }
                        .flatMap { handler ->
                            handler(exchange)
                                .tapLeft(exchange::error)
                                .tap { result -> exchange.ok(result.payload, result.contextType) }
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
}
