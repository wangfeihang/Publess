package com.example.configcenter

import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import io.reactivex.Single
import java.util.concurrent.ConcurrentHashMap

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

    /**
     * 关心对应配置
     * 会接受该配置所有更改
     */
    fun <D> concernOrder(order: BaseConfig<D>): Flowable<D>
}

internal class Dispatcher : Distribution {

    private val concernMap: MutableMap<String, MutableList<out FlowableEmitter<*>>> = ConcurrentHashMap()
    private val net by lazy { ConfigCenter.network }
    private val repo = ConfigRepository()

    @Suppress("UNCHECKED_CAST")
    override fun <D> delivery(order: BaseConfig<D>, data: D, mobValue: MobConfigValue) {
        ConfigCenter.logger.d("delivery on ${Thread.currentThread()}")
        order.bssVersion = mobValue.bssVersion
        order.data = data
        (concernMap[order.bssCode] as? MutableList<FlowableEmitter<D>>)
                ?.forEach { emitter -> emitter.onNext(data) }
    }

    override fun <D> pack(order: BaseConfig<D>, payload: MobConfigValue): D {
        ConfigCenter.logger.d("start to parse MobConfigValue to data on ${Thread.currentThread()}")
        return order.dataParser().parse(payload.config)
    }

    override fun <D> concernOrder(order: BaseConfig<D>): Flowable<D> {

        @Suppress("UNCHECKED_CAST")
        fun whoCare(bssCode: String): MutableList<FlowableEmitter<D>> =
                concernMap[bssCode] as? MutableList<FlowableEmitter<D>>
                        ?: mutableListOf<FlowableEmitter<D>>().also { concernMap[bssCode] = it }

        return Flowable.create({ e: FlowableEmitter<D> ->
            ConfigCenter.logger.i("start to concern ${order.bssCode}")
            whoCare(order.bssCode).add(e)

            e.setCancellable {
                ConfigCenter.logger.i("dispose concern ${order.bssCode}")
                whoCare(order.bssCode).remove(e)
            }
        }, BackpressureStrategy.BUFFER)
    }

    override fun <D> placeOrder(order: BaseConfig<D>): Single<D> = placeOrder(order, net)

    private fun <D, T : CacheKey> placeOrder(order: BaseConfig<D>, net: Network<T>): Single<D> {
        ConfigCenter.logger.d("placeOrder for ${order.bssCode}")
        val mobKey = MobConfigKey(order.bssCode, order.bssVersion)
        val req = net.extractKey(mobKey)
        return repo.getData(order, mobKey, req, net::performNetwork)
    }
}