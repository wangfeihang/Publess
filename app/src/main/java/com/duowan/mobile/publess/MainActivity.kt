package com.duowan.mobile.publess

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.duowan.mobile.publess.other.SecondAppData
import com.example.configcenter.Publess
import io.reactivex.Flowable
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Publess.of(AppData::class.java)
                .concern()
                .subscribe { Log.i("zydata", "AppData: $it") }

        Publess.of(AppData::class.java)
                .pull()
                .subscribe { it -> Log.i("Publess", "AppData:$it") }

        Publess.of(SameConfigData::class.java)
                .pull()
                .subscribe { data -> Log.i("Publess", "SameData:$data") }

        Publess.of(SecondAppData::class.java)
                .pull()
                .subscribe { it -> Log.i("Publess", "SecondAppData:$it") }

        Publess.of(ExtendData::class.java)
                .pull()
                .subscribe { it -> Log.i("Publess", "ExtendData:$it") }

        Flowable.timer(2, TimeUnit.SECONDS)
                .flatMap { Publess.pull("mobby-base") }
                .subscribe {
                    Log.i("zydata", "config: $it")
                }

        Flowable.timer(4, TimeUnit.SECONDS)
                .subscribe {
                    Publess.updateAll()
                }
    }
}
