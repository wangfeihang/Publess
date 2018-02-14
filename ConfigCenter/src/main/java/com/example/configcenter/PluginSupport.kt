package com.example.configcenter

/**
 * Created by 张宇 on 2018/2/7.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
/**
 * # 插件化支持
 */
internal interface PluginSupport {
    fun initPlugin(pluginEntry: Any)

    fun initConfig(map: Map<Class<*>, BaseConfig<*>>)
}

internal class ConfigPluginSupport : PluginSupport {

    override fun initPlugin(pluginEntry: Any) {
        ConfigCenter.logger.i("initPlugin: $pluginEntry")
        val loaderClassName = pluginEntry::class.java.name + "${'$'}ConfigCenter${'$'}Initialization"
        val loader = Class.forName(loaderClassName).getConstructor().newInstance() as PluginInitialization
        val map = mutableMapOf<Class<*>, BaseConfig<*>>()
        loader.loadInto(map)
        initConfig(map)
        ConfigCenter.logger.i("success load data class and config: " +
                map.entries.joinToString(prefix = "[", postfix = "]") { (cls, config) ->
                    "${cls.simpleName}:${config.name}"
                })
    }

    override fun initConfig(map: Map<Class<*>, BaseConfig<*>>) {
        ConfigCenter.getDataConfigMap().putAll(map)
        for (config in map.values) {
            ConfigCenter.getClassConfigMap()[config.javaClass] = config
        }
    }
}

interface PluginInitialization {
    fun loadInto(config: MutableMap<Class<*>, BaseConfig<*>>)
}