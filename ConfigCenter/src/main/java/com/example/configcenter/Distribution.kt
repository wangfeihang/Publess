package com.example.configcenter

import io.reactivex.Single

/**
 * Created by 张宇 on 2018/2/3.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 *
 */

/**
 * 分发中心
 */
internal interface Distribution {
    /**
     * 派件
     * 把需要的数据分发到目的地
     */
    fun <D> delivery(order: BaseConfig<D>, data: D, mobValue: MobConfigValue)

    /**
     * 打包
     * 把取出的数据解析成配置
     */
    fun <D> pack(order: BaseConfig<D>, payload: MobConfigValue): D

    /**
     * 下单
     * 去拿数据
     */
    fun <D> placeOrder(order: BaseConfig<D>): Single<D>
}

internal class Dispatcher : Distribution {

    private val net by lazy { ConfigCenter.network }
    private val repo = ConfigRepository()

    override fun <D> delivery(order: BaseConfig<D>, data: D, mobValue: MobConfigValue) {
        ConfigCenter.logger.d("delivery on ${Thread.currentThread()}")
        order.bssVersion = mobValue.bssVersion
        order.data = data
        order.whoCare.forEach { it.onChange(data) }
    }

    override fun <D> pack(order: BaseConfig<D>, payload: MobConfigValue): D {
        ConfigCenter.logger.d("start to parse MobConfigValue to data on ${Thread.currentThread()}")
        return order.dataParser().parse(payload.config)
    }

    override fun <D> placeOrder(order: BaseConfig<D>): Single<D> = placeOrder(order, net)

    private fun <D, T : CacheKey> placeOrder(order: BaseConfig<D>, net: Network<T>): Single<D> {
        ConfigCenter.logger.d("placeOrder for ${order.bssCode}")
        val mobKey = MobConfigKey(order.bssCode, order.bssVersion)
        val req: T = net.extractKey(mobKey)
        return repo.getData(order, mobKey, req, net::performNetwork)
    }
}