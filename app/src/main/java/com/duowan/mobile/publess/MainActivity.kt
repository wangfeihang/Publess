package com.duowan.mobile.publess

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.example.configcenter.Publess

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Publess.of(AppData::class.java)
                .pull()
                .subscribe { s ->
                    Log.i("Publess", "$s")
                }

        Publess.of(ExtendData::class.java)
                .pull()
                .subscribe { s ->
                    Log.i("Publess1_extend", "$s")
                }

        Publess.of(ExtendData::class.java)
                .pull()
                .subscribe { s ->
                    Log.i("Publess2_extend", "$s")
                }

        Publess.of(AppData::class.java)
                .pull()
                .subscribe { s ->
                    Log.i("Publess1", "$s")
                }

        Publess.of(AppData::class.java)
                .pull()
                .subscribe { s ->
                    Log.i("Publess2", "$s")
                }

        Publess.of(AppData::class.java)
                .pull()
                .subscribe { s ->
                    Log.i("Publess3", "$s")
                }

        Publess.of(AppData::class.java)
                .pull()
                .subscribe { s ->
                    Log.i("Publess4", "$s")
                }

        Publess.of(ExtendData::class.java).pull()
                .subscribe { s ->
                    Log.i("Publess3extend", "$s")
                }

        Publess.of(AppData::class.java)
                .pull()
                .subscribe { s ->
                    Log.i("Publess5", "$s")
                }
    }
}
