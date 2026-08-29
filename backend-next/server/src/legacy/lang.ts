// API 响应文案 —— 逐字复刻旧 backend/language/app_language.php（Q17 语义兼容决定）
// Android 端可能依赖具体文案字符串，禁止改动既有条目。
export const appLang = {
  invalid_email_format: "Email format is invalid !",
  otp_sent: "OTP has been sent on your mail...",
  email_not_found: "Email is not found !",
  invalid_password: "Password is invalid !",
  account_deactive: "Sorry ! Your account is suspended",
  login_success: "Login successfully...",
  login_fail: "Login is failed !",
  email_exist: "Email is already exist !",
  register_success: "Registration successfully...",
  register_fail: "Registration is failed !",
  comment_success: "Comment submitted successfully...",
  comment_fail: "Comment is failed",
  comment_delete: "Comment is deleted...",
  report_success: "Report submitted successfully...",
  report_fail: "Report is failed",
  report_already: "Report already submitted !",
  search_result: "Keyword is not found ! Try different keyword",
  password_sent_mail: "Password has been sent on your mail...",
  add_success: "Added successfully...",
  add_fail: "Adding is failed !",
  update_success: "Updated successfully...",
  update_fail: "Updatation is failed !",
  msg_sent: "Message has been sent...",
  rate_success: "You have successfully rated",
  rate_already: "You have already rated !",
  suggest_success:
    "You have successfully submitted your suggestion, it will be reviewed by admin and will uploaded if necessary",
  favourite_success: "Added to Favourite",
  favourite_remove_success: "Removed from Favourite",
  favourite_remove_error: "Error in remove from Favourite",
  no_data_msg: "Sorry no data found !",

  // 旧实现引用了未定义的 $app_lang['invalid_user_type']，PHP 输出空字符串，此处复刻
  invalid_user_type: "",

  // 新增文案（ADR 0003 决定 5：邮件功能移除后 forgot_pass 的固定响应）
  forgot_pass_disabled: "Password recovery is not available !",
} as const;
