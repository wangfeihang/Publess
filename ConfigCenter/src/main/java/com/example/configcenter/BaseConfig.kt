package com.example.configcenter

import io.reactivex.Single
import io.reactivex.disposables.Disposable
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Created by 张宇 on 2018/2/3.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
abstract class BaseConfig<D> {

    var data: D by ConfigDataDelegate { defaultValue() }

    protected abstract fun defaultValue(): D

    internal val whoCare: MutableList<OnConfigChange<D>> = CopyOnWriteArrayList<OnConfigChange<D>>()

    fun addListener(listener: OnConfigChange<D>) {
        whoCare.add(listener)
    }

    fun removeListener(listener: OnConfigChange<D>) {
        whoCare.remove(listener)
    }

    fun update(single: OnConfigChange<D>): Disposable = ConfigCenter.placeOrder(this)
            .subscribe({ data ->
                single.onChange(data)
            })

    fun order(): Single<D> = ConfigCenter.placeOrder(this)

    var bssVersion: Long = 0
        internal set(value) {
            field = value
        }

    abstract val bssCode: String

    abstract fun dataParser(): DataParser<D>
}

private class ConfigDataDelegate<D>(private val initializer: () -> D) : ReadOnlyProperty<BaseConfig<D>, D> {
    private var value: D? = null

    internal operator fun setValue(thisRef: BaseConfig<D>, property: KProperty<*>, value: D) {
        this.value = value
    }

    override fun getValue(thisRef: BaseConfig<D>, property: KProperty<*>): D {
        return this.value ?: initializer()
    }
}

interface OnConfigChange<in D> {
    fun onChange(data: D)
}

interface DataParser<out D> {
    fun parse(config: Map<String, String>): D
}