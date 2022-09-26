package plenix.tikrana.webserver

import plenix.tikrana.util.initLogger

fun main(args: Array<String>) {
    val argMap = parseArgs(args)
    val host = argMap["host"] ?: "localhost"
    val port = argMap["port"]?.toInt() ?: 8080

    initLogger()


    val webServer = WebServer(host, port, SimpleHttpCodec).also { it.start() }
    Runtime.getRuntime().addShutdownHook(Thread { webServer.stop() })
}

// Group by --key
fun parseArgs(args: Array<String>) =
    args
        .filter { it.contains('=') }
        .associate {
            val pos = it.indexOf('=')
            Pair(it.substring(0, pos), it.substring(pos + 1))
        }