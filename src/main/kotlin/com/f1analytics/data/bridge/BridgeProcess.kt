package com.f1analytics.data.bridge

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class BridgeProcess(private val port: Int = 9001) {

    private var process: Process? = null

    fun start() {
        process = ProcessBuilder("python3", "bridge/bridge.py", port.toString())
            .redirectErrorStream(true)
            .start()
        Thread {
            process!!.inputStream.bufferedReader()
                .lines().forEach { logger.info { "[bridge] $it" } }
        }.start()
        logger.info { "Bridge process started (PID: ${process!!.pid()})" }
    }

    fun stop() {
        process?.destroy()
        logger.info { "Bridge process stopped" }
    }

    fun isAlive(): Boolean = process?.isAlive == true
}
