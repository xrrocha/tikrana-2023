package plenix.tikrana.webserver

import com.sun.net.httpserver.HttpExchange
import plenix.tikrana.util.ApplicationFailure
import plenix.tikrana.util.Failure
import plenix.tikrana.util.SystemFailure
import plenix.tikrana.webserver.ContentTypes.ContentTypeHeader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.nio.charset.Charset

fun HttpExchange.requestContentType(): ContentType =
    requestHeaders[ContentTypeHeader]?.firstOrNull() ?: "text/plain"

fun HttpExchange.responseContentType(vararg contentType: ContentType) {
    requestHeaders[ContentTypeHeader] = contentType.toList()
}

fun HttpExchange.requestBodyAsString(charset: Charset = Charsets.UTF_8) =
    String(requestBody.readAllBytes(), charset)

fun HttpExchange.ok(payload: String? = null, contentType: ContentType = "text/plain") =
    respondWith(HttpURLConnection.HTTP_OK, payload ?: "", contentType)

fun HttpExchange.ok(contentType: ContentType = "text/plain", writer: (OutputStream) -> Unit) =
    respondWith(HttpURLConnection.HTTP_OK, writer, contentType)

fun HttpExchange.error(failure: Failure) =
    when (failure) {
        is ApplicationFailure -> badRequest(failure.message)
        is SystemFailure -> serverError(failure.message)
    }

fun HttpExchange.badRequest(payload: String? = null, contentType: ContentType = "text/plain") =
    respondWith(HttpURLConnection.HTTP_BAD_REQUEST, payload, "Bad request", contentType)

fun HttpExchange.notFound(payload: String?, contentType: ContentType = "text/plain") =
    respondWith(HttpURLConnection.HTTP_NOT_FOUND, payload, "Not found", contentType)

fun HttpExchange.methodNotAllowed(payload: String? = null, contentType: ContentType = "text/plain") =
    respondWith(HttpURLConnection.HTTP_BAD_METHOD, payload, "Method $requestMethod not allowed", contentType)

fun HttpExchange.notAcceptable(payload: String? = null, contentType: ContentType = "text/plain") =
    respondWith(HttpURLConnection.HTTP_NOT_ACCEPTABLE, payload, "Not acceptable", contentType)

fun HttpExchange.serverError(payload: String? = null, contentType: ContentType = "text/plain") =
    respondWith(HttpURLConnection.HTTP_INTERNAL_ERROR, payload, "Server error", contentType)

fun HttpExchange.respondWith(statusCode: Int, payload: String?, message: String, contentType: ContentType) {
    respondWith(statusCode, payload ?: "$message: $requestURI", contentType)
}

fun HttpExchange.respondWith(
    statusCode: StatusCode,
    payload: String,
    contentType: ContentType = "text/plain",
    headers: Map<String, List<String>> = emptyMap()
) {
    respondWith(statusCode, payload.toByteArray(), contentType, headers)
}

fun HttpExchange.respondWith(
    statusCode: StatusCode,
    payload: ByteArray,
    contentType: ContentType = "text/plain",
    headers: Map<String, List<String>> = emptyMap()
) {
    respondWith(statusCode, payload.size.toLong(), { it.write(payload) }, contentType, headers)
}

fun HttpExchange.respondWith(
    statusCode: StatusCode,
    writer: (OutputStream) -> Unit,
    contentType: ContentType = "text/plain",
    headers: Map<String, List<String>> = emptyMap()
) {
    respondWith(statusCode, 0L, writer, contentType, headers)
}

fun HttpExchange.respondWith(
    statusCode: StatusCode,
    contentLength: Long,
    writer: (OutputStream) -> Unit,
    contentType: ContentType = "text/plain",
    headers: Map<String, List<String>> = emptyMap()
) {
    responseHeaders.apply {
        putAll(headers)
        put(ContentTypeHeader, listOf(contentType))
    }

    sendResponseHeaders(statusCode, contentLength)

    responseBody.apply {
        writer(this)
        flush()
        close()
    }
}
