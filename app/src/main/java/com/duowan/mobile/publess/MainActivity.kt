package com.duowan.mobile.publess

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.example.configcenter.Publess
import com.example.configcenterannotation.BssConfig
import com.example.configcenterannotation.BssValue

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Publess.of(NewData::class.java).pull().subscribe { e ->
            Log.i("Publess", "$e")
        }
    }

    @BssConfig(name = "NewDataConfig", bssCode = "mobby-other")
    class NewData {
        @BssValue(property = "a")
        var a: String = "a"

        @BssValue(property = "d")
        var d: String = "d"

        @BssValue(property = "c")
        var c: String = "c"
    }
}
