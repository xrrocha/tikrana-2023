package plenix.tikrana.util

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.io.FileNotFoundException
import java.io.InputStream


// TODO Group CLI args by --key*
fun parseArgs(args: Array<String>) =
    args
        .filter { it.contains('=') }
        .associate {
            val pos = it.indexOf('=')
            Pair(it.substring(0, pos), it.substring(pos + 1))
        }

fun loadResource(resourceName: String) =
    openResource(resourceName).map { String(it.readAllBytes()) }

fun openResource(resourceName: String): Either<FileNotFoundException, InputStream> =
    Thread.currentThread().contextClassLoader.getResourceAsStream(resourceName)?.right()
        ?: FileNotFoundException("No such resource: $resourceName").left()
