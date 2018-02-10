package com.duowan.mobile.publess;

import com.example.configcenterannotation.BssConfig;
import com.example.configcenterannotation.BssValue;

/**
 * Created by 张宇 on 2018/2/10.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
@SuppressWarnings("SpellCheckingInspection")
@BssConfig(name = "AppBaseConfig", bssCode = "mobby-base")
public class AppData {
    @BssValue(property = "a")
    public String a;

    @BssValue(property = "b")
    private boolean b;

    @BssValue(property = "s")
    public long s;

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
