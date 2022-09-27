package plenix.tikrana.http.server

import plenix.tikrana.http.SimpleHttpCodec
import plenix.tikrana.util.initLogger
import plenix.tikrana.util.parseArgs

fun main(args: Array<String>) {
    val argMap = parseArgs(args)
    val host = argMap["host"] ?: "localhost"
    val port = argMap["port"]?.toInt() ?: 8080

    initLogger()

    val webServer = WebServer(host, port, SimpleHttpCodec).also { it.start() }
    Runtime.getRuntime().addShutdownHook(Thread { webServer.stop() })
}
