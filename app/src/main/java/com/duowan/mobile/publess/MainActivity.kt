package com.duowan.mobile.publess

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.example.configcenter.Publess
import io.reactivex.Observable
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Publess.of(AppData::class.java)
                .concern()
                .subscribe { s ->
                    Log.i("Publess", "concern appdata $s")
                }

        Publess.of(ExtendData::class.java)
                .concern()
                .subscribe { s ->
                    Log.i("Publess", "concern extenddata $s")
                }

        Publess.of(AppData::class.java)
                .update()

        Publess.of(AppData::class.java)
                .update()

        Publess.of(AppData::class.java)
                .update()

        Publess.of(AppData::class.java)
                .pull()
                .subscribe { s ->
                    Log.i("Publess", "pull $s")
                }

        Publess.of(AppData::class.java)
                .concern()
                .subscribe { s ->
                    Log.i("Publess", "concern2 appdata $s")
                }

        Publess.of(AppData::class.java)
                .pull()
                .subscribe { s ->
                    Log.i("Publess3", "$s")
                }

        Publess.of(AppData::class.java).update()

        Publess.of(ExtendData::class.java).update()

        Publess.of(ExtendData::class.java).update()

        Publess.of(AppData::class.java).update()

        Observable.timer(1, TimeUnit.SECONDS)
                .flatMap { Observable.fromArray(1, 2, 3, 4, 5) }
                .flatMapSingle { Publess.of(AppData::class.java).pull() }
                .subscribe { e ->
                    Log.i("Publess", "1s later $e")
                }
    }
}
