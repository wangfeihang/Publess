package com.duowan.mobile.publess

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.example.configcenter.Publess
import io.reactivex.Single
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Publess.of(AppData::class.java)
                .concern()
                .subscribe { Log.i("Publess", "AppData:$it") }

        Publess.of(SameConfigData::class.java)
                .pull()
                .subscribe { data -> Log.i("Publess", "SameData:$data") }

        Publess.of(AppData::class.java)
                .pull()
                .subscribe { it -> Log.i("Publess", "AppData:$it") }

        Single.timer(1, TimeUnit.SECONDS)
                .flatMap {
                    Publess.of(AppData::class.java).pull()
                }
                .subscribe { it -> Log.i("Publess", "AppData:$it") }

        Single.timer(1, TimeUnit.SECONDS)
                .flatMap {
                    Publess.of(AppData::class.java).pull()
                }
                .subscribe { it -> Log.i("Publess", "AppData:$it") }

        Single.timer(1, TimeUnit.SECONDS)
                .flatMap {
                    Publess.of(ExtendData::class.java).pull()
                }
                .subscribe { it -> Log.i("Publess", "AppData:$it") }

        Single.timer(2, TimeUnit.SECONDS)
                .flatMap {
                    Publess.of(AppData::class.java).pull()
                }
                .subscribe { it -> Log.i("Publess", "AppData:$it") }

        Single.timer(2, TimeUnit.SECONDS)
                .flatMap {
                    Publess.of(SameConfigData::class.java).pull()
                }
                .subscribe { it -> Log.i("Publess", "AppData:$it") }

        Single.timer(3, TimeUnit.SECONDS)
                .flatMap {
                    Publess.of(ExtendData::class.java).pull()
                }
                .subscribe { it -> Log.i("Publess", "AppData:$it") }
    }
}
