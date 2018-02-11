package com.example.configcenter

/**
 * Created by 张宇 on 2018/2/7.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
internal interface Logger : ILog {
    var logger: ILog
}

internal class ConfigLogger : Logger {
    override var logger: ILog = Quiet

    override fun i(info: String) = logger.i(info)

    override fun e(error: Throwable) = logger.e(error)

    override fun d(info: String) = logger.d(info)
}

object Quiet : ILog {
    override fun i(info: String) {}

    override fun d(info: String) {}

    override fun e(error: Throwable) {}
}

interface ILog {
    fun i(info: String)

    fun d(info: String)

    fun e(error: Throwable)
}