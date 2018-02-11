package com.example.configcenter

import com.example.configcenter.MemoryRepo.Companion.cacheTime
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

    private val remote = CacheRepos(MemoryRepo(RemoteRepos()))
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
 *
 * if there are a lot of same requests within specified time [cacheTime],
 * it will directly take the memory result
 */
private class MemoryRepo(private val repo: Repository) : Repository {

    companion object {
        const val cacheTime = 2 * 60 * 1000L
    }

    private val lastTime = mutableMapOf<String, Pair<CacheKey, Long>>()

    override fun <DATA, KEY : CacheKey> getData(
            config: BaseConfig<DATA>,
            mobKey: MobConfigKey,
            req: KEY,
            net: Net<KEY>): Single<DATA> {

        val pair = lastTime[config.bssCode]
        if (pair != null) {
            val (key, time) = pair
            if (key == req && System.currentTimeMillis() - time < cacheTime) {
                ConfigCenter.logger.i("hit memory cache of ${config.bssCode}")
                return Single.just(config.data)
            }
        }

        return repo.getData(config, mobKey, req, net)
                .doOnSuccess {
                    lastTime[config.bssCode] = Pair(req, System.currentTimeMillis())
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
        TODO("not implemented") //有空再写
    }
}

/**
 * read from network
 */
private class RemoteRepos : Repository {
    override fun <DATA, KEY : CacheKey> getData(
            config: BaseConfig<DATA>,
            mobKey: MobConfigKey,
            req: KEY,
            net: (KEY) -> Single<MobConfigValue>): Single<DATA> {
        ConfigCenter.logger.i("start network request for ${config.bssCode}")
        return net(req)
                .map { Pair(ConfigCenter.pack(config, it), it) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSuccess { (data, value) ->
                    ConfigCenter.delivery(config, data, value)
                }
                .map { (data, _) -> data }
    }
}

/**
 * Decorator Pattern
 *
 * if there are a lot of the same requests at the same time, only the first will be actually executed,
 * and the others will wait for the unique response
 */
private class CacheRepos(private val repo: Repository) : Repository {

    private val cacheMap = mutableMapOf<CacheKey, MutableList<out SingleEmitter<*>>>()


    override fun <DATA, KEY : CacheKey> getData(
            config: BaseConfig<DATA>,
            mobKey: MobConfigKey,
            req: KEY,
            net: (KEY) -> Single<MobConfigValue>): Single<DATA> {

        @Suppress("UNCHECKED_CAST")
        fun getEmitters(key: CacheKey): MutableList<SingleEmitter<DATA>>? {
            return cacheMap[key] as? MutableList<SingleEmitter<DATA>>
        }

        /*
       如果已经有相同的请求 则直接排队等候请求的结果
        */
        getEmitters(req)?.let {
            return waitingForPreviousResult(it)
        }

        synchronized(cacheMap) {
            /*
            双重检查稳一波
             */
            getEmitters(req)?.let {
                return waitingForPreviousResult(it)

            } ?: run {
                /*
                从网络取 取到之后通知那些在排队的
                 */
                cacheMap[req] = mutableListOf()
                return Single.create({ e: SingleEmitter<DATA> ->
                    repo.getData(config, mobKey, req, net)
                            .subscribe({ data ->
                                ConfigCenter.logger.i("request success for $data")
                                ConfigCenter.logger.d("data response on ${Thread.currentThread()}")
                                e.onSuccess(data)
                                synchronized(cacheMap) {
                                    getEmitters(req)?.forEach { e -> e.onSuccess(data) }
                                    cacheMap.remove(req)
                                }

                            }, { error ->
                                ConfigCenter.logger.e(error)
                                e.onError(error)
                                synchronized(cacheMap) {
                                    getEmitters(req)?.forEach { e -> e.onError(error) }
                                    cacheMap.remove(req)
                                }
                            })
                })
            }
        } //synchronized
    }

    fun <DATA> waitingForPreviousResult(list: MutableList<SingleEmitter<DATA>>)
            : Single<DATA> {
        return Single.create({ emitter: SingleEmitter<DATA> ->
            synchronized(cacheMap) {
                list.add(emitter)
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