package plenix.tikrana.util

import java.util.logging.Level
import java.util.logging.Logger

sealed class Failure(level: String, context: String, cause: Throwable? = null) {

    val message = "$level error: $context${cause?.let { " (${cause.message ?: cause})" } ?: ""}"
    private val exception by lazy { RuntimeException(message, cause) }

    fun toException() = exception

    fun doThrow(): Nothing = throw exception

    override fun toString(): String = "Failure($message)"
}

class SystemFailure(context: String, cause: Throwable) : Failure("System", context, cause)

open class ApplicationFailure(context: String, cause: Throwable? = null) : Failure("Application", context, cause)

fun Logger.log(failure: Failure, logStackTrace: Boolean = false) {
    val loggingLevel = when (failure) {
        is SystemFailure -> Level.SEVERE
        else -> Level.WARNING
    }
    if (logStackTrace) log(loggingLevel, failure.message, failure)
    else log(loggingLevel, failure.message)
}