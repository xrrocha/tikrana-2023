package plenix.tikrana.util

import java.util.logging.Level
import java.util.logging.Logger
import javax.script.ScriptException

sealed class Failure(level: String, context: String, cause: Throwable? = null) :
    RuntimeException("$level error: $context${cause?.let { " (${cause.message})" } ?: ""}", cause) {

    override fun toString(): String = "Failure($message)"
}

class SystemFailure(context: String, cause: Throwable) : Failure("System", context, cause)

open class ApplicationFailure(context: String, cause: Throwable? = null) : Failure("Application", context, cause)

// TODO Extend ScriptingFailure to show offending source code, if any
class ScriptingFailure(context: String, script: String, cause: Throwable) :
    ApplicationFailure(location(context, script, cause), Exception("", cause)) {
    companion object {
        private val Location = "(.*)\\(([^:]+):([^:]+):([^:]+)\\)\$".toRegex()
        fun location(context: String, script: String, cause: Throwable): String {
            val items = Location.matchAt(cause.message ?: "", 0)?.groupValues
            return context + when {
                cause is ScriptException && cause.lineNumber != -1 ->
                    " ${cause.fileName}(${cause.lineNumber}:${cause.columnNumber})"

                items != null -> {
                    // TODO Get script filename
                    val (_, errorMessage, _, lineNumber, columnNumber) = items
                    """
                         line $lineNumber, column $columnNumber: $errorMessage
                        ${script.split("\n")[lineNumber.toInt() - 1]}
                        ${"".padStart(columnNumber.toInt() - 1, '-')}^
                    """.trimIndent()
                }

                else -> ""
            }

        }
    }
}

fun Logger.log(failure: Failure) {
    val loggingLevel = when (failure) {
        is SystemFailure -> Level.SEVERE
        else -> Level.WARNING
    }
    log(loggingLevel, failure.message/*, failure*/)
}