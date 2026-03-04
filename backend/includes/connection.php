<?php
    error_reporting(E_ALL);
    ini_set('display_errors', 1);
	ob_start();
    session_start();

 	header("Content-Type: text/html;charset=UTF-8");

	// 支持 Docker 环境变量
	$db_host = getenv('DB_HOST') ?: 'localhost';
	$db_user = getenv('DB_USER') ?: 'root';
	$db_password = getenv('DB_PASSWORD') ?: 'lq520977WC';
	$db_name = getenv('DB_NAME') ?: 'qqt';

	DEFINE ('DB_USER', $db_user);
	DEFINE ('DB_PASSWORD', $db_password);
	DEFINE ('DB_HOST', $db_host); //填写数据库信息
	DEFINE ('DB_NAME', $db_name);

	$mysqli =mysqli_connect(DB_HOST,DB_USER,DB_PASSWORD,DB_NAME);

	if ($mysqli->connect_errno)
	{
    	echo "Failed to connect to MySQL: (" . $mysqli->connect_errno . ") " . $mysqli->connect_error;
	}

	mysqli_query($mysqli,"SET NAMES 'utf8'");

	//Settings
	$setting_qry="SELECT * FROM tbl_settings where id='1'";
    $setting_result=mysqli_query($mysqli,$setting_qry);
    $settings_details=mysqli_fetch_assoc($setting_result);

    define("ONESIGNAL_APP_ID",$settings_details['onesignal_app_id']);
    define("ONESIGNAL_REST_KEY",$settings_details['onesignal_rest_key']);

    define("APP_NAME",$settings_details['app_name']);
    define("APP_LOGO",$settings_details['app_logo']);

    define("API_LATEST_LIMIT",$settings_details['api_latest_limit']);
    define("API_CAT_ORDER_BY",$settings_details['api_cat_order_by']);
    define("API_CAT_POST_ORDER_BY",$settings_details['api_cat_post_order_by']);


    //Profile
    if(isset($_SESSION['id']))
    {
    	$profile_qry="SELECT * FROM tbl_admin where id='".$_SESSION['id']."'";
	    $profile_result=mysqli_query($mysqli,$profile_qry);
	    $profile_details=mysqli_fetch_assoc($profile_result);

	    define("PROFILE_IMG",$profile_details['image']);
    }



?>

