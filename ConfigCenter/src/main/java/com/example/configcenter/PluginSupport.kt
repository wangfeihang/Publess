package com.example.configcenter

/**
 * Created by 张宇 on 2018/2/7.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
internal interface PluginSupport {
    fun initPlugin(pluginEntry: Any)
}

internal class ConfigPluginSupport : PluginSupport {

    override fun initPlugin(pluginEntry: Any) {
        val loaderClassName = pluginEntry::class.java.name + "_ConfigCenter_Initialization"
        val loader = Class.forName(loaderClassName).getConstructor().newInstance() as PluginInitialization
        ConfigCenter.getDataConfigMap().let {
            loader.loadInto(it)
            it.forEach {
                ConfigCenter.getClassConfigMap()[it.value.javaClass] = it.value
                ConfigCenter.getBssConfigMap()[it.value.bssCode] = it.value
            }
        }
    }
}

interface PluginInitialization {
    fun loadInto(config: MutableMap<Class<*>, BaseConfig<*>>)
}