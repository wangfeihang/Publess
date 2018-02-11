package com.example.configcenter

import io.reactivex.Single
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Created by 张宇 on 2018/2/8.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
interface Network<T : CacheKey> {
    fun performNetwork(req: T): Single<MobConfigValue>

    fun extractKey(key: MobConfigKey): T
}

internal interface CustomNet {
    var network: Network<out CacheKey>
}

internal class ConfigNet : CustomNet {
    override var network: Network<out CacheKey> by LateInit()

    class LateInit : ReadWriteProperty<ConfigNet, Network<out CacheKey>> {

        private var value: Network<out CacheKey>? = null

        override fun getValue(thisRef: ConfigNet, property: KProperty<*>): Network<out CacheKey> {
            return value ?: throw IllegalStateException("you need to call the method" +
                    " 'Publess.performNetwork(Network)' before using config")
        }

        override fun setValue(thisRef: ConfigNet, property: KProperty<*>, net: Network<out CacheKey>) {
            if (value == null) {
                value = net
            } else {
                throw IllegalStateException("'Publess.performNetork(Network)' can just be called once")
            }
        }

    }
}
