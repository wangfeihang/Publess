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
    fun initplugin(pluginEntry: Any): Unit = ConfigCenter.initPlugin(pluginEntry)

    @JvmStatic
    fun enableLog(logger: ILog) {
        ConfigCenter.logger = logger
    }

    @JvmStatic
    fun logger(): ILog = ConfigCenter.logger

    @JvmStatic
    fun perfomNetwork(network: Network) {
        ConfigCenter.network = network
    }
}

internal object ConfigCenter :
        PluginSupport by ConfigPluginSupport(), //插件化支持
        PickupCenter by InBox(), //取件中心
        Distribution by Dispatcher(), //分发中心
        Repository<MobConfigKey, MobConfigValue> by ConfigRepository(), //仓库
        Internet by ConfigNet(),
        CLogger by ConfigLogger() //日志