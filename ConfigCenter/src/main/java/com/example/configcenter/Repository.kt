package com.example.configcenter

import android.util.LruCache
import com.google.gson.JsonParseException
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Created by 张宇 on 2018/2/3.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
/**
 * # 数据仓库
 */
internal interface Repository {
    /**
     * @param config 要获取的配置
     * @param mobKey 配置产生的请求
     * @param req 自定义请求
     * @param net 自定义网络交互的实现
     */
    fun <DATA, KEY : CacheKey> getData(
            config: BaseConfig<DATA>,
            mobKey: MobConfigKey,
            req: KEY,
            net: Net<KEY>): Single<DATA>
}

typealias Net<T> = (T) -> Single<MobConfigValue>

internal class ConfigRepository : Repository {

    private val remote = MemoryRepo(DataCacheRepo(CacheRemoteRepos(RemoteRepos())))
    private val local = LocalRepo()

    override fun <DATA, KEY : CacheKey> getData(
            config: BaseConfig<DATA>,
            mobKey: MobConfigKey,
            req: KEY,
            net: Net<KEY>): Single<DATA> {

        return remote.getData(config, mobKey, req, net)
                .onErrorResumeNext({ throwable ->
                    // 如果是数据解析时产生的错误，希望抛出去在开发阶段解决
                    if (throwable is NumberFormatException || throwable is JsonParseException) {
                        throw throwable
                    }
                    local.getData(config, mobKey, req, net)
                })
    }
}

/**
 * Decorator Pattern
 */
private class MemoryRepo(private val repo: Repository) : Repository {

    companion object {
        const val cacheTime = 2 * 60 * 1000L
    }

    private val lastTimeData = mutableMapOf<BaseConfig<*>, Pair<CacheKey, Long>>()

    override fun <DATA, KEY : CacheKey> getData(
            config: BaseConfig<DATA>,
            mobKey: MobConfigKey,
            req: KEY,
            net: Net<KEY>): Single<DATA> {

        val pair = lastTimeData[config]
        if (pair != null) {
            val (key, time) = pair
            if (key == req && System.currentTimeMillis() - time < cacheTime) {
                ConfigCenter.logger.i("内存命中 直接返回$config 的data")
                return Single.just(config.data).observeOn(AndroidSchedulers.mainThread())
            }
        }

        return repo.getData(config, mobKey, req, net)
                .doOnSuccess {
                    lastTimeData[config] = Pair(req, System.currentTimeMillis())
                }
                .observeOn(AndroidSchedulers.mainThread())
    }
}

/**
 * read from local
 */
private class LocalRepo : Repository {
    override fun <DATA, KEY : CacheKey> getData(
            config: BaseConfig<DATA>,
            mobKey: MobConfigKey,
            req: KEY,
            net: Net<KEY>): Single<DATA> {
        TODO("not implemented") //有空再写
    }
}

internal interface Repo {
    /**
     * @param config 要获取的配置
     * @param mobKey 配置产生的请求
     * @param req 自定义请求
     * @param net 自定义网络交互的实现
     */
    fun <DATA, KEY : CacheKey> getData(
            config: BaseConfig<DATA>,
            mobKey: MobConfigKey,
            req: KEY,
            net: Net<KEY>): Single<MobConfigValue>
}

/**
 * 从网络读取，相同请求在2分钟内会读缓存
 */
private class RemoteRepos : Repo {
    val cache = LruCache<CacheKey, Pair<MobConfigValue, Long>>(6)
    val limit = 6 * 60 * 1000L

    override fun <DATA, KEY : CacheKey> getData(
            config: BaseConfig<DATA>,
            mobKey: MobConfigKey,
            req: KEY,
            net: (KEY) -> Single<MobConfigValue>): Single<MobConfigValue> {

        var pair: Pair<MobConfigValue, Long>? = null
        synchronized(cache) {
            pair = cache[req]
        }
        pair?.let {
            val (value, time) = it
            if (System.currentTimeMillis() - time < limit) {
                ConfigCenter.logger.i("缓存有该请求的数据 直接返回 ${config.bssCode}")
                return Single.just(value)
            }
        }
        ConfigCenter.logger.i("开始网络请求 ${config.bssCode}")
        return net(req)
                .doOnSuccess {
                    synchronized(cache) {
                        cache.put(req, Pair(it, System.currentTimeMillis()))
                    }
                }
                .subscribeOn(Schedulers.io())
    }
}

/**
 * 让同个Config同个Request 不会多次请求
 */
private class DataCacheRepo(private val repo: Repository) : Repository {

    private data class UniqueKey(private val config: BaseConfig<*>, private val req: CacheKey) {
        override fun equals(other: Any?): Boolean {
            if (other is UniqueKey) {
                return config == other.config && req == other.req
            }
            return false
        }

        override fun hashCode(): Int {
            return config.hashCode() * 31 + req.hashCode() * 31
        }
    }

    private val cacheMap = mutableMapOf<UniqueKey, MutableList<SingleEmitter<*>>>()

    override fun <DATA, KEY : CacheKey> getData(
            config: BaseConfig<DATA>,
            mobKey: MobConfigKey,
            req: KEY,
            net: (KEY) -> Single<MobConfigValue>): Single<DATA> {

        val key = UniqueKey(config, req)

        @Suppress("UNCHECKED_CAST")
        fun getEmitters(): MutableList<SingleEmitter<DATA>>? {
            return cacheMap[key] as? MutableList<SingleEmitter<DATA>>
        }

        /*
       如果已经有相同的请求 则直接排队等候请求的结果
        */
        getEmitters()?.let {
            return waitingForPreviousResult(it)
        }

        synchronized(cacheMap) {
            /*
            双重检查稳一波
             */
            getEmitters()?.let {
                return waitingForPreviousResult(it)
            }

            /*
            从网络取 取到之后通知那些在排队的
             */
            cacheMap[key] = mutableListOf()
            return Single.create({ e: SingleEmitter<DATA> ->
                repo.getData(config, mobKey, req, net)
                        .subscribe({ data ->
                            e.onSuccess(data)
                            synchronized(cacheMap) {
                                getEmitters()?.forEach { e -> e.onSuccess(data) }
                                cacheMap.remove(key)
                            }

                        }, { error ->
                            ConfigCenter.logger.e(error)
                            e.onError(error)
                            synchronized(cacheMap) {
                                getEmitters()?.forEach { e -> e.onError(error) }
                                cacheMap.remove(key)
                            }
                        })
            })

        } //synchronized
    }

    fun <DATA> waitingForPreviousResult(list: MutableList<SingleEmitter<DATA>>)
            : Single<DATA> {
        return Single.create({ emitter: SingleEmitter<DATA> ->
            ConfigCenter.logger.i("已有相同配置的相同请求, 进入队列等待 位置： ${list.size}")
            synchronized(cacheMap) {
                list.add(emitter)
            }
        })
    }
}

/**
 *
 * 让同个request但不同Config 不会多次请求网络
 */
private class CacheRemoteRepos(private val repo: Repo) : Repository {

    private val cacheMap = mutableMapOf<CacheKey, ConfigMap>()

    class ConfigMap {
        val map = mutableMapOf<BaseConfig<*>, SingleEmitter<*>>()

        val keySet
            get() = map.keys

        val valueSet
            get() = map.values

        @Suppress("UNCHECKED_CAST")
        fun <T> useEmitter(config: BaseConfig<T>, value: MobConfigValue) {
            (map.remove(config) as? SingleEmitter<T>)?.let {
                val data: T = ConfigCenter.pack(config, value)
                it.onSuccess(data)
                ConfigCenter.delivery(config, data, value)
            }
        }

        fun <T> setEmitter(config: BaseConfig<T>, emitter: SingleEmitter<T>) {
            map[config] = emitter
        }

        val size: Int
            get() = map.size
    }

    override fun <DATA, KEY : CacheKey> getData(
            config: BaseConfig<DATA>,
            mobKey: MobConfigKey,
            req: KEY,
            net: (KEY) -> Single<MobConfigValue>): Single<DATA> {

        /*
       如果已经有相同的请求 则直接排队等候请求的结果
        */
        cacheMap[req]?.let {
            return waitingForPreviousResult(config, it)
        }

        synchronized(cacheMap) {
            /*
            双重检查稳一波
             */
            cacheMap[req]?.let {
                return waitingForPreviousResult(config, it)

            }
            /*
            从网络取 取到之后通知那些在排队的
             */
            cacheMap[req] = ConfigMap()
            return Single.create({ e: SingleEmitter<DATA> ->
                ConfigCenter.logger.i("start network request for ${config.bssCode}")
                repo.getData(config, mobKey, req, net)
                        .subscribe({ value ->
                            ConfigCenter.logger.i("request success for $value")
                            ConfigCenter.logger.d("网络请求在 ${Thread.currentThread()}")

                            val d = ConfigCenter.pack(config, value)
                            e.onSuccess(d)
                            ConfigCenter.delivery(config, d, value)

                            var map: ConfigMap? = null

                            synchronized(cacheMap) {
                                map = cacheMap.remove(req)
                            }

                            map?.let { _map ->
                                for (_config in _map.keySet) {
                                    _map.useEmitter(_config, value)
                                }
                            }


                        }, { error ->

                            ConfigCenter.logger.e(error)
                            e.onError(error)
                            var map: ConfigMap? = null
                            synchronized(cacheMap) {
                                map = cacheMap.remove(req)
                            }

                            map?.let { _map ->
                                for (emitter in _map.valueSet) {
                                    emitter.onError(error)
                                }
                            }
                        })
            })

        } //synchronized
    }

    fun <DATA> waitingForPreviousResult(
            config: BaseConfig<DATA>,
            map: ConfigMap)
            : Single<DATA> {
        return Single.create({ emitter: SingleEmitter<DATA> ->
            ConfigCenter.logger.i("有不同配置但相同的请求，等待网络请求返回，队列位置： ${map.size}")
            synchronized(cacheMap) {
                map.setEmitter(config, emitter)
            }
        })
    }
}

/**
 * 定义何为相同的配置请求。
 *
 * 方法[equals] 为true的两个请求，应该有相同的[hashCode]值，而且会得到完全相同的响应
 */
interface CacheKey {
    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int
}