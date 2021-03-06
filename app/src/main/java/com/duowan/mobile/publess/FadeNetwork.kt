package com.duowan.mobile.publess

import android.util.Log
import com.example.configcenter.*
import io.reactivex.Single
import java.util.concurrent.TimeUnit

/**
 * Created by 张宇 on 2018/2/10.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
class FadeNetwork : Network<CustomRequest> {
    override fun performNetwork(req: CustomRequest): Single<MobConfigValue> {
        return when (req.bssCode) {
            "mobby-base" -> Single.just(MobConfigValue("mobby-base", 0, mapOf(
                    "a" to "i am a",
                    "b" to "1",
                    "s" to "12312",
                    "efg" to "i am efg",
                    "list" to """["a","b","c","d"]"""
            )))
            else -> Single.just(MobConfigValue("mobby-extend", 0, mapOf(
                    "asd" to "i am asd",
                    "user" to """{"uid":"123456","name":"i am a name"}"""
            )))
        }
                .delay(1, TimeUnit.SECONDS)
    }

    override fun extractKey(key: MobConfigKey): CustomRequest {
        return CustomRequest(key.bssCode, 0)
    }
}

data class CustomRequest(val bssCode: String, private val uid: Long) : CacheKey {
    override fun equals(other: Any?): Boolean {
        if (other is CustomRequest) {
            return bssCode == other.bssCode && uid == other.uid
        }
        return false
    }

    override fun hashCode(): Int = bssCode.hashCode() * 31 + uid.hashCode()
}

class PublessLog : ILog {
    override fun d(info: String) {
        Log.i(TAG, info)
    }

    override fun e(error: Throwable) {
        Log.e(TAG, "", error)
    }

    override fun i(info: String) {
        Log.i(TAG, info)
    }
}