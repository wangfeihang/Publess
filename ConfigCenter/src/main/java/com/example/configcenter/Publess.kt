package com.example.configcenter

/**
 * Created by 张宇 on 2018/2/2.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 *
 * 配置中心
 */
object Publess {

    @JvmStatic
    fun <D> get(configCls: Class<out BaseConfig<D>>): BaseConfig<D> = ConfigCenter.get(configCls)

    @JvmStatic
    fun <D> of(dataCls: Class<D>): BaseConfig<D> = ConfigCenter.of(dataCls)

    @JvmStatic
    fun initPlugin(pluginEntry: Any): Unit = ConfigCenter.initPlugin(pluginEntry)

    @JvmStatic
    fun enableLog(logger: ILog) {
        ConfigCenter.logger = logger
    }

    @JvmStatic
    fun performNetwork(network: Network<out CacheKey>) {
        ConfigCenter.network = network
    }
}

internal object ConfigCenter : //快递中心
        PluginSupport by ConfigPluginSupport(), //跨省支持
        PickupCenter by InBox(), //取件箱
        Distribution by Dispatcher(), //分发派件
        CustomNet by ConfigNet(), //个性化物流
        Logger by ConfigLogger() //记账