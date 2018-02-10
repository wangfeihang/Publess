package com.example.configcenter

import io.reactivex.Single
import io.reactivex.SingleEmitter

/**
 * Created by 张宇 on 2018/2/3.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */

/**
 * # 数据仓库
 */
internal interface Repository<in REQ, RESP> {
    fun getData(req: REQ): Single<RESP>
}

internal class ConfigRepository : Repository<MobConfigKey, MobConfigValue> {
    private val network by lazy { ConfigCenter.network }
    private val remote by lazy { CacheRepos(RemoteRepos(network)) }
    private val local = LocalRepository()

    override fun getData(req: MobConfigKey): Single<MobConfigValue> {
        val key = network.extractKey(req)

        return remote.getData(key).onErrorResumeNext(local.getData(key))
    }
}

private class RemoteRepos(private val network: Network) : Repository<CacheKey, MobConfigValue> {
    override fun getData(req: CacheKey): Single<MobConfigValue> {
        return network.performNetwork(req)
    }
}

private class LocalRepository : Repository<CacheKey, MobConfigValue> {
    override fun getData(req: CacheKey): Single<MobConfigValue> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

/**
 * 装饰器模式
 * --------
 * 对于同一种请求，只有第一个请求会去执行，后面的请求会直接等待第一个请求的结果。
 * 同一种请求的定义由[cacheKey]决定
 */
private class CacheRepos<in T, R>(
        val repo: Repository<T, R>)
    : Repository<T, R> {

    private val cacheMap = mutableMapOf<T, MutableList<SingleEmitter<R>>>()

    override fun getData(req: T): Single<R> {

        val mark = req

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
                从仓库取 取到之后通知那些在排队的
                 */
                cacheMap[mark] = mutableListOf()
                return repo.getData(req)
                        .doOnSuccess {
                            synchronized(cacheMap) {
                                cacheMap[mark]!!
                                        .filterNot { it.isDisposed }
                                        .forEach { e -> e.onSuccess(it) }
                                cacheMap.remove(mark)
                            }
                        }
                        .doOnError {
                            synchronized(cacheMap) {
                                cacheMap[mark]!!
                                        .filterNot { it.isDisposed }
                                        .forEach { e -> e.onError(it) }
                                cacheMap.remove(mark)
                            }
                        }
            }
        }
    }

    fun waitingForPreviousResult(list: MutableList<SingleEmitter<R>>)
            : Single<R> {
        return Single.create({ emitter: SingleEmitter<R> ->
            synchronized(cacheMap) {
                list.add(emitter)
            }
        })
    }
}

/**
 * ## 定义何为相同的配置请求。
 * 方法[CacheKey.equals] 为true的两个配置请求，应该获取同一份配置
 */
interface CacheKey {
    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int
}