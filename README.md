# Publess
-------------
客户端统一配置中心（测试环境：http://publesstest.yy.com/ ）的Android客户端支持

* 减少数据解析的模版代码，直接面向需要使用的数据对象
* 数据具有实时性和一致性，不用关心uid改变等原因导致当前数据过期
* 支持一个bssCode对应多份配置数据，只关心和解析自己业务要的数据
* 数据具有缓存，对于高频率或不同线程但其实是相同的数据请求，只会执行一次真正的网络请求。

# 配置
```groovy
kapt "com.yy.mobile:publess-compiler:${basesdk_version}"
compile "com.yy.mobile:publess-api:${basesdk_version}"
compile "com.yy.mobile:publess-annotation:${basesdk_version}"
```

```
//proguard
-keep class com.example.configcenter.**{*;}
-keep class * implements com.example.configcenter.PluginInitialization{*;}
-keep class * implements com.example.configcenter.DataParser{*;}
-keep public @interface com.example.configcenterannotation.BssConfig
-keep @com.example.configcenterannotation.BssConfig class * { *; }
```

# 使用
## 属性定义

- 简单的属性配置

如果要加一项业务配置，比如在 **手Y基础配置** （BssCode： **mobyy-base** ）

![配置中心首页][1]
配置了如下属性：

| 键 | 默认值 |
| :----: | :----: |
| test | 我就一句话 |

那么在我们的代码里面，我们需要定义一个自己业务的JavaBean类，作为从配置中心取得的数据。然后在此类上面注解上 **@BssConfig(name="配置的类名",bssCode="配置中心定义的业务号")** 。注意配置的类名在当前包下不能有已存在的同名类，否则生成类会同名冲突。bssCode必须要跟网页版配置中心上定义的相同。然后定义一个业务所需要的属性，并加上注解 **@BssValue(property = "配置中心定义的键")** ，Publess就会在该属性上附上对应的值：

```java
@BssConfig(name = "AppBasicsConfig", bssCode = "mobyy-base")
public class AppBasicsData {

    @BssValue(property = "test")
    public String value = ""; //assertEquals("我就一句话",value)
}
```
- 简单的Json对象

而在现有的配置中心里面，大部分的配置都不是简单的一个字符串，而是一个Json对象

| 键 | 默认值 |
| :----: | :----: |
| PaoSaoUseRN | {"switch":1} |
| doubleValue | {"value":"2355234.123"} |

那么我们的代码的注解就会多一个**key**属性：

```java
@BssConfig(name = "AppBasicsConfig", bssCode = "mobyy-base")
public class AppBasicsData {

    @BssValue(property = "PaoSaoUseRN", key = "switch")
    public int paoSaoRn = 0; //assertEquals(1, paoSaoRn)

    @BssValue(property = "doubleValue", key = "value")
    public double doubleValue = ""; //doubleValue = Double.valueOf("2355234.123"）
}
```
- 复杂的Json对象或Json数组

| 键 | 默认值 |
| :----: |:----: |
| playWithMc | ["青铜","白银","黄金","铂金","钻石"] |
| game_pk_config | {"android":{"webviewType":0,"urlLoadTimeout":30,"gameLoadTimeout":30},"ios":{"webpk_game_resource":"http://empfs.bs2dl.yy.com","record_fps":15,"suspend_fps":1}} |

```java
    @BssValue(property = "playWithMc")
    public List<String> playWithMc = Collections.emptyList();

    @BssValue(property = "game_pk_config", key = "android")
    public GamePkConfig pkConfig = new GamePkConfig();

    public class GamePkConfig {
        @SerializedName("webviewType")
        public int webviewType;
        @SerializedName("urlLoadTimeout")
        public int urlLoadTimeout;
        @SerializedName("gameLoadTimeout")
        public int gameLoadTimeout;
    }
```
注意：被解析的类需要提供一个无参的构造方法，而且成员变量需要用 **@SerializedName** 注解防止混淆，详见Gson。如果是内部类的话，还需要 ` public static ` 的修饰符。

- 私有的成员变量

如果数据类里面的成员变量被声明为 `private` ，那么还需要提供 `public` 的 **getter/setter** 方法，如下：
```java
    @BssValue(property = "unicom_wspx", key = "switch")
    private boolean unicomSwitch = false;

    public boolean isUnicomSwitch() {
        return unicomSwitch;
    }

    public void setUnicomSwitch(int IntegerValue) {
        this.unicomSwitch = IntegerValue == 1;
    }
```

- 自定义解析数据的方法

如果配置的数据并不是Java基础类型、Json类型，或者是希望自己去完成这样的解析工作，可以注解在自定义的方法上面（但必须是外界可访问且只含有一个参数）：
```java
    @BssValue(property = "yourProperty",key = "optional")
    public void parseMyCustomObject(String configValue){
        //to parse your data
    }
```

- kotlin支持

在kotlin中使用时，成员变量需要是var。如果是使用 `data class` ，由于要提供无参构造函数，所以每个变量都需要有默认值。
```kotlin
@BssConfig(name = "KotlinConfig", bssCode = "mobby-test")
data class KotlinData(
        @BssValue(property = "key0", key = "option1")
        var str: String = "",
        @BssValue(property = "key1")
        var complex: Complex = Complex(),
        @BssValue(property = "key2")
        var stringList: MutableList<String> = mutableListOf()
) {
    var customData: Int = 0
        private set (value) {
            field = value
        }

    @BssValue(property = "key3", key = "option2")
    @Throws(JSONException::class)
    fun parseCustom(json: String) {
        customData = JSONObject(json).optInt("custom", 0)
    }
}

data class Complex(
        @SerializedName("i") var i: Int = 0,
        @SerializedName("j") var j: Int = 0
)
```

-------------

## 访问配置
- 获取配置
```java
//Publess.of(dataCls: java.lang.Class<DATA>): com.example.configcenter.BaseConfig<D>

BaseConfig<AppBasicsData> config = Publess.of(AppBasicsData.class);
```
- 请求访问一次配置数据（并不代表网络请求）

```java
//fun BaseConfig<D>.pull(): io.reactivex.Single<D>

Publess.of(AppBasicsData.class).pull().subscribe(new Consumer<AppBasicsData>() {
    @Override
    public void accept(AppBasicsData data) {
        //TODO
    }
});

//PublessAppBasicsConfig是根据注解BssConfig name生成的类
//不推荐
Publess.get(PublessAppBasicsConfig.class).pull().subscribe(new Consumer<AppBasicsData>() {
    @Override
    public void accept(AppBasicsData data) {
        //TODO
    }
});
```
注意：在kotlin里面是没办法直接把生成的类写在代码里面的，所以只推荐用Publess.of这种用法。

**顺便说一句：YYStore这套框架的Action是用apt生成，在kotlin里面根本没法用，这种设计凉透了**

- 关联一个配置，监听配置每一次变化

```java
//fun BaseConfig<D>.concern(): io.reactivex.Flowable<D>

Publess.of(AppBasicsData.class).concern().subscribe(new Consumer<AppBasicsData>() {
    @Override
    public void accept(AppBasicsData appBasicsData) throws Exception {
        //TODO
    }
});
```
`concern`方法建议与`RxLifecycle`合用，否则一定要记得`dispose()`防止内存泄漏

- 直接使用配置数据（不推荐）

```java
AppBasicsData data = Publess.of(AppBasicsData.class).getData();
```
这个仅仅是用来兼容祖传代码的用法。无法保证拿到的数据是否是最新的、有效的。

-------------
#插件化

如果条件允许的话，可以在插件入口加上 **@BssInit** 和 **Publess.initPlugin(this);** 
```java
@BssInit
public enum PluginEntryPoint implements IPluginEntryPoint, IHostApiFactory {
    //...
    
    @Override
    public void initialize(IPluginManager manager) {
        //...
        Publess.initPlugin(this);
        //...
    }
}
```

后续的优化会通过这个入口初始化整个插件的配置，略微提升使用时的性能。不加也不影响使用。
