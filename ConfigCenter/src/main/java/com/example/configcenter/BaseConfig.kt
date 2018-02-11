package com.example.configcenter

import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Created by 张宇 on 2018/2/3.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
abstract class BaseConfig<D> {

    var data: D by ConfigDataDelegate { defaultValue() }

    fun update(): Disposable = Publess.update(this)

    fun pull(): Single<D> = Publess.pull(this)

    fun concern(): Flowable<D> = Publess.concern(this)

    var bssVersion: Long = 0
        internal set(value) {
            field = value
        }

    abstract val bssCode: String

    abstract fun dataParser(): DataParser<D>

    protected abstract fun defaultValue(): D
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

interface DataParser<out D> {
    fun parse(config: Map<String, String>): D
}