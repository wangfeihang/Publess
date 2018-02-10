package com.example.configcenter

/**
 * Created by 张宇 on 2018/2/8.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
data class MobConfigKey(
        val bssCode: String, //业务代号,用于标识业务,全局唯一
        val bssVersion: Long, //业务最新版本号,默认值设置为0
        val uid: Long
)

data class MobConfigValue(
        val bssCode: String,
        val bssVersion: Long,
        val config: Map<String, String>
)