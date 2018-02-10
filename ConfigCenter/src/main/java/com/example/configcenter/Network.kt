package com.example.configcenter

import io.reactivex.Single

/**
 * Created by 张宇 on 2018/2/8.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
interface Network<T : CacheKey> {
    fun performNetwork(req: T): Single<MobConfigValue>

    fun extractKey(key: MobConfigKey): T
}

internal interface Internet {
    var network: Network<out CacheKey>
}

internal class ConfigNet : Internet {
    override lateinit var network: Network<out CacheKey>
}
