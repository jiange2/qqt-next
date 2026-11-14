package android.net

import android.os.Parcel

/**
 * JVM 测试专用的 Uri 替身（票据02 ViewModel 编排测试）。
 *
 * 为什么需要它：单元测试跑在 mockable android.jar 上（returnDefaultValues=true），
 * `Uri.parse` / `Uri.fromFile` 等工厂方法一律返回默认值 null，静态字段 `Uri.EMPTY` 也是 null
 * （mockable jar 清空了 `<clinit>`，已对 AGP 8.5.2 MockableJarGenerator 字节码静态核实）——
 * 拿不到任何非空 Uri 实例喂 [com.qqt.music.ui.screens.localmusic.LocalMusicViewModel.importAudio]。
 *
 * 为什么放在 android.net 包：`Uri()` 构造器是包私有（javap android-35 核实），只有同包才能继承。
 * 构造器在 mockable jar 里被截成 super()+return（不抛异常），实例可正常创建。
 *
 * 口径：导入编排只把 Uri 原样转交接缝（fake 接缝不读它），本替身除 [toString] 外全部方法抛
 * [UnsupportedOperationException]——被测代码若意外调用 Uri 的框架方法会响亮失败，而不是静默拿 null。
 * 需要更多行为时再按需补，不预先实现。
 */
class TestUri(private val uriString: String) : Uri() {

    override fun toString(): String = uriString

    override fun isHierarchical(): Boolean = throw UnsupportedOperationException("TestUri 不支持")
    override fun isRelative(): Boolean = throw UnsupportedOperationException("TestUri 不支持")
    override fun getScheme(): String? = throw UnsupportedOperationException("TestUri 不支持")
    override fun getSchemeSpecificPart(): String? = throw UnsupportedOperationException("TestUri 不支持")
    override fun getEncodedSchemeSpecificPart(): String? = throw UnsupportedOperationException("TestUri 不支持")
    override fun getAuthority(): String? = throw UnsupportedOperationException("TestUri 不支持")
    override fun getEncodedAuthority(): String? = throw UnsupportedOperationException("TestUri 不支持")
    override fun getUserInfo(): String? = throw UnsupportedOperationException("TestUri 不支持")
    override fun getEncodedUserInfo(): String? = throw UnsupportedOperationException("TestUri 不支持")
    override fun getHost(): String? = throw UnsupportedOperationException("TestUri 不支持")
    override fun getPort(): Int = throw UnsupportedOperationException("TestUri 不支持")
    override fun getPath(): String? = throw UnsupportedOperationException("TestUri 不支持")
    override fun getEncodedPath(): String? = throw UnsupportedOperationException("TestUri 不支持")
    override fun getQuery(): String? = throw UnsupportedOperationException("TestUri 不支持")
    override fun getEncodedQuery(): String? = throw UnsupportedOperationException("TestUri 不支持")
    override fun getFragment(): String? = throw UnsupportedOperationException("TestUri 不支持")
    override fun getEncodedFragment(): String? = throw UnsupportedOperationException("TestUri 不支持")
    override fun getPathSegments(): List<String> = throw UnsupportedOperationException("TestUri 不支持")
    override fun getLastPathSegment(): String? = throw UnsupportedOperationException("TestUri 不支持")
    override fun buildUpon(): Builder = throw UnsupportedOperationException("TestUri 不支持")
    override fun describeContents(): Int = throw UnsupportedOperationException("TestUri 不支持")
    override fun writeToParcel(dest: Parcel, flags: Int) = throw UnsupportedOperationException("TestUri 不支持")
}