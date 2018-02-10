package com.example.configcenter

import com.google.gson.JsonParseException
import io.reactivex.Single
import io.reactivex.SingleEmitter
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
    fun getData(req: MobConfigKey): Single<MobConfigValue>
}

/**
 * # 支持缓存的数据仓库
 */
internal interface CachingRepository : Repository {
    fun getCacheKey(req: MobConfigKey): CacheKey
}

internal class ConfigRepository : Repository {
    private val remote by lazy { CacheRemoteRepos(ConfigCenter.network) }
    private val local = LocalRepository()

    override fun getData(req: MobConfigKey): Single<MobConfigValue> {
        return remote.getData(req)
                .onErrorResumeNext({ throwable ->
                    // 如果是数据解析时产生的错误，希望抛出去在开发阶段解决
                    if (throwable is NumberFormatException || throwable is JsonParseException) {
                        throw throwable
                    }
                    local.getData(req)
                })
    }
}

/**
 * 从本地获取数据，不支持[CacheKey]，
 * 也就是只要[MobConfigKey]相同就当作命中，不会判断其他信息。
 * 只作为一种后补手段，还是以网络获取为主
 */
private class LocalRepository : Repository {
    override fun getData(req: MobConfigKey): Single<MobConfigValue> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

/**
 * 从网络获取数据
 *
 * 对于同一种请求，只有第一个请求会去执行，后面的请求会直接等待第一个请求的结果。
 * 同一种请求的定义由[CachingRepository.getCacheKey]决定
 * todo 创建Single换优雅一点的写法
 */
private class CacheRemoteRepos<T : CacheKey>(
        val net: Network<T>) : Repository {

    private val cacheMap = mutableMapOf<CacheKey, MutableList<SingleEmitter<MobConfigValue>>>()

    override fun getData(req: MobConfigKey): Single<MobConfigValue> {

        val mark = net.extractKey(req)

        /*
        如果已经有相同的请求 则直接排队等候请求的结果
         */
        cacheMap[mark]?.let {
            return waitingForPreviousResult(it)
        }

        synchronized(cacheMap) {
            /*
            双重检查稳一波
             */
            cacheMap[mark]?.let {
                return waitingForPreviousResult(it)

            } ?: run {
                /*
                从网络取 取到之后通知那些在排队的
                 */
                cacheMap[mark] = mutableListOf()
                return requestAndNotify(mark)
            }
        } //synchronized
    }

    fun waitingForPreviousResult(list: MutableList<SingleEmitter<MobConfigValue>>)
            : Single<MobConfigValue> {
        return Single.create({ emitter: SingleEmitter<MobConfigValue> ->
            synchronized(cacheMap) {
                list.add(emitter)
            }
        })
    }

    fun requestAndNotify(req: T): Single<MobConfigValue> {
        return Single.create({ e: SingleEmitter<MobConfigValue> ->
            net.performNetwork(req)
                    .subscribeOn(Schedulers.io())
                    .subscribe({
                        if (!e.isDisposed) e.onSuccess(it)

                        synchronized(cacheMap) {
                            cacheMap[req]
                                    ?.filterNot { it.isDisposed }
                                    ?.forEach { e -> e.onSuccess(it) }
                            cacheMap.remove(req)
                        }
                    }, {
                        if (!e.isDisposed) e.onError(it)

                        synchronized(cacheMap) {
                            cacheMap[req]
                                    ?.filterNot { it.isDisposed }
                                    ?.forEach { e -> e.onError(it) }
                            cacheMap.remove(req)
                        }
                    })
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