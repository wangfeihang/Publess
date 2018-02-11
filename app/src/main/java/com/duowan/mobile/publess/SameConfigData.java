package com.duowan.mobile.publess;

import com.example.configcenterannotation.BssConfig;
import com.example.configcenterannotation.BssValue;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by 张宇 on 2018/2/11.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */
@BssConfig(name = "AppBaseConfig2", bssCode = "mobby-base")
public class SameConfigData {

    @BssValue(property = "a")
    public String a;

    @BssValue(property = "efg")
    public String efg = "";

    @BssValue(property = "list")
    public List<String> list = new ArrayList<>();

    @Override
    public String toString() {
        return "SameConfigData{" +
                "a='" + a + '\'' +
                ", efg='" + efg + '\'' +
                ", list=" + list +
                '}';
    }
}
