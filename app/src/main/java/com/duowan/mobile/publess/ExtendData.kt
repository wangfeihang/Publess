package com.duowan.mobile.publess

import com.example.configcenterannotation.BssConfig
import com.example.configcenterannotation.BssValue
import com.google.gson.annotations.SerializedName

/**
 * Created by 张宇 on 2018/2/11.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
@BssConfig(name = "ExtendConfig", bssCode = "mobby-extend")
data class ExtendData(
        @BssValue(property = "asd")
        var asd: String = "",
        @BssValue(property = "user")
        var user: User = User()
)

data class User(
        @SerializedName("uid")
        var uid: Long = 0,
        @SerializedName("name")
        var name: String = ""
)