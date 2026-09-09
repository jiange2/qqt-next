package com.qqt.music

object AppConfig {
    /** 主用地址：后端域名入口（与回退同端口 8001），明文 HTTP（仓库 docs/adr/0009） */
    const val BASE_URL = "http://qqt.yunshangzhiai7.top:8001/"
    /** 回退地址：域名连接层失败时改用的 IP 直连（仓库 docs/adr/0009） */
    const val FALLBACK_BASE_URL = "http://101.132.159.145:8001/"
    const val PACKAGE_NAME = "com.vpapps.onlinemp3"
    const val SIGN_KEY = "viaviweb"

    /** 歌词文件内容加密口令（仓库 docs/adr/0010）：必须与服务端 LRC_CONTENT_SECRET 一致，
     *  否则密文歌词解密失败显示「歌词加载失败」；存量明文歌词不受影响 */
    const val LRC_CONTENT_SECRET = "518b465562a5ec61b6d750fa68533bc1"

    /** 媒体 CDN host（仓库级 ADR 0011）：字节解混淆只对该 host 的响应生效，其他地址原样透传。
     *  必须与服务端 OSS_PUBLIC_BASE 的域名一致，换值 = 发版 */
    const val MEDIA_HOST = "cdn.qqt.yunshangzhiai7.top"

    /** 媒体字节混淆位移量（仓库级 ADR 0011）：OSS 存储逐字节 +31，客户端读出路径 -31 还原；
     *  必须与后端 obfuscate.ts 的 SHIFT 一致 */
    const val MEDIA_BYTE_SHIFT = 31
}
