# R8 宽 keep 规则（2026-09 体积优化决策：可靠性优先，一劳永逸）。
# Gson/Retrofit/OkHttp/Media3/Compose 均自带 consumer 规则，此处只补业务侧。

# Retrofit/Gson 依赖泛型签名与运行时注解
-keepattributes Signature, *Annotation*

# Gson 反射解析：@SerializedName 标注的字段不可删除、不可改名
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson TypeToken 兜底（Gson >=2.10 自带等效规则，此处防依赖降级）
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# data 包整体保留（API 接口 / DTO / 本地持久化模型）：
# 新增模型或字段永不静默坏，代价仅几十 KB 级的混淆收益
-keep class com.qqt.music.data.** { *; }

# release 崩溃堆栈保留行号，配合 mapping.txt + retrace 还原
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
