package com.example.configcenter

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
     * 派件
     * 把需要的数据分发到目的地
     */
    fun <D> delivery(order: BaseConfig<D>, payload: D)

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

    override fun <D> delivery(order: BaseConfig<D>, payload: D) {
        order.data = payload
        order.whoCare.forEach { it.onChange(payload) }
    }

    override fun <D> pack(order: BaseConfig<D>, payload: MobConfigValue): D {
        return order.dataParser().parse(payload.config)
    }

    override fun <D> placeOrder(order: BaseConfig<D>): Single<D> {

        val key = MobConfigKey(order.bssCode, order.bssVersion, 0)

        return ConfigCenter.getData(key)
                .map { pack(order, it) }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSuccess { delivery(order, it) }
    }
}