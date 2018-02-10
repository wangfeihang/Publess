package com.duowan.mobile.publess

import android.app.Application
import com.example.configcenter.Publess
import com.example.configcenterannotation.BssInit

/**
 * Created by 张宇 on 2018/2/10.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
@BssInit
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Publess.initPlugin(this)
        Publess.enableLog(PublessLog())
        Publess.performNetwork(FadeNetwork())
    }
}

const val TAG = "Publess"
