# 鉴权与登录

> **何时阅读**：当你要修改管理员登录逻辑、Session 鉴权守卫、用户注册/登录 API，或排查「未登录被重定向」问题时，阅读此文件。

## 功能概述

后端有两套独立的鉴权体系：管理后台（基于 PHP Session）和 App 用户（基于 API 签名 + 用户 token）。管理后台通过 `session_check.php` 守卫每个页面；API 调用通过 `checkSignSalt()` 函数验证每次请求的签名。

## 关键文件

| 文件 | 职责 |
|------|------|
| `login_db.php` | 管理员登录处理（验证账号 + 设置 Session） |
| `logout.php` | 销毁 Session，跳转登录页 |
| `includes/session_check.php` | Session 守卫：未登录则 `header("Location: login_db.php")` |
| `includes/function.php` | `checkSignSalt()` — API 请求签名验证 |
| `includes/connection.php` | Session 初始化（`session_start()`）；读取 `tbl_settings` 的 `package_name` |
| `verification.php` | 用户邮箱验证（点击邮件中的链接后激活账号） |

## 管理员登录流程

1. 访问 `login_db.php` 显示登录表单
2. POST 提交 `username` + `password`
3. `adminUser($username, $password)` 函数（`function.php`）：`md5($password)` 后查 `tbl_admin`
4. 验证通过：`$_SESSION['ADMIN_ID']` 和 `$_SESSION['ADMIN_USERNAME']` 写入 Session
5. 跳转后台首页 `home.php`

所有管理后台页面顶部均需：

```php
require("includes/session_check.php");
// session_check.php 内部：
if (!isset($_SESSION['ADMIN_ID'])) {
    header("Location: login_db.php");
    exit;
}
```

## API 签名验证流程

```
Android 端                          后端 api.php
发送 data = base64(urlencode(json)) → checkSignSalt($_POST['data'])
                                         ↓
                                    base64_decode → urldecode → json_decode
                                         ↓
                                    验证: md5("viaviweb" + salt) == sign ?
                                    验证: package_name == PACKAGE_NAME ?
                                         ↓ 失败
                                    返回 { "success": -1, "msg": "Invalid sign salt." }
                                         ↓ 成功
                                    返回解码后的参数 array
```

`PACKAGE_NAME` 常量在 `connection.php` 启动时从 `tbl_settings.package_name` 读取，修改后台包名配置立即影响所有 App 请求。

## App 用户登录（API）

App 用户（非管理员）通过 `api.php` 中的 `method_name = "login"` / `"register"` 接口认证。用户数据存储在 `tbl_users`，密码使用 MD5 哈希存储。注册后发送验证邮件（通过 `smtp_email.php` 的 PHPMailer），用户点击链接后 `verification.php` 将 `tbl_users.verified` 设为 1。

## 注意事项

- 管理员密码在 `tbl_admin` 中以 MD5 裸哈希存储（无加盐），生产环境安全性较低，建议使用 `password_hash()` 替换
- App 用户密码同样是 MD5 无盐哈希，存在安全风险
- `connection.php` 调用了 `session_start()`，必须在任何输出之前 `include`；`ob_start()` 用于缓冲防止意外输出导致 Session 失败
- API 的 `salt` 字段是客户端生成的随机串（Android 端使用时间戳），后端不存储，仅用于单次签名校验，不防重放攻击
