package com.example.configcenter

/**
 * Created by 张宇 on 2018/2/7.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
internal interface CLogger : ILog {
    var logger: ILog
}

internal class ConfigLogger : CLogger {
    override var logger: ILog = object : ILog {
        override fun i(info: String) {
            //Log.i("Publess", info)
        }

        override fun e(error: String) {
            //Log.e("publess", error)
        }
    }

    override fun i(info: String) = logger.i(info)


    override fun e(error: String) = logger.e(error)
}

interface ILog {
    fun i(info: String)

    fun e(error: String)
}