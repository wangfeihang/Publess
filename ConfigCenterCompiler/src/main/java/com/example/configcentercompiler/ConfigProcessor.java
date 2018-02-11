package com.example.configcentercompiler;

import com.example.configcenterannotation.BssConfig;
import com.example.configcenterannotation.BssInit;
import com.example.configcenterannotation.BssValue;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

@AutoService(Processor.class)
public class ConfigProcessor extends AbstractProcessor {

    private Logger log;
    private static final String packageRoot = "com.example.configcenter";
    private static final ClassName DataParser_ClsName =
            ClassName.get(packageRoot, "DataParser");
    private static final ClassName BaseConfig_ClsName =
            ClassName.get(packageRoot, "BaseConfig");
    private static final ClassName PluginInitialization_ClsName =
            ClassName.get(packageRoot, "PluginInitialization");
    private static final ClassName TypeToken_ClsName =
            ClassName.get("com.google.gson.reflect", "TypeToken");
    private static final ClassName Publess_ClsName =
            ClassName.get("com.example.configcenter", "Publess");
    private static final ClassName JSONException_ClsName = ClassName.get("org.json",
            "JSONException");
    private static final ClassName NumberFormatException_ClsName =
            ClassName.get(NumberFormatException.class);
    private static final ClassName JSONObject_ClsName = ClassName.get("org.json",
            "JSONObject");
    private static final ClassName Map_ClsName = ClassName.get(Map.class);
    private static final ClassName String_clsName = ClassName.get(String.class);
    private static final ClassName Gson_clsName = ClassName.get("com.google.gson",
            "Gson");

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add(BssConfig.class.getCanonicalName());
        annotations.add(BssValue.class.getCanonicalName());
        annotations.add(BssInit.class.getCanonicalName());
        return annotations;
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment env) {
        log = new Logger(processingEnv.getMessager());
        final Map<ClassName, String> mapDataToConfig = new HashMap<>();

        try {
            checkClassAnnotation(env);

            Set<? extends Element> elements = env.getElementsAnnotatedWith(BssConfig.class);
            for (Element cls : elements) {
                if (!(cls instanceof TypeElement)) {
                    throw new IllegalStateException("element '" + cls + "' with annotation @BssConfig is not a class!");
                }
                if (!cls.getModifiers().contains(Modifier.PUBLIC)) {
                    throw new IllegalStateException("class '" + cls + "' must be public!");
                }
                if (cls.getModifiers().contains(Modifier.ABSTRACT)) {
                    throw new IllegalStateException("class '" + cls + "' can not be abstract!");
                }
                final BssConfig bssConfigAnno = cls.getAnnotation(BssConfig.class);
                final String bssCode = bssConfigAnno.bssCode();
                final String bssName = bssConfigAnno.name();
                final String packageName = getPackageName(cls);
                final ClassName dataClassName = ClassName.get((TypeElement) cls);
                mapDataToConfig.put(dataClassName, bssName);

                genDataParserClass((TypeElement) cls, dataClassName, bssName, packageName);

                genConfigClass(dataClassName, bssName, bssCode, packageName);

                genDataInitClass(dataClassName, bssName);
            } //每个配置类

            Set<? extends Element> pluginElements = env.getElementsAnnotatedWith(BssInit.class);
            for (Element pluginEle : pluginElements) {
                if (pluginEle instanceof TypeElement) {
                    genInitClass((TypeElement) pluginEle, mapDataToConfig);
                } else {
                    throw new IllegalStateException("@BssInit should act on a class");
                }
            }

        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return false;
    }

    private void genDataParserClass(TypeElement cls, ClassName dataClassName, String bssName, String packageName) {
        List<? extends Element> members = cls.getEnclosedElements();

        List<MethodSpec> methodSpecs = Collections.singletonList(
                generateParseMethod(dataClassName, members));
        TypeSpec classSpecs = generateDataParserClass(bssName, dataClassName, methodSpecs);
        writeFile(classSpecs, packageName);
    }

    @SuppressWarnings("UnnecessaryContinue")
    private MethodSpec generateParseMethod(TypeName dataCls, List<? extends Element> members) {

        MethodSpec.Builder methodSpec = MethodSpec.methodBuilder("parse")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(dataCls)
                .addParameter(ParameterizedTypeName.get(Map_ClsName, String_clsName, String_clsName), "config");

        // record the variable who need to invoke it's setter method
        final List<VariableElement> valueNeedToSet = new ArrayList<>();
        // map method name to method
        final Map<String, ExecutableElement> publicSetterMethods = new HashMap<>();

        methodSpec.addStatement("$T data = new $T()", dataCls, dataCls);
        methodSpec.addStatement("$T gson = new $T()", Gson_clsName, Gson_clsName);
        for (Element fieldOrMethod : members) {

            // record accessable setter methods
            if (fieldOrMethod instanceof ExecutableElement
                    && fieldOrMethod.getModifiers().contains(Modifier.PUBLIC)
                    && fieldOrMethod.toString().startsWith("set")) {
                publicSetterMethods.put(
                        fieldOrMethod.getSimpleName().toString(), // setter method name
                        (ExecutableElement) fieldOrMethod // setter method
                );
            }

            BssValue bssValueAnno = fieldOrMethod.getAnnotation(BssValue.class);
            if (bssValueAnno == null) continue;

            final String property = bssValueAnno.property();
            final String key = bssValueAnno.key();

            if (fieldOrMethod instanceof VariableElement) {
                if (!fieldOrMethod.getModifiers().contains(Modifier.PUBLIC)) {
                    // 如果变量有set方法 则调用其set方法
                    valueNeedToSet.add((VariableElement) fieldOrMethod);
                    continue;
                } else {
                    checkElementAccessible(fieldOrMethod, dataCls);
                    // data.field = config.get("property")
                    final VariableElement field = (VariableElement) fieldOrMethod;
                    generateParseCondition(methodSpec, property, key, new ParserGen() {
                        @Override
                        public void parse(MethodSpec.Builder methodSpec, String value) {
                            final String dest = generateConfigParseJson(methodSpec, field, value, key);
                            methodSpec.addStatement("data." + field + " = " + dest);
                        }
                    });
                }

            } else if (fieldOrMethod instanceof ExecutableElement) {
                final ExecutableElement method = (ExecutableElement) fieldOrMethod;
                checkElementAccessible(fieldOrMethod, dataCls);
                checkMethodParams(method, 1);
                // data.parseMethod(config.get("property"))
                final VariableElement argument = method.getParameters().get(0);
                generateParseCondition(methodSpec, property, key, new ParserGen() {
                    @Override
                    public void parse(MethodSpec.Builder methodSpec, String value) {
                        final String dest = generateConfigParseJson(methodSpec, argument, value, key);
                        methodSpec.addStatement("data." + method.getSimpleName() + "(" + dest + ")");
                    }
                });
            }
        } //每个变量或方法

        // 补充set方法
        for (VariableElement var : valueNeedToSet) {
            final String setterMethodName = "set" + toVarStr(var.toString());
            final ExecutableElement setterMethod = publicSetterMethods.get(setterMethodName);
            if (publicSetterMethods.containsKey(setterMethodName)) {
                checkMethodParams(setterMethod, 1);
                final BssValue bssValueAnno = var.getAnnotation(BssValue.class);
                final String property = bssValueAnno.property();
                final String key = bssValueAnno.key();
                // data.setValue(config.get("property"))
                final VariableElement argument = setterMethod.getParameters().get(0);
                generateParseCondition(methodSpec, property, key, new ParserGen() {
                    @Override
                    public void parse(MethodSpec.Builder methodSpec, String value) {
                        final String dest = generateConfigParseJson(methodSpec, argument, value, key);
                        methodSpec.addStatement("data." + setterMethodName + "(" + dest + ")");
                    }
                });
            } else {
                throw new IllegalStateException("the field '" + var + "' with @BssValue in " + dataCls +
                        " must be public or to provide a public getter/setter method");
            }
        }
        methodSpec.addStatement("return data");

        return methodSpec.build();
    }

    @SuppressWarnings("SameParameterValue")
    private void checkMethodParams(ExecutableElement method, int numOfParam) {
        List<? extends Element> params = method.getParameters();
        if (params.size() != numOfParam) {
            throw new IllegalArgumentException("the method " + method + " in " +
                    method.getEnclosingElement() + " should contains " + numOfParam + " argument(s)!");
        }
    }

    private int tempCnt = 0;

    private String generateConfigParseJson(MethodSpec.Builder methodSpec, VariableElement destEle, String valueName, String key) {
        TypeName destTypeName = TypeName.get(destEle.asType());
        if (!key.isEmpty()) {
            valueName = String.format("new %s(%s).getString(\"%s\")",
                    JSONObject_ClsName, valueName, key);
        }
        if (String_clsName.equals(destTypeName)) {
            return valueName;
        }

        final String variableName = tempName(tempCnt);
        tempCnt++;
        if (destTypeName.isPrimitive()) {
            destTypeName = destTypeName.box();
        }
        if (destTypeName.isBoxedPrimitive()) {
            methodSpec.addStatement("final $T $L = " +
                    "$T.valueOf(" + valueName + ")", destTypeName, variableName, destTypeName);
        } else {
            methodSpec.addStatement("final $T $L = " +
                            "gson.fromJson(" + valueName + ", new $T<" + destTypeName + ">() {}.getType())",
                    destTypeName, variableName, TypeToken_ClsName);
        }

        return variableName;
    }

    private String tempName(int tmpCnt) {
        return "tmp_" + tmpCnt;
    }

    private int cnt = 0;

    private void generateParseCondition(MethodSpec.Builder method, String property, String key, ParserGen gen) {
        method.addStatement("final String " + valueName(cnt) + " = " + getConfig(property));
        method.beginControlFlow("if(" + valueName(cnt) + " != null)");
        if (!key.isEmpty()) {
            method.beginControlFlow("try");
        }
        gen.parse(method, valueName(cnt));
        if (!key.isEmpty()) {
            method.endControlFlow();
            method.beginControlFlow("catch($T e)", JSONException_ClsName);
            method.addStatement("$T.logger().e(e)", Publess_ClsName);
            method.endControlFlow();
//            method.beginControlFlow("catch($T e)", NumberFormatException_ClsName);
//            method.addStatement("$T.logger().e(e.toString())", Publess_ClsName);
//            method.endControlFlow();
        }
        method.endControlFlow();
        cnt++;
    }

    private String valueName(int cnt) {
        return "value_" + cnt;
    }

    private String getConfig(String property) {
        return "config.get(\"" + property + "\")";
    }

    private interface ParserGen {
        void parse(MethodSpec.Builder methodSpec, String valueName);
    }

    private TypeSpec generateDataParserClass(String bssName, TypeName dataCls, Iterable<MethodSpec> methods) {
        return TypeSpec.classBuilder(bssName + "$Parser")
                .addModifiers(Modifier.PUBLIC)
                .addMethods(methods)
                .addSuperinterface(ParameterizedTypeName.get(DataParser_ClsName, dataCls))
                .build();
    }

    private void genConfigClass(TypeName dataCls, String bssName, String bssCode, String packageName) {

        MethodSpec defaultValue = MethodSpec.methodBuilder("defaultValue")
                .addModifiers(Modifier.PROTECTED)
                .addAnnotation(Override.class)
                .returns(dataCls)
                .addStatement("return new $T()", dataCls)
                .build();

        MethodSpec getBssCode = MethodSpec.methodBuilder("getBssCode")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(String_clsName)
                .addStatement("return $S", bssCode)
                .build();

        MethodSpec dataParser = MethodSpec.methodBuilder("dataParser")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(DataParser_ClsName, dataCls))
                .addStatement("return new $T()", ClassName.get(packageName, bssName + "$Parser"))
                .build();

        TypeSpec cls = generateDataConfigClass(bssName, dataCls, Arrays.asList(defaultValue, getBssCode, dataParser));
        writeFile(cls, packageName);
    }

    private TypeSpec generateDataConfigClass(String bssName, TypeName dataCls, Iterable<MethodSpec> methods) {
        return TypeSpec.classBuilder(bssName)
                .addModifiers(Modifier.PUBLIC)
                .addMethods(methods)
                .superclass(ParameterizedTypeName.get(BaseConfig_ClsName, dataCls))
                .build();
    }

    private void genInitClass(TypeElement pluginClass, Map<ClassName, String> mapDataToConfig) {
        final String packageName = pluginClass.getEnclosingElement().toString();
        final String pluginName = pluginClass.getSimpleName().toString();
        // ?
        final TypeName wild = WildcardTypeName.subtypeOf(TypeName.OBJECT);
        // Class<?>
        final TypeName cls_wild = ParameterizedTypeName.get(ClassName.get(Class.class), wild);
        // BaseConfig<?>
        final TypeName config_wild = ParameterizedTypeName.get(BaseConfig_ClsName, wild);
        // Map<Class<?>,BaseConfig<?>>
        final TypeName map_class_config = ParameterizedTypeName.get(Map_ClsName, cls_wild, config_wild);
        log.info(map_class_config.toString());
        MethodSpec.Builder loadInto = MethodSpec.methodBuilder("loadInto")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(TypeName.VOID)
                .addParameter(map_class_config, "config");
        for (Map.Entry<ClassName, String> entry : mapDataToConfig.entrySet()) {
            loadInto.addStatement("config.put($T.class, new " + entry.getValue() + "())", entry.getKey());
        }
        TypeSpec cls = TypeSpec.classBuilder(pluginName + "$ConfigCenter$Initialization")
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(PluginInitialization_ClsName)
                .addMethod(loadInto.build())
                .build();
        writeFile(cls, packageName);
    }

    private void genDataInitClass(ClassName dataClassName, String bssConfigName) {
        final String packageName = dataClassName.packageName();
        // ?
        final TypeName wild = WildcardTypeName.subtypeOf(TypeName.OBJECT);
        // Class<?>
        final TypeName cls_wild = ParameterizedTypeName.get(ClassName.get(Class.class), wild);
        // BaseConfig<?>
        final TypeName config_wild = ParameterizedTypeName.get(BaseConfig_ClsName, wild);
        // Map<Class<?>,BaseConfig<?>>
        final TypeName map_class_config = ParameterizedTypeName.get(Map_ClsName, cls_wild, config_wild);

        MethodSpec.Builder loadInto = MethodSpec.methodBuilder("loadInto")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(TypeName.VOID)
                .addParameter(map_class_config, "config")
                .addStatement("config.put($T.class, new " + bssConfigName + "())", dataClassName);
        TypeSpec cls = TypeSpec.classBuilder(dataClassName.simpleName() + "$$Initializer")
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(PluginInitialization_ClsName)
                .addMethod(loadInto.build())
                .build();
        writeFile(cls, packageName);
    }

    /**
     * 首字母大写字符串
     */
    private String toVarStr(String str) {
        if (str == null) return "";
        if (str.length() <= 1) return str.toUpperCase();
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private String getPackageName(Element element) {
        log.info("Data Class：" + element);
        Element e = element;
        while (!(e instanceof PackageElement)) {
            e = e.getEnclosingElement();
        }
        return e.toString();
    }

    /**
     * 确保变量或方法是否可访问
     */
    private void checkElementAccessible(Element fieldOrMethod, TypeName scope) {
        if (!fieldOrMethod.getModifiers().contains(Modifier.PUBLIC)) {
            throw new IllegalStateException("what a pity! the field or method '" + fieldOrMethod +
                    "' in '" + scope + "' with annotation @BssValue must be public!");
        }

        if (fieldOrMethod.getModifiers().contains(Modifier.ABSTRACT)
                || fieldOrMethod.getModifiers().contains(Modifier.NATIVE)) {
            throw new IllegalStateException("the field or method '" + fieldOrMethod + "' in '" + scope
                    + "' with annotation @BssValue can not be abstract or native");
        }
    }

    /**
     * 确保所有@BssValue注解的值 都在@BssConfig注解的class里面
     */
    private void checkClassAnnotation(RoundEnvironment env) {
        Set<? extends Element> elements = env.getElementsAnnotatedWith(BssValue.class);
        for (Element fieldOrMethod : elements) {
            Element cls = fieldOrMethod.getEnclosingElement();
            BssConfig configAnno = cls.getAnnotation(BssConfig.class);
            if (configAnno == null) {
                throw new IllegalStateException("the class " + cls + " missing " +
                        "@BssConfig annotation contains @BssValue '" + fieldOrMethod + "'!");
            }
        }
    }

    private void writeFile(TypeSpec cls, String packageName) {
        JavaFile file = JavaFile.builder(packageName, cls).build();
        try {
            file.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            //e.printStackTrace();
        }
    }
}