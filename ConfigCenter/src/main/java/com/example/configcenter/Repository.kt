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

    private val remote: Repository = MemoryRepo(BlockSameConfigRepo(BlockSameRequestRepo(RemoteRepos())))
    private val local: Repository = LocalRepo()

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
 * 一定时间内完全相同的请求直接取内存数据
 */
private class MemoryRepo(private val repo: Repository) : Repository {

    val cacheTime = 30 * 60 * 1000L

    /**
     * 读写都控制在主线程
     */
    private val lastTimeData = mutableMapOf<BaseConfig<*>, Pair<CacheKey, Long>>()

    override fun <DATA, KEY : CacheKey> getData(
            config: BaseConfig<DATA>,
            mobKey: MobConfigKey,
            req: KEY,
            net: Net<KEY>): Single<DATA> {

        return Single.create({ emitter: SingleEmitter<DATA> ->
            val pair = lastTimeData[config]
            if (pair != null) {
                val (key, time) = pair
                if (key == req && System.currentTimeMillis() - time < cacheTime) {
                    ConfigCenter.logger.i("内存命中 直接返回$config 的data on ${Thread.currentThread()}")
                    emitter.onSuccess(config.data)
                    return@create
                }
            }
            emitter.onError(IllegalStateException("no memory cache"))
        })
                .subscribeOn(AndroidSchedulers.mainThread())
                .onErrorResumeNext {
                    repo.getData(config, mobKey, req, net)
                            .observeOn(AndroidSchedulers.mainThread())
                            .doOnSuccess {
                                ConfigCenter.logger.d("更新内存$config （${config.bssCode}）的数据")
                                lastTimeData[config] = Pair(req, System.currentTimeMillis())
                            }
                }
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
        return Single.just(config.data) //有空再写
    }
}

/**
 * 让同个Config同个Request 不会多次请求
 */
private class BlockSameConfigRepo(private val repo: Repository) : Repository {

    private data class UniqueKey(private val config: BaseConfig<*>, private val req: CacheKey)

    /**
     * 所有对该map对操作都控制在主线程，不需要上锁
     */
    private val cacheMap = mutableMapOf<UniqueKey, MutableList<SingleEmitter<*>>>()

    override fun <DATA, KEY : CacheKey> getData(
            config: BaseConfig<DATA>,
            mobKey: MobConfigKey,
            req: KEY,
            net: Net<KEY>): Single<DATA> {
        return Single.create({ emitter: SingleEmitter<DATA> ->

            //以下代码在主线程运行

            val key = UniqueKey(config, req)

            @Suppress("UNCHECKED_CAST")
            fun getEmitters(): MutableList<SingleEmitter<DATA>>? {
                return cacheMap[key] as? MutableList<SingleEmitter<DATA>>
            }

            //如果已经有相同的请求 则直接排队等候请求的结果
            getEmitters()?.let {
                ConfigCenter.logger.i("已有相同配置的相同请求, 进入队列等待 位置： ${it.size}")
                it.add(emitter)
                return@create
            }

            //从网络取 取到之后通知那些在排队的
            cacheMap[key] = mutableListOf()
            repo.getData(config, mobKey, req, net)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ data ->
                        emitter.onSuccess(data)
                        getEmitters()?.forEach { e -> e.onSuccess(data) }
                        cacheMap.remove(key)

                    }, { error ->
                        ConfigCenter.logger.e(error)
                        emitter.onError(error)
                        getEmitters()?.forEach { e -> e.onError(error) }
                        cacheMap.remove(key)
                    })
        }).subscribeOn(AndroidSchedulers.mainThread()) //Single.create
    }
}

/**
 * 让同个request但不同Config 不会同时多次请求网络
 */
private class BlockSameRequestRepo(private val repo: RemoteRepos) : Repository {

    private val waitingQueue: MutableMap<CacheKey, ConfigMap> = mutableMapOf()

    private class ConfigMap(private val map: MutableMap<BaseConfig<*>, SingleEmitter<*>>)
        : Map<BaseConfig<*>, SingleEmitter<*>> by map {

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
    }

    override fun <DATA, KEY : CacheKey> getData(
            config: BaseConfig<DATA>,
            mobKey: MobConfigKey,
            req: KEY,
            net: (KEY) -> Single<MobConfigValue>): Single<DATA> {

        //以下代码在主线程
        //如果已经有相同的请求 则直接排队等候请求的结果
        waitingQueue[req]?.let {
            return waitingForPreviousResult(config, it)
        }

        synchronized(waitingQueue) {
            //双重检查稳一波
            waitingQueue[req]?.let {
                return waitingForPreviousResult(config, it)
            }

            //从网络取 取到之后通知那些在排队的
            waitingQueue[req] = ConfigMap(mutableMapOf())
            return Single.create({ e: SingleEmitter<DATA> ->
                repo.getData(config, mobKey, req, net)
                        //以下代码在IO线程
                        .subscribe({ value ->
                            ConfigCenter.logger.i("网络请求成功 $value")

                            val d = ConfigCenter.pack(config, value)
                            e.onSuccess(d)
                            ConfigCenter.delivery(config, d, value)

                            var map: ConfigMap? = null

                            synchronized(waitingQueue) {
                                map = waitingQueue.remove(req)
                            }

                            map?.let {
                                for (_config in it.keys) {
                                    it.useEmitter(_config, value)
                                }
                            }

                        }, { error ->
                            ConfigCenter.logger.e(error)
                            e.onError(error)
                            var map: ConfigMap? = null
                            synchronized(waitingQueue) {
                                map = waitingQueue.remove(req)
                            }

                            map?.let {
                                for (emitter in it.values) {
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
            synchronized(waitingQueue) {
                map.setEmitter(config, emitter)
            }
        })
    }
}

/**
 * 从网络读取，相同请求在2分钟内会读缓存
 */
private class RemoteRepos {
    private val cache = LruCache<CacheKey, Pair<MobConfigValue, Long>>(6)
    private val limit = 10 * 60 * 1000L

    fun <DATA, KEY : CacheKey> getData(
            config: BaseConfig<DATA>,
            mobKey: MobConfigKey,
            req: KEY,
            net: (KEY) -> Single<MobConfigValue>): Single<MobConfigValue> {

        //以下代码在主线程
        var pair: Pair<MobConfigValue, Long>? = null
        synchronized(cache) {
            pair = cache[req]
        }
        pair?.let {
            val (value, time) = it
            if (System.currentTimeMillis() - time < limit) {
                ConfigCenter.logger.i("缓存有该网络请求的数据 直接返回 ${config.bssCode}")
                return Single.just(value)
            }
        }
        ConfigCenter.logger.i("开始网络请求 ${config.bssCode}")
        return net(req)
                //以下代码在IO线程
                .doOnSuccess {
                    synchronized(cache) {
                        cache.put(req, Pair(it, System.currentTimeMillis()))
                    }
                }
                .subscribeOn(Schedulers.io())
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