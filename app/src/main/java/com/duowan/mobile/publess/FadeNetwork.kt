package com.duowan.mobile.publess

import android.util.Log
import com.example.configcenter.*
import io.reactivex.Single

/**
 * Created by 张宇 on 2018/2/10.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
class FadeNetwork : Network<CustomRequest> {
    override fun performNetwork(req: CustomRequest): Single<MobConfigValue> {
        return Single.just(MobConfigValue("mobby-base", 0, mapOf(
                "a" to "i am a",
                "b" to "1",
                "s" to "12312"
        )))
    }

    override fun extractKey(key: MobConfigKey): CustomRequest {
        return CustomRequest(key.bssCode, 0L)
    }
}

data class CustomRequest(private val bssCode: String, private val uid: Long) : CacheKey {
    override fun equals(other: Any?): Boolean {
        if (other is CustomRequest) {
            return bssCode == other.bssCode && uid == other.uid
        }
        return false
    }

    override fun hashCode(): Int = bssCode.hashCode() * 31 + uid.hashCode()
}

class PublessLog : ILog {
    override fun i(info: String) {
        Log.i(TAG, info)
    }

    override fun e(error: String) {
        Log.e(TAG, error)
    }
}