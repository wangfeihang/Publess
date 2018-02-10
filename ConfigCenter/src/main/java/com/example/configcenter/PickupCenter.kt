package com.example.configcenter

/**
 * Created by 张宇 on 2018/2/3.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */

/**
 * 取件中心
 */
internal interface PickupCenter {

    fun <D> get(configCls: Class<out BaseConfig<D>>): BaseConfig<D>

    fun <D> get(bssCode: String): BaseConfig<D>

    fun <D> of(dataCls: Class<out D>): BaseConfig<D>

    fun getDataConfigMap(): MutableMap<Class<*>, BaseConfig<*>>

    fun getClassConfigMap(): MutableMap<Class<out BaseConfig<*>>, BaseConfig<*>>

    fun getBssConfigMap(): MutableMap<String, BaseConfig<*>>
}

internal class InBox : PickupCenter {

    private val configMap = mutableMapOf<Class<out BaseConfig<*>>, BaseConfig<*>>()
    private val bssCodeMap = mutableMapOf<String, BaseConfig<*>>()
    private val dataMap = mutableMapOf<Class<*>, BaseConfig<*>>()

    @Suppress("UNCHECKED_CAST")
    @Throws(InstantiationException::class, IllegalAccessException::class)
    override fun <D> get(configCls: Class<out BaseConfig<D>>): BaseConfig<D> =
            configMap[configCls] as? BaseConfig<D>
                    ?: throw IllegalArgumentException("cannot find the config of " +
                            "the data $configCls,is it annotated by @BssConfig?")

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalArgumentException::class)
    override fun <D> get(bssCode: String): BaseConfig<D> = bssCodeMap[bssCode] as? BaseConfig<D>
            ?: throw IllegalArgumentException("the bssCode [$bssCode] is not exist")

    @Suppress("UNCHECKED_CAST")
    override fun <D> of(dataCls: Class<out D>): BaseConfig<D> = (dataMap[dataCls]
            ?: initDataConfig(dataCls)) as? BaseConfig<D>
            ?: throw IllegalArgumentException("cannot find the config of " +
                    "the data $dataCls,is it annotated by @BssConfig?")

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    private fun <D> initDataConfig(dataCls: Class<out D>): BaseConfig<D> {
        ConfigCenter.logger.i("initDataConfig: $dataCls")
        val config = dataMap[dataCls]
        if (config != null) {
            return config as BaseConfig<D>
        }
        val initializer = Class.forName("${dataCls.name}${'$'}${'$'}Initializer")
                .newInstance() as PluginInitialization
        val map = mutableMapOf<Class<*>, BaseConfig<*>>()
        initializer.loadInto(map)
        ConfigCenter.logger.i("success load data class and config: $map")
        ConfigCenter.initConfig(map)
        return dataMap[dataCls] as BaseConfig<D>
    }

    override fun getDataConfigMap(): MutableMap<Class<*>, BaseConfig<*>> = dataMap

    override fun getClassConfigMap(): MutableMap<Class<out BaseConfig<*>>, BaseConfig<*>> = configMap

    override fun getBssConfigMap(): MutableMap<String, BaseConfig<*>> = bssCodeMap
}