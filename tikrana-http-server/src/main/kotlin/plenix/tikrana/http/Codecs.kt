package plenix.tikrana.http

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import plenix.tikrana.util.ApplicationFailure
import plenix.tikrana.util.Failure
import java.io.InputStream
import java.io.OutputStream

// NOTE Not passing Codec generic type info is deliberate! Payloads are assumed to contain runtime type info
interface Codec {
    fun decode(inputStream: InputStream): Any?
    fun encode(data: Any?, outputStream: OutputStream): Unit?
}

object TextPlainCodec : Codec {
    override fun decode(inputStream: InputStream) =
        String(inputStream.readAllBytes())

    override fun encode(data: Any?, outputStream: OutputStream) =
        data?.let { outputStream.write(it.toString().toByteArray()) }
}

open class JacksonCode(private val objectMapper: ObjectMapper) : Codec {
    override fun decode(inputStream: InputStream): Any? =
        objectMapper.readValue(inputStream, Any::class.java)

    override fun encode(data: Any?, outputStream: OutputStream) =
        data?.let { objectMapper.writeValue(outputStream, it) }
}

open class HttpCodec(private val codecs: Map<ContentType, Codec>) {

    init {
        codecs.keys.filterNot(ContentTypes::isValid).let { invalidContentTypes ->
            require(invalidContentTypes.isEmpty()) {
                "Invalid content types: ${invalidContentTypes.joinToString(", ", "[", "]")}}"
            }
        }
    }

    fun decode(inputStream: InputStream, contentType: ContentType): Either<Failure, Any?> =
        ContentTypes.validate(contentType)
            .flatMap { cType ->
                Either.fromNullable(codecs[cType])
                    .mapLeft { ApplicationFailure("No codec for content type $cType") }
            }
            .flatMap { codec ->
                Either.catch { codec.decode(inputStream) }
                    .mapLeft { ApplicationFailure("Error decoding input stream", it) }
            }

    fun encode(data: Any?, outputStream: OutputStream, contentType: String): Either<Failure, Unit?> =
        Either.fromNullable(codecs[contentType])
            .mapLeft { ApplicationFailure("No codec for content type $contentType") }
            .flatMap { codec ->
                Either.catch { codec.encode(data, outputStream) }
                    .mapLeft { ApplicationFailure("Error encoding to output stream", it) }
            }

    fun decode(inputStream: InputStream, contentTypes: List<String>): Either<Failure, Any?> =
        contentTypes.find(codecs::containsKey)?.let { decode(inputStream, it) }
            ?: ApplicationFailure("No applicable content types").left()

    fun encode(data: Any?, outputStream: OutputStream, contentTypes: List<String>): Either<Failure, Unit?> =
        contentTypes.find(codecs::containsKey)?.let { encode(data, outputStream, it) }
            ?: ApplicationFailure("No applicable content types").left()
}

object SimpleHttpCodec : HttpCodec(
    mapOf(
        ContentTypes.TextPlain to TextPlainCodec,
        "application/json" to JacksonCode(ObjectMapper().registerKotlinModule()),
        "application/yaml" to JacksonCode(YAMLMapper.builder().build().registerKotlinModule()),
    )
)
