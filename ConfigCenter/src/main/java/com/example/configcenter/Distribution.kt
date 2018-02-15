package com.example.configcenter

import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers

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
     * ## 派件
     * 把需要的数据分发到目的地
     */
    fun <D> delivery(order: BaseConfig<D>, data: D, mobValue: MobConfigValue)

    /**
     * ## 打包
     * 把取出的数据解析成配置
     */
    fun <D> pack(order: BaseConfig<D>, payload: MobConfigValue): D

    /**
     * ## 下单
     * 去拿[order]的数据
     */
    fun <D> placeOrder(order: BaseConfig<D>): Single<D>

    /**
     * ## 下单
     * 拿属于[bssCode]的所有配置的数据,
     * 若[bssCode]为空，则拿所有配置
     */
    fun placeOrder(bssCode: String? = null): Flowable<out BaseConfig<*>>

    /**
     * ## 关心对应配置
     * 会接受该配置所有更改
     */
    fun <D> concernOrder(order: BaseConfig<D>): Flowable<D>
}

internal class Dispatcher : Distribution {

    private val net by lazy { ConfigCenter.network }
    private val repo = ConfigRepository()

    @Suppress("UNCHECKED_CAST")
    override fun <D> delivery(order: BaseConfig<D>, data: D, mobValue: MobConfigValue) {
        //IO线程
        ConfigCenter.logger.i("监听器分发 ${order.name} ${Thread.currentThread()} 监听器数量: ${order.whoCare.size}")
        order.bssVersion = mobValue.bssVersion
        order.data = data
        for (emitter in order.whoCare) {
            emitter.onNext(data)
        }
    }

    override fun <D> pack(order: BaseConfig<D>, payload: MobConfigValue): D {
        ConfigCenter.logger.i("解析数据 ${order.name} data ${Thread.currentThread()}")
        return order.dataParser().parse(payload.config)
    }

    override fun <D> concernOrder(order: BaseConfig<D>): Flowable<D> {
        return Flowable.create({ e: FlowableEmitter<D> ->
            ConfigCenter.logger.i("start to concern ${order.bssCode}")
            order.whoCare.add(e)

            val disposable = order.pull().subscribe { data ->
                e.onNext(data)
            }

            e.setCancellable {
                order.whoCare.remove(e)
                disposable.dispose()
                ConfigCenter.logger.i("dispose concern ${order.bssCode}")
            }
        }, BackpressureStrategy.BUFFER).subscribeOn(AndroidSchedulers.mainThread())
    }

    override fun placeOrder(bssCode: String?) = requestAllConfig(bssCode)

    override fun <D> placeOrder(order: BaseConfig<D>) = requestConfig(order, net)

    private fun requestAllConfig(bssCode: String? = null): Flowable<out BaseConfig<*>> {

        fun <D> getConfigData(config: BaseConfig<D>): Single<BaseConfig<D>> {
            return placeOrder(config)
                    .map { config }
        }

        var flowable = Flowable.fromIterable(ConfigCenter.getDataConfigMap().values)
        if (bssCode != null) {
            flowable = flowable.filter { it.bssCode == bssCode }
        }
        return flowable.flatMapSingle { getConfigData(it) }
    }

    private fun <D, T : CacheKey> requestConfig(order: BaseConfig<D>, net: Network<T>): Single<D> {
        ConfigCenter.logger.i("需要${order.name}（${order.bssCode}）的数据  ${Thread.currentThread()}")
        val mobKey = MobConfigKey(order.bssCode, order.bssVersion)
        val req = net.extractKey(mobKey)
        return repo.getData(order, mobKey, req, net::performNetwork)
    }
}