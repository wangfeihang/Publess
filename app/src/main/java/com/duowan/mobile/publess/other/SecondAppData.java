package com.duowan.mobile.publess.other;

import com.example.configcenterannotation.BssConfig;
import com.example.configcenterannotation.BssValue;

/**
 * Created by 张宇 on 2018/2/14.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
@BssConfig(name = "SecondConfig", bssCode = "mobby-base")
public class SecondAppData {

    @BssValue(property = "a")
    public String a;
    @BssValue(property = "s")
    public long s;
    @BssValue(property = "b")
    private boolean b;

    public boolean isB() {
        return b;
    }

    public void setB(int b) {
        this.b = b == 1;
    }

    @Override
    public String toString() {
        return "AppData{" +
                "a='" + a + '\'' +
                ", b=" + b +
                ", s=" + s +
                '}';
    }
}
