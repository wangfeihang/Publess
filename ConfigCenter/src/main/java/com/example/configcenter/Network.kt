package com.example.configcenter

import io.reactivex.Single

/**
 * Created by 张宇 on 2018/2/8.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
interface Network {
    fun performNetwork(req: CacheKey): Single<MobConfigValue>

    fun extractKey(key: MobConfigKey): CacheKey
}

internal interface Internet {
    var network: Network
}

internal class ConfigNet : Internet {
    override lateinit var network: Network
}
