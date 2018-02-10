package com.example.configcenterannotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by 张宇 on 2018/2/5.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 */

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface BssConfig {
    String name();

    String bssCode();
}
