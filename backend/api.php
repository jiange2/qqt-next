<?php

	include("includes/connection.php");
	include("includes/function.php");
	include("language/app_language.php");
	include("smtp_email.php");

	define("APP_FROM_EMAIL", $settings_details['email_from']);

	define("PACKAGE_NAME", $settings_details['package_name']);

	date_default_timezone_set("Asia/Kolkata");

	$file_path = getBaseUrl();

	function generateRandomPassword($length = 10) {
	    $characters = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
	    $charactersLength = strlen($characters);
	    $randomString = '';
	    for ($i = 0; $i < $length; $i++) {
	        $randomString .= $characters[rand(0, $charactersLength - 1)];
	    }
	    return $randomString;
	}

	if($settings_details['envato_buyer_name']=='' OR $settings_details['envato_purchase_code']=='' OR $settings_details['envato_purchased_status']==0) {

		$set['ONLINE_MP3'][] =array('msg' => 'Purchase code verification failed!','success'=>-1);
		header( 'Content-Type: application/json; charset=utf-8' );
	    echo $val= str_replace('\\/', '/', json_encode($set,JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}

	function is_favourite($post_id, $user_id, $type='song')
    {
        global $mysqli;

        $sql_favourite="SELECT * FROM tbl_favourite WHERE `post_id`='$post_id' AND `user_id`='$user_id' AND `type`='$type'";
        $res_favourite=mysqli_query($mysqli,$sql_favourite);

		if(mysqli_num_rows($res_favourite) > 0){
			return true;
		}
		else{
			return false;
		}
    }


	$get_method = checkSignSalt($_POST['data']);

	if($get_method['method_name'] == "home")
	{
		$limit = API_LATEST_LIMIT;

		$jsonObj_1 = array();

		$query1 = "SELECT * FROM tbl_banner WHERE status='1' ORDER BY tbl_banner.`bid` DESC";
		$sql1 = mysqli_query($mysqli, $query1);

		$user_id=isset($get_method['user_id']) ? $get_method['user_id'] : 0;

		while ($data1 = mysqli_fetch_assoc($sql1)) {

			$row1['bid'] = $data1['bid'];
			$row1['banner_title'] = $data1['banner_title'];
			$row1['banner_sort_info'] = $data1['banner_sort_info'];
			$row1['banner_link'] = $data1['link'];
			$row1['banner_image'] = $file_path . 'images/' . $data1['banner_image'];
			$row1['banner_image_thumb'] = $file_path . 'images/thumbs/' . $data1['banner_image'];


			$songs_ids = trim($data1['banner_songs']);

			$query01 = "SELECT * FROM tbl_mp3
				LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
				WHERE tbl_mp3.`id` IN ($songs_ids) AND tbl_category.`status`='1' AND tbl_mp3.`status`='1'";

			$sql01 = mysqli_query($mysqli, $query01);

			$row1['total_songs'] = mysqli_num_rows($sql01);

			if (mysqli_num_rows($sql01) > 0) {

				while ($data01 = mysqli_fetch_assoc($sql01))
				{
					$total_songs++;
					$row01['id'] = $data01['id'];
					$row01['cat_id'] = $data01['cat_id'];
					$row01['mp3_type'] = $data01['mp3_type'];
					$row01['mp3_title'] = $data01['mp3_title'];

					$mp3_file=$data01['mp3_url'];
					if($data01['mp3_type']=='local'){
						$mp3_file=$file_path.'uploads/'.basename($data01['mp3_url']);
					}

					$row01['mp3_url'] = $mp3_file;

					$row01['mp3_thumbnail_b'] = $file_path . 'images/' . $data01['mp3_thumbnail'];
					$row01['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data01['mp3_thumbnail'];

					$row01['mp3_artist'] = $data01['mp3_artist'];
					$row01['mp3_description'] = $data01['mp3_description'];
					$row01['total_rate'] = $data01['total_rate'];
					$row01['rate_avg'] = $data01['rate_avg'];
					$row01['total_views'] = $data01['total_views'];
					$row01['total_download'] = $data01['total_download'];

					$row01['is_favourite'] = is_favourite($data01['id'],$user_id);

					$row01['cid'] = $data01['cid'];
					$row01['category_name'] = $data01['category_name'];
					$row01['category_image'] = $file_path . 'images/' . $data01['category_image'];
					$row01['category_image_thumb'] = $file_path . 'images/thumbs/' . $data01['category_image'];

					$row1['songs_list'][] = $row01;
				}
			} else {
				$row1['songs_list'] = array();
			}

			array_push($jsonObj_1, $row1);

			unset($row1['songs_list']);
		}

		$row['home_banner'] = $jsonObj_1;

		$jsonObj4 = array();

		$query4 = "SELECT * FROM tbl_album WHERE status='1' ORDER BY tbl_album.`aid` DESC LIMIT $limit";
		$sql4 = mysqli_query($mysqli, $query4);

		while ($data4 = mysqli_fetch_assoc($sql4))
		{

			$row4['aid'] = $data4['aid'];
			$row4['album_name'] = $data4['album_name'];
			$row4['album_image'] = $file_path . 'images/' . $data4['album_image'];
			$row4['album_image_thumb'] = $file_path . 'images/thumbs/' . $data4['album_image'];

			array_push($jsonObj4, $row4);
		}

		$row['latest_album'] = $jsonObj4;


		$jsonObj3 = array();

		$query3 = "SELECT id,artist_name,artist_image FROM tbl_artist ORDER BY tbl_artist.`id` DESC LIMIT $limit";
		$sql3 = mysqli_query($mysqli, $query3);

		while ($data3 = mysqli_fetch_assoc($sql3)) {
			$row3['id'] = $data3['id'];
			$row3['artist_name'] = $data3['artist_name'];
			$row3['artist_image'] = $file_path . 'images/' . $data3['artist_image'];
			$row3['artist_image_thumb'] = $file_path . 'images/thumbs/' . $data3['artist_image'];

			array_push($jsonObj3, $row3);
		}

		$row['latest_artist'] = $jsonObj3;

		$jsonObj = array();
		$data_arr = array();

	//	$sql_views="SELECT DISTINCT tbl_mp3_views.`mp3_id`, `tbl_mp3_views`.`mp3_id`, `tbl_mp3_views`.`views` FROM tbl_mp3_views WHERE (tbl_mp3_views.`date` BETWEEN DATE_SUB(NOW(), INTERVAL 1 MONTH) AND NOW()) ORDER BY `tbl_mp3_views`.`views` DESC LIMIT 50";

        $sql_views="SELECT DISTINCT tbl_mp3_views.`mp3_id`, `tbl_mp3_views`.`mp3_id`, `tbl_mp3_views`.`views` FROM tbl_mp3_views WHERE `mp3_id`<38 ORDER BY `tbl_mp3_views`.`views` DESC LIMIT 50";

		$res_views=mysqli_query($mysqli, $sql_views);

		while ($row_views=mysqli_fetch_assoc($res_views))
		{
			$id=$row_views['mp3_id'];

			$sql = "SELECT * FROM tbl_mp3
				LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
				WHERE tbl_mp3.`status`='1' AND tbl_category.`status`='1' AND tbl_mp3.`id`='$id' ORDER BY tbl_mp3.`id` DESC";

			$result = mysqli_query($mysqli, $sql);

			if(mysqli_num_rows($result) > 0)
			{
				$data = mysqli_fetch_assoc($result);

				$data_arr['id'] = $data['id'];
				$data_arr['cat_id'] = $data['cat_id'];
				$data_arr['mp3_type'] = $data['mp3_type'];
				$data_arr['mp3_title'] = $data['mp3_title'];

				$mp3_file=$data['mp3_url'];
				if($data['mp3_type']=='local'){
					$mp3_file=$file_path.'uploads/'.basename($data['mp3_url']);
				}

				$data_arr['mp3_url'] = $mp3_file;

				$data_arr['mp3_thumbnail_b'] = $file_path . 'images/' . $data['mp3_thumbnail'];
				$data_arr['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data['mp3_thumbnail'];

				$data_arr['mp3_artist'] = $data['mp3_artist'];
				$data_arr['mp3_description'] = $data['mp3_description'];
				$data_arr['total_rate'] = $data['total_rate'];
				$data_arr['rate_avg'] = $data['rate_avg'];
				$data_arr['total_views'] = $data['total_views'];
				$data_arr['total_download'] = $data['total_download'];

				$data_arr['is_favourite'] = is_favourite($data['id'],$user_id);

				$data_arr['cid'] = $data['cid'];
				$data_arr['category_name'] = $data['category_name'];
				$data_arr['category_image'] = $file_path . 'images/' . $data['category_image'];
				$data_arr['category_image_thumb'] = $file_path . 'images/thumbs/' . $data['category_image'];

				array_push($jsonObj, $data_arr);
			}
		}

		$row['trending_songs'] = $jsonObj;

		$set['ONLINE_MP3'] = $row;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "home_new")
	{
		$limit = API_LATEST_LIMIT;

		$user_id=isset($get_method['user_id']) ? $get_method['user_id'] : 0;

		$jsonObj = array();
		$data_arr = array();

		$sql = "SELECT * FROM tbl_banner WHERE status='1' ORDER BY tbl_banner.`bid` DESC";
		$result = mysqli_query($mysqli, $sql);

		while ($data = mysqli_fetch_assoc($result)) {

			$data_arr['bid'] = $data['bid'];
			$data_arr['banner_title'] = $data['banner_title'];
			$data_arr['banner_sort_info'] = $data['banner_sort_info'];
			$data_arr['banner_link'] = $data['link'];
			$data_arr['banner_image'] = $file_path . 'images/' . $data['banner_image'];
			$data_arr['banner_image_thumb'] = $file_path . 'images/thumbs/' . $data['banner_image'];

			$songs_ids = trim($data['banner_songs']);

			$query01 = "SELECT * FROM tbl_mp3
				LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
				WHERE tbl_mp3.`id` IN ($songs_ids) AND tbl_category.`status`='1' AND tbl_mp3.`status`='1'";

			$sql01 = mysqli_query($mysqli, $query01);

			$data_arr['total_songs'] = mysqli_num_rows($sql01);

			$data_arr['songs_list'] = array();

			array_push($jsonObj, $data_arr);
		}

		$row['home_banner'] = $jsonObj;

		mysqli_free_result($result);

		$jsonObj = array();
		$data_arr = array();

		$sql = "SELECT * FROM tbl_album WHERE status='1' ORDER BY tbl_album.`aid` DESC LIMIT $limit";
		$result = mysqli_query($mysqli, $sql);

		while ($data = mysqli_fetch_assoc($result))
		{
			$data_arr['aid'] = $data['aid'];
			$data_arr['album_name'] = $data['album_name'];
			$data_arr['album_image'] = $file_path . 'images/' . $data['album_image'];
			$data_arr['album_image_thumb'] = $file_path . 'images/thumbs/' . $data['album_image'];

			array_push($jsonObj, $data_arr);
		}

		$row['latest_album'] = $jsonObj;

		mysqli_free_result($result);
		$jsonObj = array();
		$data_arr = array();

		$sql = "SELECT id,artist_name,artist_image FROM tbl_artist ORDER BY tbl_artist.`id` DESC LIMIT $limit";
		$result = mysqli_query($mysqli, $sql);

		while ($data = mysqli_fetch_assoc($result))
		{
			$data_arr['id'] = $data['id'];
			$data_arr['artist_name'] = $data['artist_name'];
			$data_arr['artist_image'] = $file_path . 'images/' . $data['artist_image'];
			$data_arr['artist_image_thumb'] = $file_path . 'images/thumbs/' . $data['artist_image'];

			array_push($jsonObj, $data_arr);
		}

		$row['latest_artist'] = $jsonObj;

		mysqli_free_result($result);
		$jsonObj = array();
		$data_arr = array();

		$sql_views="SELECT DISTINCT tbl_mp3_views.`mp3_id`, `tbl_mp3_views`.`mp3_id`, `tbl_mp3_views`.`views` FROM tbl_mp3_views WHERE (tbl_mp3_views.`date` BETWEEN DATE_SUB(NOW(), INTERVAL 1 MONTH) AND NOW()) ORDER BY `tbl_mp3_views`.`views` DESC LIMIT 50";

		$res_views=mysqli_query($mysqli, $sql_views);

		while ($row_views=mysqli_fetch_assoc($res_views))
		{
			$id=$row_views['mp3_id'];

			$sql = "SELECT * FROM tbl_mp3
				LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
				WHERE tbl_mp3.`status`='1' AND tbl_category.`status`='1' AND tbl_mp3.`id`='$id' ORDER BY tbl_mp3.`id` DESC";

			$result = mysqli_query($mysqli, $sql);

			if(mysqli_num_rows($result) > 0)
			{
				$data = mysqli_fetch_assoc($result);

				$data_arr['id'] = $data['id'];
				$data_arr['cat_id'] = $data['cat_id'];
				$data_arr['mp3_type'] = $data['mp3_type'];
				$data_arr['mp3_title'] = $data['mp3_title'];

				$mp3_file=$data['mp3_url'];
				if($data['mp3_type']=='local'){
					$mp3_file=$file_path.'uploads/'.basename($data['mp3_url']);
				}

				$data_arr['mp3_url'] = $mp3_file;

				$data_arr['mp3_thumbnail_b'] = $file_path . 'images/' . $data['mp3_thumbnail'];
				$data_arr['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data['mp3_thumbnail'];

				$data_arr['mp3_artist'] = $data['mp3_artist'];
				$data_arr['mp3_description'] = $data['mp3_description'];
				$data_arr['total_rate'] = $data['total_rate'];
				$data_arr['rate_avg'] = $data['rate_avg'];
				$data_arr['total_views'] = $data['total_views'];
				$data_arr['total_download'] = $data['total_download'];

				$data_arr['is_favourite'] = is_favourite($data['id'],$user_id);

				$data_arr['cid'] = $data['cid'];
				$data_arr['category_name'] = $data['category_name'];
				$data_arr['category_image'] = $file_path . 'images/' . $data['category_image'];
				$data_arr['category_image_thumb'] = $file_path . 'images/thumbs/' . $data['category_image'];

				array_push($jsonObj, $data_arr);
			}
		}

		$row['trending_songs'] = $jsonObj;

		$set['ONLINE_MP3'] = $row;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "all_songs")
	{
		$user_id=isset($get_method['user_id']) ? $get_method['user_id'] : 0;

		$query_rec = "SELECT COUNT(*) as num FROM tbl_mp3
		LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
		WHERE tbl_mp3.`status`='1' AND tbl_category.`status`='1'";
		$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

		$page_limit = 10;

		$limit = ($get_method['page'] - 1) * $page_limit;

		$jsonObj = array();

		$query = "SELECT * FROM tbl_mp3
		LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
		WHERE tbl_mp3.`status`='1' AND tbl_category.`status`='1' ORDER BY tbl_mp3.`id` DESC LIMIT $limit, $page_limit";

		$sql = mysqli_query($mysqli, $query);

		while ($data = mysqli_fetch_assoc($sql)) {

			$row['total_songs'] = $total_pages['num'];
			$row['id'] = $data['id'];
			$row['cat_id'] = $data['cat_id'];
			$row['mp3_type'] = $data['mp3_type'];
			$row['mp3_title'] = $data['mp3_title'];

			$mp3_file=$data['mp3_url'];
			if($data['mp3_type']=='local'){
				$mp3_file=$file_path.'uploads/'.basename($data['mp3_url']);
			}

			$row['mp3_url'] = $mp3_file;

			$row['mp3_thumbnail_b'] = $file_path . 'images/' . $data['mp3_thumbnail'];
			$row['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data['mp3_thumbnail'];

			$row['mp3_artist'] = $data['mp3_artist'];
			$row['mp3_description'] = $data['mp3_description'];
			$row['total_rate'] = $data['total_rate'];
			$row['rate_avg'] = $data['rate_avg'];
			$row['total_views'] = $data['total_views'];
			$row['total_download'] = $data['total_download'];

			$row['is_favourite'] = is_favourite($data['id'],$user_id);

			$row['cid'] = $data['cid'];
			$row['category_name'] = $data['category_name'];
			$row['category_image'] = $file_path . 'images/' . $data['category_image'];
			$row['category_image_thumb'] = $file_path . 'images/thumbs/' . $data['category_image'];


			array_push($jsonObj, $row);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "latest")
	{

		$user_id=isset($get_method['user_id']) ? $get_method['user_id'] : 0;

		$query_rec = "SELECT COUNT(*) as num FROM tbl_mp3
		LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
		WHERE tbl_mp3.`status`='1' AND tbl_category.`status`='1' ORDER BY tbl_mp3.`id`";

		$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

		$page_limit = API_LATEST_LIMIT;

		$limit = ($get_method['page'] - 1) * $page_limit;

		$jsonObj = array();

		$query = "SELECT * FROM tbl_mp3
		LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
		WHERE tbl_mp3.`status`='1' AND tbl_category.`status`='1' ORDER BY tbl_mp3.`id` DESC LIMIT $limit, $page_limit";

		$sql = mysqli_query($mysqli, $query);

		while ($data = mysqli_fetch_assoc($sql)) {
			$row['total_records'] = $total_pages['num'];

			$row['id'] = $data['id'];
			$row['cat_id'] = $data['cat_id'];
			$row['mp3_type'] = $data['mp3_type'];
			$row['mp3_title'] = $data['mp3_title'];


			$mp3_file=$data['mp3_url'];

			if($data['mp3_type']=='local'){
				$mp3_file=$file_path.'uploads/'.basename($data['mp3_url']);
			}

			$row['mp3_url'] = $mp3_file;

			$row['mp3_thumbnail_b'] = $file_path . 'images/' . $data['mp3_thumbnail'];
			$row['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data['mp3_thumbnail'];

			$row['mp3_artist'] = $data['mp3_artist'];
			$row['mp3_description'] = $data['mp3_description'];
			$row['total_rate'] = $data['total_rate'];
			$row['rate_avg'] = $data['rate_avg'];
			$row['total_views'] = $data['total_views'];
			$row['total_download'] = $data['total_download'];

			$row['is_favourite'] = is_favourite($data['id'],$user_id);

			$row['cid'] = $data['cid'];
			$row['category_name'] = $data['category_name'];
			$row['category_image'] = $file_path . 'images/' . $data['category_image'];
			$row['category_image_thumb'] = $file_path . 'images/thumbs/' . $data['category_image'];

			array_push($jsonObj, $row);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "banners")
	{
	    $query1 = "SELECT * FROM tbl_banner WHERE status='1' ORDER BY tbl_banner.`bid` DESC";
		$sql1 = mysqli_query($mysqli, $query1);

		$user_id=isset($get_method['user_id']) ? $get_method['user_id'] : 0;
        $jsonObj = [];
		while ($data1 = mysqli_fetch_assoc($sql1)) {
			$row1['bid'] = $data1['bid'];
			$row1['banner_title'] = $data1['banner_title'];
			$row1['banner_sort_info'] = $data1['banner_sort_info'];
			$row1['banner_link'] = $data1['link'];
			$row1['banner_image'] = $file_path . 'images/' . $data1['banner_image'];
			$row1['banner_image_thumb'] = $file_path . 'images/thumbs/' . $data1['banner_image'];
			array_push($jsonObj,$row1);
		}

		$set['ONLINE_MP3'] = $jsonObj;
		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "banner_songs")
	{
		$user_id=isset($get_method['user_id']) ? $get_method['user_id'] : 0;

		$post_order_by = API_CAT_POST_ORDER_BY;

		$banner_id = $get_method['banner_id'];

		$sql_banner="SELECT * FROM tbl_banner WHERE status='1' AND `bid`='$banner_id' ORDER BY tbl_banner.`bid` DESC";
		$res_banner = mysqli_query($mysqli,$sql_banner);
		$row_banner=mysqli_fetch_assoc($res_banner);

		$songs_ids = trim($row_banner['banner_songs']);

		$query_rec = "SELECT COUNT(*) as num FROM tbl_mp3
			LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
			WHERE tbl_mp3.`id` IN ($songs_ids) AND tbl_category.`status`='1' AND tbl_mp3.`status`='1'";

		$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

		$page_limit = 10;

		$limit = ($get_method['page'] - 1) * $page_limit;

		$jsonObj = array();

		$sql = "SELECT * FROM tbl_mp3
			LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
			WHERE tbl_mp3.`id` IN ($songs_ids) AND tbl_category.`status`='1' AND tbl_mp3.`status`='1' ORDER BY tbl_mp3.`id` $post_order_by LIMIT $limit, $page_limit";

		$result = mysqli_query($mysqli, $sql);

		while ($data = mysqli_fetch_assoc($result))
		{
			$row['total_records'] = $total_pages['num'];
			$row['id'] = $data['id'];
			$row['cat_id'] = $data['cat_id'];
			$row['album_id'] = $data['album_id'];
			$row['mp3_type'] = $data['mp3_type'];
			$row['mp3_title'] = $data['mp3_title'];

			$mp3_file=$data['mp3_url'];
			if($data['mp3_type']=='local'){
				$mp3_file=$file_path.'uploads/'.basename($data['mp3_url']);
			}

			$row['mp3_url'] = $mp3_file;

			$row['mp3_thumbnail_b'] = $file_path . 'images/' . $data['mp3_thumbnail'];
			$row['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data['mp3_thumbnail'];

			$row['mp3_artist'] = $data['mp3_artist'];
			$row['mp3_description'] = $data['mp3_description'];

			$row['total_rate'] = $data['total_rate'];
			$row['rate_avg'] = $data['rate_avg'];
			$row['total_views'] = $data['total_views'];
			$row['total_download'] = $data['total_download'];

			$row['is_favourite'] = is_favourite($data['id'],$user_id);

			$row['cid'] = $data['cid'];
			$row['link'] = $data['cid'];
			$row['category_name'] = $data['category_name'];
			$row['category_image'] = $file_path . 'images/' . $data['category_image'];
			$row['category_image_thumb'] = $file_path . 'images/thumbs/' . $data['category_image'];

			array_push($jsonObj, $row);
		}

		$set['ONLINE_MP3'] = $jsonObj;
		mysqli_free_result($result);

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "cat_list")
	{
		$query_rec = "SELECT COUNT(*) as num FROM tbl_category WHERE status=1";

		$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

		$page_limit = 10;

		$limit = ($get_method['page'] - 1) * $page_limit;

		$jsonObj = array();

		$cat_order = API_CAT_ORDER_BY;

		$query = "SELECT cid,category_name,category_image FROM tbl_category WHERE status=1 ORDER BY tbl_category." . $cat_order . " LIMIT $limit, $page_limit";
		$sql = mysqli_query($mysqli, $query);

		while ($data = mysqli_fetch_assoc($sql)) {
			$row['total_records'] = $total_pages['num'];

			$row['cid'] = $data['cid'];
			$row['category_name'] = $data['category_name'];
			$row['category_image'] = $file_path . 'images/' . $data['category_image'];
			$row['category_image_thumb'] = $file_path . 'images/thumbs/' . $data['category_image'];

			array_push($jsonObj, $row);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "cat_songs")
	{
		$post_order_by = API_CAT_POST_ORDER_BY;

		$cat_id = $get_method['cat_id'];

		$user_id=isset($get_method['user_id']) ? $get_method['user_id'] : 0;

		$query_rec = "SELECT COUNT(*) as num FROM tbl_mp3
		LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
		WHERE tbl_mp3.`cat_id`='$cat_id' AND tbl_category.`status`='1' AND tbl_mp3.`status`='1'";
		$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

		$page_limit = 10;

		$limit = ($get_method['page'] - 1) * $page_limit;

		$jsonObj = array();

		$query = "SELECT * FROM tbl_mp3
		LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
		WHERE tbl_mp3.`cat_id`='$cat_id' AND tbl_category.`status`='1' AND tbl_mp3.`status`='1' ORDER BY tbl_mp3.`id` " . $post_order_by . " LIMIT $limit, $page_limit";

		$sql = mysqli_query($mysqli, $query) or die(mysqli_error());

		while ($data = mysqli_fetch_assoc($sql)) {
			$row['total_records'] = $total_pages['num'];

			$row['id'] = $data['id'];
			$row['cat_id'] = $data['cat_id'];
			$row['mp3_type'] = $data['mp3_type'];
			$row['mp3_title'] = $data['mp3_title'];

			$mp3_file=$data['mp3_url'];
			if($data['mp3_type']=='local'){
				$mp3_file=$file_path.'uploads/'.basename($data['mp3_url']);
			}

			$row['mp3_url'] = $mp3_file;

			$row['mp3_thumbnail_b'] = $file_path . 'images/' . $data['mp3_thumbnail'];
			$row['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data['mp3_thumbnail'];

			$row['mp3_artist'] = $data['mp3_artist'];
			$row['mp3_description'] = $data['mp3_description'];
			$row['total_rate'] = $data['total_rate'];
			$row['rate_avg'] = $data['rate_avg'];
			$row['total_views'] = $data['total_views'];
			$row['total_download'] = $data['total_download'];

			$row['is_favourite'] = is_favourite($data['id'],$user_id);

			$row['cid'] = $data['cid'];
			$row['category_name'] = $data['category_name'];
			$row['category_image'] = $file_path . 'images/' . $data['category_image'];
			$row['category_image_thumb'] = $file_path . 'images/thumbs/' . $data['category_image'];


			array_push($jsonObj, $row);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "recent_artist_list")
	{
		$jsonObj = array();

		$query = "SELECT id,artist_name,artist_image FROM tbl_artist ORDER BY tbl_artist.`id` DESC LIMIT 10";
		$sql = mysqli_query($mysqli, $query);

		while ($data = mysqli_fetch_assoc($sql)) {

			$row['id'] = $data['id'];
			$row['artist_name'] = $data['artist_name'];
			$row['artist_image'] = $file_path . 'images/' . $data['artist_image'];
			$row['artist_image_thumb'] = $file_path . 'images/thumbs/' . $data['artist_image'];

			array_push($jsonObj, $row);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "artist_list")
	{

		$query_rec = "SELECT COUNT(*) as num FROM tbl_artist ORDER BY tbl_artist.`id`";

		$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

		$page_limit = 10;

		$limit = ($get_method['page'] - 1) * $page_limit;

		$jsonObj = array();

		$query = "SELECT id,artist_name,artist_image FROM tbl_artist ORDER BY tbl_artist.`id` LIMIT $limit, $page_limit";
		$sql = mysqli_query($mysqli, $query);

		while ($data = mysqli_fetch_assoc($sql)) {
			$row['total_records'] = $total_pages['num'];

			$row['id'] = $data['id'];
			$row['artist_name'] = $data['artist_name'];
			$row['artist_image'] = $file_path . 'images/' . $data['artist_image'];
			$row['artist_image_thumb'] = $file_path . 'images/thumbs/' . $data['artist_image'];

			array_push($jsonObj, $row);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "artist_album_list")
	{

		$query_rec = "SELECT COUNT(*) as num FROM tbl_album WHERE status='1' AND FIND_IN_SET(" . $get_method['artist_id'] . ",tbl_album.artist_ids) ORDER BY tbl_album.`aid` DESC";

		$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

		$page_limit = 10;

		$limit = ($get_method['page'] - 1) * $page_limit;

		$jsonObj = array();


		$query = "SELECT * FROM tbl_album WHERE status='1' AND FIND_IN_SET(" . $get_method['artist_id'] . ",tbl_album.artist_ids) ORDER BY tbl_album.`aid` DESC LIMIT $limit, $page_limit";

		$sql = mysqli_query($mysqli, $query);

		while ($data = mysqli_fetch_assoc($sql)) {
			$row['total_records'] = $total_pages['num'];

			$row['aid'] = $data['aid'];
			$row['artist_ids'] = $data['artist_ids'];
			$row['album_name'] = $data['album_name'];
			$row['album_image'] = $file_path . 'images/' . $data['album_image'];
			$row['album_image_thumb'] = $file_path . 'images/thumbs/' . $data['album_image'];

			array_push($jsonObj, $row);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "artist_name_songs")
	{
		$user_id=isset($get_method['user_id']) ? $get_method['user_id'] : 0;

		$post_order_by = API_CAT_POST_ORDER_BY;

		$artist_name = $get_method['artist_name'];

		$query_rec = "SELECT COUNT(*) as num FROM tbl_mp3
		LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
		WHERE FIND_IN_SET('" . $artist_name . "',tbl_mp3.`mp3_artist`) AND tbl_category.`status`='1' AND tbl_mp3.`status`='1'";
		$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

		$page_limit = 10;

		$limit = ($get_method['page'] - 1) * $page_limit;

		$jsonObj = array();

		$query = "SELECT * FROM tbl_mp3
		LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
		WHERE FIND_IN_SET('" . $artist_name . "',tbl_mp3.`mp3_artist`) AND tbl_category.`status`='1' AND tbl_mp3.`status`='1' ORDER BY tbl_mp3.`id` DESC LIMIT $limit, $page_limit";

		$sql = mysqli_query($mysqli, $query);

		while ($data = mysqli_fetch_assoc($sql)) {
			$row['total_records'] = $total_pages['num'];
			$row['id'] = $data['id'];
			$row['cat_id'] = $data['cat_id'];
			$row['mp3_type'] = $data['mp3_type'];
			$row['mp3_title'] = $data['mp3_title'];

			$mp3_file=$data['mp3_url'];
			if($data['mp3_type']=='local'){
				$mp3_file=$file_path.'uploads/'.basename($data['mp3_url']);
			}

			$row['mp3_url'] = $mp3_file;


			$row['mp3_thumbnail_b'] = $file_path . 'images/' . $data['mp3_thumbnail'];
			$row['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data['mp3_thumbnail'];

			$row['mp3_artist'] = $data['mp3_artist'];
			$row['mp3_description'] = $data['mp3_description'];
			$row['total_rate'] = $data['total_rate'];
			$row['rate_avg'] = $data['rate_avg'];
			$row['total_views'] = $data['total_views'];
			$row['total_download'] = $data['total_download'];

			$row['is_favourite'] = is_favourite($data['id'],$user_id);

			$row['cid'] = $data['cid'];
			$row['category_name'] = $data['category_name'];
			$row['category_image'] = $file_path . 'images/' . $data['category_image'];
			$row['category_image_thumb'] = $file_path . 'images/thumbs/' . $data['category_image'];


			array_push($jsonObj, $row);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "album_list")
	{
		$query_rec = "SELECT COUNT(*) as num FROM tbl_album WHERE `status`='1' ORDER BY tbl_album.`aid` DESC";

		$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

		$page_limit = 10;

		$limit = ($get_method['page'] - 1) * $page_limit;

		$jsonObj = array();


		$query = "SELECT * FROM tbl_album WHERE `status`='1' ORDER BY tbl_album.`aid` DESC LIMIT $limit, $page_limit";
		$sql = mysqli_query($mysqli, $query);

		while ($data = mysqli_fetch_assoc($sql)) {

			$row['total_records'] = $total_pages['num'];

			$row['aid'] = $data['aid'];
			$row['album_name'] = $data['album_name'];
			$row['album_image'] = $file_path . 'images/' . $data['album_image'];
			$row['album_image_thumb'] = $file_path . 'images/thumbs/' . $data['album_image'];

			array_push($jsonObj, $row);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "album_songs")
	{
		$user_id=isset($get_method['user_id']) ? $get_method['user_id'] : 0;

		$post_order_by = API_CAT_POST_ORDER_BY;

		$album_id = $get_method['album_id'];

		$query_rec = "SELECT COUNT(*) as num FROM tbl_mp3
		LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
		LEFT JOIN tbl_album ON tbl_mp3.`album_id`= tbl_album.`aid`
		WHERE tbl_mp3.`album_id`='$album_id' AND tbl_category.`status`='1' AND tbl_album.`status`='1' AND tbl_mp3.`status`='1'";
		$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

		$page_limit = 10;

		$limit = ($get_method['page'] - 1) * $page_limit;


		$jsonObj = array();

		$query = "SELECT * FROM tbl_mp3
		LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
		LEFT JOIN tbl_album ON tbl_mp3.`album_id`= tbl_album.`aid`
		WHERE tbl_mp3.`album_id`='$album_id' AND tbl_category.`status`='1' AND tbl_album.`status`='1' AND tbl_mp3.`status`='1' ORDER BY tbl_mp3.`mp3_title` " . $post_order_by . " LIMIT $limit, $page_limit";

		$sql = mysqli_query($mysqli, $query);

		while ($data = mysqli_fetch_assoc($sql)) {
			$row['total_records'] = $total_pages['num'];
			$row['id'] = $data['id'];
			$row['cat_id'] = $data['cat_id'];
			$row['album_id'] = $data['album_id'];
			$row['mp3_type'] = $data['mp3_type'];
			$row['mp3_title'] = $data['mp3_title'];

			$mp3_file=$data['mp3_url'];
			if($data['mp3_type']=='local'){
				$mp3_file=$file_path.'uploads/'.basename($data['mp3_url']);
			}

			$row['mp3_url'] = $mp3_file;

			$row['mp3_thumbnail_b'] = $file_path . 'images/' . $data['mp3_thumbnail'];
			$row['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data['mp3_thumbnail'];

			$row['mp3_artist'] = $data['mp3_artist'];
			$row['mp3_description'] = $data['mp3_description'];
			$row['total_rate'] = $data['total_rate'];
			$row['rate_avg'] = $data['rate_avg'];
			$row['total_views'] = $data['total_views'];
			$row['total_download'] = $data['total_download'];

			$row['is_favourite'] = is_favourite($data['id'],$user_id);

			$row['cid'] = $data['cid'];
			$row['category_name'] = $data['category_name'];
			$row['category_image'] = $file_path . 'images/' . $data['category_image'];
			$row['category_image_thumb'] = $file_path . 'images/thumbs/' . $data['category_image'];

			$row['aid'] = $data['aid'];
			$row['album_name'] = $data['album_name'];
			$row['album_image'] = $file_path . 'images/' . $data['album_image'];
			$row['album_image_thumb'] = $file_path . 'images/thumbs/' . $data['album_image'];

			array_push($jsonObj, $row);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "playlist")
	{

		$query_rec = "SELECT COUNT(*) as num FROM tbl_playlist WHERE status='1' ORDER BY tbl_playlist.`pid` DESC";

		$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

		$page_limit = 10;

		$limit = ($get_method['page'] - 1) * $page_limit;

		$jsonObj = array();

		$query = "SELECT * FROM tbl_playlist WHERE status='1' ORDER BY tbl_playlist.`pid` DESC LIMIT $limit, $page_limit";
		$sql = mysqli_query($mysqli, $query);

		while ($data = mysqli_fetch_assoc($sql)) {

			$row['total_records'] = $total_pages['num'];

			$row['pid'] = $data['pid'];
			$row['playlist_name'] = $data['playlist_name'];
			$row['playlist_image'] = $file_path . 'images/' . $data['playlist_image'];
			$row['playlist_image_thumb'] = $file_path . 'images/thumbs/' . $data['playlist_image'];

			array_push($jsonObj, $row);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "playlist_songs")
	{
		$user_id=isset($get_method['user_id']) ? $get_method['user_id'] : 0;

		$post_order_by = API_CAT_POST_ORDER_BY;

		$playlist_id = $get_method['playlist_id'];

		$jsonObj = array();

		$query = "SELECT * FROM tbl_playlist
		where tbl_playlist.`status`='1' AND pid='" . $playlist_id . "'";

		$sql = mysqli_query($mysqli, $query);

		while ($data = mysqli_fetch_assoc($sql)) {

			$row['pid'] = $data['pid'];
			$row['playlist_name'] = $data['playlist_name'];
			$row['playlist_image'] = $file_path . 'images/' . $data['playlist_image'];
			$row['playlist_image_thumb'] = $file_path . 'images/thumbs/' . $data['playlist_image'];

			$songs_list = explode(",", $data['playlist_songs']);

			$total_records = count($songs_list);

			foreach ($songs_list as $song_id) {
				$page_limit = 10;

				$limit = ($get_method['page'] - 1) * $page_limit;

				$query1 = "SELECT * FROM tbl_mp3
				LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
				WHERE tbl_mp3.`id`='$song_id' AND tbl_category.`status`='1' AND tbl_mp3.`status`='1' LIMIT $limit, $page_limit";

				$sql1 = mysqli_query($mysqli, $query1);

				while ($data1 = mysqli_fetch_assoc($sql1)) {
					$row1['total_records'] = "$total_records";

					$row1['id'] = $data1['id'];
					$row1['cat_id'] = $data1['cat_id'];
					$row1['mp3_type'] = $data1['mp3_type'];
					$row1['mp3_title'] = $data1['mp3_title'];

					$mp3_file=$data1['mp3_url'];

					if($data1['mp3_type']=='local'){
						$mp3_file=$file_path.'uploads/'.basename($data1['mp3_url']);
					}

					$row1['mp3_url'] = $mp3_file;


					$row1['mp3_thumbnail_b'] = $file_path . 'images/' . $data1['mp3_thumbnail'];
					$row1['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data1['mp3_thumbnail'];

					$row1['mp3_artist'] = $data1['mp3_artist'];
					$row1['mp3_description'] = $data1['mp3_description'];
					$row1['total_rate'] = $data1['total_rate'];
					$row1['rate_avg'] = $data1['rate_avg'];
					$row1['total_views'] = $data1['total_views'];
					$row1['total_download'] = $data1['total_download'];

					$row1['is_favourite'] = is_favourite($data1['id'],$user_id);

					$row1['cid'] = $data1['cid'];
					$row1['category_name'] = $data1['category_name'];
					$row1['category_image'] = $file_path . 'images/' . $data1['category_image'];
					$row1['category_image_thumb'] = $file_path . 'images/thumbs/' . $data1['category_image'];


					$row['songs_list'][] = $row1;
				}
			}

			array_push($jsonObj, $row);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "song_info")
	{
	    $user_id=isset($get_method['user_id']) ? $get_method['user_id'] : 0;
		$jsonObj = array();
		$song_id = $get_method['song_id'];
		$query = "SELECT * FROM tbl_mp3
		LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
		WHERE tbl_mp3.`id`='" . $get_method['song_id'] . "' AND tbl_category.`status`='1' AND tbl_mp3.status='1'";
		$sql = mysqli_query($mysqli, $query);
		while ($data = mysqli_fetch_assoc($sql)) {
			$row['id'] = $data['id'];
			$row['cat_id'] = $data['cat_id'];
			$row['mp3_type'] = $data['mp3_type'];
			$row['mp3_title'] = $data['mp3_title'];

			$mp3_file=$data['mp3_url'];
			if($data['mp3_type']=='local'){
				$mp3_file=$file_path.'uploads/'.basename($data['mp3_url']);
			}

			$row['mp3_url'] = $mp3_file;

			$row['mp3_thumbnail_b'] = $file_path . 'images/' . $data['mp3_thumbnail'];
			$row['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data['mp3_thumbnail'];
			$row['mp3_lrc_txt'] = $data['mp3_lrc_txt'] ?? '';
			$row['mp3_lrc_url'] = $data['mp3_lrc_url'] ? $file_path . 'lrc/' . $data['mp3_lrc_url'] : '';

			$row['mp3_artist'] = $data['mp3_artist'];
			$row['mp3_description'] = $data['mp3_description'];
			$row['total_rate'] = $data['total_rate'];
			$row['rate_avg'] = $data['rate_avg'];
			$row['total_views'] = $data['total_views'];
			$row['total_download'] = $data['total_download'];

			$row['is_favourite'] = is_favourite($data['id'],$user_id);

			$row['cid'] = $data['cid'];
			$row['category_name'] = $data['category_name'];
			$row['category_image'] = $file_path . 'images/' . $data['category_image'];
			$row['category_image_thumb'] = $file_path . 'images/thumbs/' . $data['category_image'];

			if ($get_method['user_id']) {
				$query1 = mysqli_query($mysqli, "SELECT * FROM tbl_rating WHERE post_id  = '" . $get_method['song_id'] . "' AND `ip` = '" . $get_method['user_id'] . "' ");
				$data1 = mysqli_fetch_assoc($query1);

				if (@count($data1) != 0) {
					$row['user_rate'] = $data1['rate'];
				} else {
					$row['user_rate'] = 0;
				}
			}

			array_push($jsonObj, $row);
		}

		$view_qry = mysqli_query($mysqli, "UPDATE tbl_mp3 SET total_views = total_views + 1 WHERE id = '" . $get_method['song_id'] . "'");

		$mp3_id = $get_method['song_id'];
		$date = date('Y-m-d');

		$start = (date('D') != 'Mon') ? date('Y-m-d', strtotime('last Monday')) : date('Y-m-d');
		$finish = (date('D') != 'Sat') ? date('Y-m-d', strtotime('next Saturday')) : date('Y-m-d');

		$query = "SELECT * FROM tbl_mp3_views WHERE mp3_id='$mp3_id' and date BETWEEN '$start' AND '$finish'";
		$sql = mysqli_query($mysqli, $query);

		if (mysqli_num_rows($sql) > 0) {

			$query1 = "UPDATE tbl_mp3_views SET views=views+1 WHERE mp3_id='$mp3_id' and date BETWEEN '$start' AND '$finish'";
			$sql1 = mysqli_query($mysqli, $query1);
		} else {

			$data = array(
				'mp3_id'  =>  $mp3_id,
				'views'  =>  1,
				'date'  =>  $date
			);

			$qry = Insert('tbl_mp3_views', $data);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "single_song")
	{

		$user_id=isset($get_method['user_id']) ? $get_method['user_id'] : 0;

		$jsonObj = array();

		$query = "SELECT * FROM tbl_mp3
		LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
		WHERE tbl_mp3.`id`='" . $get_method['song_id'] . "' AND tbl_category.`status`='1' AND tbl_mp3.status='1'";

		$sql = mysqli_query($mysqli, $query);

		while ($data = mysqli_fetch_assoc($sql)) {
			$row['id'] = $data['id'];
			$row['cat_id'] = $data['cat_id'];
			$row['mp3_type'] = $data['mp3_type'];
			$row['mp3_title'] = $data['mp3_title'];

			$mp3_file=$data['mp3_url'];
			if($data['mp3_type']=='local'){
				$mp3_file=$file_path.'uploads/'.basename($data['mp3_url']);
			}

			$row['mp3_url'] = $mp3_file;

			$row['mp3_thumbnail_b'] = $file_path . 'images/' . $data['mp3_thumbnail'];
			$row['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data['mp3_thumbnail'];

			$row['mp3_artist'] = $data['mp3_artist'];
			$row['mp3_description'] = $data['mp3_description'];

			$row['mp3_lrc_txt'] = $data['mp3_lrc_txt'] ?? '';
			$row['mp3_lrc_url'] = $data['mp3_lrc_url'] ? $file_path . 'lrc/' . $data['mp3_lrc_url'] : '';

			$row['total_rate'] = $data['total_rate'];
			$row['rate_avg'] = $data['rate_avg'];
			$row['total_views'] = $data['total_views'];
			$row['total_download'] = $data['total_download'];

			$row['is_favourite'] = is_favourite($data['id'],$user_id);

			$row['cid'] = $data['cid'];
			$row['category_name'] = $data['category_name'];
			$row['category_image'] = $file_path . 'images/' . $data['category_image'];
			$row['category_image_thumb'] = $file_path . 'images/thumbs/' . $data['category_image'];

			if ($get_method['user_id']) {
				$query1 = mysqli_query($mysqli, "SELECT * FROM tbl_rating WHERE post_id  = '" . $get_method['song_id'] . "' AND `ip` = '" . $get_method['user_id'] . "' ");
				$data1 = mysqli_fetch_assoc($query1);

				if (@count($data1) != 0) {
					$row['user_rate'] = $data1['rate'];
				} else {
					$row['user_rate'] = 0;
				}
			}

			array_push($jsonObj, $row);
		}

		$view_qry = mysqli_query($mysqli, "UPDATE tbl_mp3 SET total_views = total_views + 1 WHERE id = '" . $get_method['song_id'] . "'");

		$mp3_id = $get_method['song_id'];
		$date = date('Y-m-d');

		$start = (date('D') != 'Mon') ? date('Y-m-d', strtotime('last Monday')) : date('Y-m-d');
		$finish = (date('D') != 'Sat') ? date('Y-m-d', strtotime('next Saturday')) : date('Y-m-d');

		$query = "SELECT * FROM tbl_mp3_views WHERE mp3_id='$mp3_id' and date BETWEEN '$start' AND '$finish'";
		$sql = mysqli_query($mysqli, $query);


		if (mysqli_num_rows($sql) > 0) {

			$query1 = "UPDATE tbl_mp3_views SET views=views+1 WHERE mp3_id='$mp3_id' and date BETWEEN '$start' AND '$finish'";
			$sql1 = mysqli_query($mysqli, $query1);
		} else {

			$data = array(
				'mp3_id'  =>  $mp3_id,
				'views'  =>  1,
				'date'  =>  $date
			);

			$qry = Insert('tbl_mp3_views', $data);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "song_download") {
		$jsonObj = array();

		$view_qry = mysqli_query($mysqli, "UPDATE tbl_mp3 SET total_download = total_download + 1 WHERE id = '" . $get_method['song_id'] . "'");

		$total_dw_sql = "SELECT * FROM tbl_mp3 WHERE id='" . $get_method['song_id'] . "'";
		$total_dw_res = mysqli_query($mysqli, $total_dw_sql);
		$total_dw_row = mysqli_fetch_assoc($total_dw_res);


		$jsonObj = array('total_download' => $total_dw_row['total_download']);

		$set['ONLINE_MP3'][] = $jsonObj;
		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "song_search") {

		$user_id=isset($get_method['user_id']) ? $get_method['user_id'] : 0;

		if ($get_method['search_type'] == "songs") {

			$query_rec = "SELECT COUNT(*) as num FROM tbl_mp3
			LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
			WHERE tbl_mp3.`status`='1' AND tbl_category.`status`='1' AND tbl_mp3.`mp3_title` like '%" . addslashes($get_method['search_text']) . "%'
			ORDER BY tbl_mp3.mp3_title";

			$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

			$page_limit = 10;

			$limit = ($get_method['page'] - 1) * $page_limit;

			$jsonObj0 = array();

			$query0 = "SELECT * FROM tbl_mp3
			LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
			WHERE tbl_mp3.`status`='1' AND tbl_category.`status`='1' AND tbl_mp3.`mp3_title` like '%" . addslashes($get_method['search_text']) . "%'
			ORDER BY tbl_mp3.mp3_title LIMIT $limit, $page_limit";

			$sql0 = mysqli_query($mysqli, $query0) or die(mysqli_error());

			while ($data0 = mysqli_fetch_assoc($sql0)) {
				$row0['total_data'] = $total_pages['num'];
				$row0['id'] = $data0['id'];
				$row0['cat_id'] = $data0['cat_id'];
				$row0['mp3_type'] = $data0['mp3_type'];
				$row0['mp3_title'] = $data0['mp3_title'];

				$mp3_file=$data0['mp3_url'];
				if($data0['mp3_type']=='local'){
					$mp3_file=$file_path.'uploads/'.basename($data0['mp3_url']);
				}

				$row0['mp3_url'] = $mp3_file;


				$row0['mp3_thumbnail_b'] = $file_path . 'images/' . $data0['mp3_thumbnail'];
				$row0['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data0['mp3_thumbnail'];

				$row0['mp3_artist'] = $data0['mp3_artist'];
				$row0['mp3_description'] = $data0['mp3_description'];
				$row0['total_rate'] = $data0['total_rate'];
				$row0['rate_avg'] = $data0['rate_avg'];
				$row0['total_views'] = $data0['total_views'];
				$row0['total_download'] = $data0['total_download'];

				$row0['is_favourite'] = is_favourite($data0['id'],$user_id);

				$row0['cid'] = $data0['cid'];
				$row0['category_name'] = $data0['category_name'];
				$row0['category_image'] = $file_path . 'images/' . $data0['category_image'];
				$row0['category_image_thumb'] = $file_path . 'images/thumbs/' . $data0['category_image'];

				array_push($jsonObj0, $row0);
			}

			$set['ONLINE_MP3'] = $jsonObj0;
		}
		else if($get_method['search_type'] == "artist") {

			$query_rec = "SELECT COUNT(*) as num FROM tbl_artist
			WHERE tbl_artist.`artist_name` like '%" . addslashes($get_method['search_text']) . "%'
			ORDER BY tbl_artist.artist_name";
			$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

			$page_limit = 10;

			$limit = ($get_method['page'] - 1) * $page_limit;

			$jsonObj2 = array();

			$query2 = "SELECT * FROM tbl_artist
			WHERE tbl_artist.`artist_name` like '%" . addslashes($get_method['search_text']) . "%'
			ORDER BY tbl_artist.artist_name LIMIT $limit, $page_limit";

			$sql2 = mysqli_query($mysqli, $query2) or die(mysqli_error());

			while ($data2 = mysqli_fetch_assoc($sql2)) {

				$row2['total_data'] = $total_pages['num'];
				$row2['id'] = $data2['id'];
				$row2['artist_name'] = $data2['artist_name'];
				$row2['artist_image'] = $file_path . 'images/' . $data2['artist_image'];
				$row2['artist_image_thumb'] = $file_path . 'images/thumbs/' . $data2['artist_image'];

				array_push($jsonObj2, $row2);
			}

			$set['ONLINE_MP3'] = $jsonObj2;
		}
		else if($get_method['search_type'] == "album") {

			$query_rec = "SELECT COUNT(*) as num FROM tbl_album
			WHERE tbl_album.`album_name` AND tbl_album.`status`='1' like '%" . addslashes($get_method['search_text']) . "%'
			ORDER BY tbl_album.album_name";
			$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

			$page_limit = 10;

			$limit = ($get_method['page'] - 1) * $page_limit;

			$jsonObj1 = array();

			$query1 = "SELECT * FROM tbl_album
			WHERE tbl_album.`album_name` like '%" . addslashes($get_method['search_text']) . "%' AND tbl_album.`status`='1'
			ORDER BY tbl_album.album_name LIMIT $limit, $page_limit";

			$sql1 = mysqli_query($mysqli, $query1) or die(mysqli_error());

			while ($data1 = mysqli_fetch_assoc($sql1)) {
				$row1['total_data'] = $total_pages['num'];
				$row1['aid'] = $data1['aid'];
				$row1['artist_ids'] = $data1['artist_ids'] ? $data1['artist_ids'] : '';
				$row1['album_name'] = $data1['album_name'];
				$row1['album_image'] = $file_path . 'images/' . $data1['album_image'];
				$row1['album_image_thumb'] = $file_path . 'images/thumbs/' . $data1['album_image'];

				array_push($jsonObj1, $row1);
			}

			$set['ONLINE_MP3'] = $jsonObj1;
		}
		else {

			$query_rec = "SELECT COUNT(*) as num FROM tbl_mp3
			LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
			WHERE tbl_mp3.`status`='1' AND tbl_category.`status`='1' AND tbl_mp3.`mp3_title` like '%" . addslashes($get_method['search_text']) . "%'
			ORDER BY tbl_mp3.mp3_title";
			$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

			$page_limit = 10;

			$limit = ($get_method['page'] - 1) * $page_limit;

			$jsonObj0 = array();

			$query0 = "SELECT * FROM tbl_mp3
			LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
			WHERE tbl_mp3.`status`='1' AND tbl_category.`status`='1' AND tbl_mp3.`mp3_title` like '%" . addslashes($get_method['search_text']) . "%'
			ORDER BY tbl_mp3.mp3_title LIMIT $limit, $page_limit";

			$sql0 = mysqli_query($mysqli, $query0) or die(mysqli_error());

			while ($data0 = mysqli_fetch_assoc($sql0)) {
				$row0['total_data'] = $total_pages['num'];
				$row0['id'] = $data0['id'];
				$row0['cat_id'] = $data0['cat_id'];
				$row0['mp3_type'] = $data0['mp3_type'];
				$row0['mp3_title'] = $data0['mp3_title'];

				$mp3_file=$data0['mp3_url'];
				if($data0['mp3_type']=='local'){
					$mp3_file=$file_path.'uploads/'.basename($data0['mp3_url']);
				}

				$row0['mp3_url'] = $mp3_file;


				$row0['mp3_thumbnail_b'] = $file_path . 'images/' . $data0['mp3_thumbnail'];
				$row0['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data0['mp3_thumbnail'];

				$row0['mp3_artist'] = $data0['mp3_artist'];
				$row0['mp3_description'] = $data0['mp3_description'];
				$row0['total_rate'] = $data0['total_rate'];
				$row0['rate_avg'] = $data0['rate_avg'];
				$row0['total_views'] = $data0['total_views'];
				$row0['total_download'] = $data0['total_download'];

				$row0['is_favourite'] = is_favourite($data0['id'],$user_id);

				$row0['cid'] = $data0['cid'];
				$row0['category_name'] = $data0['category_name'];
				$row0['category_image'] = $file_path . 'images/' . $data0['category_image'];
				$row0['category_image_thumb'] = $file_path . 'images/thumbs/' . $data0['category_image'];

				array_push($jsonObj0, $row0);
			}

			$row['search_songs'] = $jsonObj0;


			$jsonObj1 = array();

			$query1 = "SELECT * FROM tbl_album
			WHERE tbl_album.`status`='1' AND tbl_album.`album_name` like '%" . addslashes($get_method['search_text']) . "%'
			ORDER BY tbl_album.album_name LIMIT 20";

			$sql1 = mysqli_query($mysqli, $query1) or die(mysqli_error());

			while ($data1 = mysqli_fetch_assoc($sql1)) {
				$row1['aid'] = $data1['aid'];
				$row1['artist_ids'] = $data1['artist_ids'] ? $data1['artist_ids'] : '';
				$row1['album_name'] = $data1['album_name'];
				$row1['album_image'] = $file_path . 'images/' . $data1['album_image'];
				$row1['album_image_thumb'] = $file_path . 'images/thumbs/' . $data1['album_image'];

				array_push($jsonObj1, $row1);
			}

			$row['search_album'] = $jsonObj1;

			$jsonObj2 = array();

			$query2 = "SELECT * FROM tbl_artist WHERE tbl_artist.`artist_name` LIKE '%" . addslashes($get_method['search_text']) . "%'
			ORDER BY tbl_artist.artist_name LIMIT 20";

			$sql2 = mysqli_query($mysqli, $query2) or die(mysqli_error());

			while ($data2 = mysqli_fetch_assoc($sql2)) {
				$row2['id'] = $data2['id'];
				$row2['artist_name'] = $data2['artist_name'];
				$row2['artist_image'] = $file_path . 'images/' . $data2['artist_image'];
				$row2['artist_image_thumb'] = $file_path . 'images/thumbs/' . $data2['artist_image'];

				array_push($jsonObj2, $row2);
			}

			$row['search_artist'] = $jsonObj2;

			$set['ONLINE_MP3'] = $row;
		}

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "song_rating") {

		$user_id = trim($get_method['user_id']);
		$post_id = trim($get_method['post_id']);
		$therate = trim($get_method['rate']);

		$query1 = mysqli_query($mysqli, "SELECT * FROM tbl_rating WHERE `post_id`  = '$post_id' AND `ip` = '$user_id'");
		while ($data1 = mysqli_fetch_assoc($query1)) {
			$rate_db1[] = $data1;
		}
		if (@count($rate_db1) == 0) {

			$data = array(
				'post_id'  => $post_id,
				'rate'  =>  $therate,
				'ip'  => $user_id,
			);

			$qry = Insert('tbl_rating', $data);

			//Total rate result

			$query = mysqli_query($mysqli, "SELECT * FROM tbl_rating WHERE `post_id` = '$post_id' ");

			while ($data = mysqli_fetch_assoc($query)) {
				$rate_db[] = $data;
				$sum_rates[] = $data['rate'];
			}

			if (@count($rate_db)) {
				$rate_times = count($rate_db);
				$sum_rates = array_sum($sum_rates);
				$rate_value = $sum_rates / $rate_times;
				$rate_bg = (($rate_value) / 5) * 100;
			} else {
				$rate_times = 0;
				$rate_value = 0;
				$rate_bg = 0;
			}

			$rate_avg = round($rate_value);

			$sql = "UPDATE tbl_mp3 SET `total_rate`=`total_rate` + 1, `rate_avg`='$rate_avg' WHERE `id`='$post_id'";

			mysqli_query($mysqli, $sql);

			$total_rat_sql = "SELECT * FROM tbl_mp3 WHERE id='" . $post_id . "'";
			$total_rat_res = mysqli_query($mysqli, $total_rat_sql);
			$total_rat_row = mysqli_fetch_assoc($total_rat_res);

			$set['ONLINE_MP3'][] = array('total_rate' => $total_rat_row['total_rate'], 'rate_avg' => $total_rat_row['rate_avg'], 'msg' => $app_lang['rate_success'], 'success' => '1');
		} else {

			$set['ONLINE_MP3'][] = array('msg' => $app_lang['rate_already'], 'success' => '0');
		}

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "song_report") {

		$report = addslashes(trim($get_method['report']));

		$qry1 = "INSERT INTO tbl_reports (`user_id`,`song_id`,`report`) VALUES ('" . $get_method['user_id'] . "','" . $get_method['song_id'] . "','" . $report . "')";

		$result1 = mysqli_query($mysqli, $qry1);


		$set['ONLINE_MP3'][] = array('msg' => $app_lang['report_success'], 'success' => '1');

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "song_suggest") {
		$user_id = $get_method['user_id'];
		$song_title = $get_method['song_title'];
		$message = $get_method['message'];

		if ($_FILES['song_image']['name'] != "") {
			$song_image = rand(0, 99999) . "_" . $_FILES['song_image']['name'];

			//Main Image
			$tpath1 = 'images/' . $song_image;
			$pic1 = compress_image($_FILES["song_image"]["tmp_name"], $tpath1, 80);

			$qry1 = "INSERT INTO tbl_song_suggest (`user_id`,`song_title`,`song_image`,`message`) VALUES ('" . $user_id . "','" . $song_title . "','" . $song_image . "','" . $message . "')";
			$result1 = mysqli_query($mysqli, $qry1);


			$set['ONLINE_MP3'][] = array('msg' => $app_lang['suggest_success'], 'success' => '1');
		} else {
			$qry1 = "INSERT INTO tbl_song_suggest (`user_id`,`song_title`,`message`) VALUES ('" . $user_id . "','" . $song_title . "','" . $message . "')";
			$result1 = mysqli_query($mysqli, $qry1);

			$set['ONLINE_MP3'][] = array('msg' => $app_lang['suggest_success'], 'success' => '1');
		}

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name']=="user_register")
  	{

  		$user_type=trim($get_method['type']); //Google, Normal, Facebook

		$email=addslashes(trim($get_method['email']));
		$auth_id=addslashes(trim($get_method['auth_id']));

		$to = $get_method['email'];
		$recipient_name=$get_method['name'];
		// subject

		$subject = str_replace('###', APP_NAME, $app_lang['register_mail_lbl']);

		if($user_type=='Google' || $user_type=='google'){
			// register with google

			$sql="SELECT * FROM tbl_users WHERE (`email` = '$email' OR `auth_id`='$auth_id') AND `user_type`='Google'";
			$res=mysqli_query($mysqli,$sql);
			$num_rows = mysqli_num_rows($res);
 			$row = mysqli_fetch_assoc($res);

    		if($num_rows == 0)
    		{
    			// data is not available
    			$data = array(
					'user_type'=>'Google',
					'name'  => addslashes(trim($get_method['name'])),
					'email'  =>  addslashes(trim($get_method['email'])),
					'password'  =>  trim($get_method['password']),
					'phone'  =>  addslashes(trim($get_method['phone'])),
					'registered_on'  =>  strtotime(date('d-m-Y h:i:s A')),
					'status'  =>  '1'
				);

				$qry = Insert('tbl_users',$data);

				$user_id=mysqli_insert_id($mysqli);

				$sql_activity_log="SELECT * FROM tbl_active_log WHERE `user_id`='$user_id'";
                $res_activity_log=mysqli_query($mysqli, $sql_activity_log);

                if(mysqli_num_rows($res_activity_log) == 0){
                    // insert active log

                    $data_log = array(
                        'user_id'  =>  $user_id,
                        'date_time'  =>  strtotime(date('d-m-Y h:i:s A'))
                    );

                    $qry = Insert('tbl_active_log',$data_log);

                }
                else{
                    // update active log
                    $data_log = array(
                        'date_time'  =>  strtotime(date('d-m-Y h:i:s A'))
                    );

                    $update=Update('tbl_active_log', $data_log, "WHERE user_id = '$user_id'");
                }

                mysqli_free_result($res_activity_log);

				$message='<div style="background-color: #eee;" align="center"><br />
							  <table style="font-family: OpenSans,sans-serif; color: #666666;" border="0" width="600" cellspacing="0" cellpadding="0" align="center" bgcolor="#FFFFFF">
							    <tbody>
							      <tr>
							        <td colspan="2" bgcolor="#FFFFFF" align="center" ><img src="'.$file_path.'images/'.APP_LOGO.'" alt="logo" style="width:100px;height:auto"/></td>
							      </tr>
							      <br>
							      <br>
							      <tr>
							        <td colspan="2" bgcolor="#FFFFFF" align="center" style="padding-top:25px;">
							          <img src="'.$file_path.'assets/images/thankyoudribble.gif" alt="header" auto-height="100" width="50%"/>
							        </td>
							      </tr>
							      <tr>
							        <td width="600" valign="top" bgcolor="#FFFFFF">
							          <table style="font-family:OpenSans,sans-serif; color: #666666; font-size: 10px; padding: 15px;" border="0" width="100%" cellspacing="0" cellpadding="0" align="left">
							            <tbody>
							              <tr>
							                <td valign="top">
							                  <table border="0" align="left" cellpadding="0" cellspacing="0" style="font-family:OpenSans,sans-serif; color: #666666; font-size: 10px; width:100%;">
							                    <tbody>
							                      <tr>
							                        <td>
							                          <p style="color: #717171; font-size: 24px; margin-top:0px; margin:0 auto; text-align:center;"><strong>'.$app_lang['welcome_lbl'].', '.addslashes(trim($get_method['name'])).'</strong></p>
							                          <br>
							                          <p style="color:#15791c; font-size:18px; line-height:32px;font-weight:500;margin-bottom:30px; margin:0 auto; text-align:center;">'.$app_lang['google_register_msg'].'<br /></p>
							                          <br/>
							                          <p style="color:#999; font-size:17px; line-height:32px;font-weight:500;">'.$app_lang['thank_you_lbl'].' '.APP_NAME.'</p>
							                            </td>
							                      </tr>
							                    </tbody>
							                  </table>
							                </td>
							              </tr>
							            </tbody>
							          </table>
							        </td>
							      </tr>
							      <tr>
							        <td style="color: #262626; padding: 20px 0; font-size: 18px; border-top:5px solid #52bfd3;" colspan="2" align="center" bgcolor="#ffffff">'.$app_lang['email_copyright'].' '.APP_NAME.'.</td>
							      </tr>
							    </tbody>
							  </table>
							</div>';

				$set['ONLINE_MP3'][]=array('user_id' => strval($user_id),'name'=>$get_method['name'],'email'=>$get_method['email'], 'success'=>'1', 'msg' =>'', 'auth_id' => $auth_id);
    		}
    		else{
    			$data = array(
		            'auth_id'  =>  $auth_id,
		        );

		        $update=Update('tbl_users', $data, "WHERE id = '".$row['id']."'");

		        $user_id=$row['id'];

				$sql_activity_log="SELECT * FROM tbl_active_log WHERE `user_id`='$user_id'";
                $res_activity_log=mysqli_query($mysqli, $sql_activity_log);

                if(mysqli_num_rows($res_activity_log) == 0){
                    // insert active log

                    $data_log = array(
                        'user_id'  =>  $user_id,
                        'date_time'  =>  strtotime(date('d-m-Y h:i:s A'))
                    );

                    $qry = Insert('tbl_active_log',$data_log);

                }
                else{
                    // update active log
                    $data_log = array(
                        'date_time'  =>  strtotime(date('d-m-Y h:i:s A'))
                    );

                    $update=Update('tbl_active_log', $data_log, "WHERE user_id = '$user_id'");
                }

                mysqli_free_result($res_activity_log);


				if($row['status']==0)
				{
					$set['ONLINE_MP3'][]=array('msg' =>$app_lang['account_deactive'],'success'=>'0');
				}
				else
				{

					$set['ONLINE_MP3'][]=array('user_id' => $row['id'], 'name'=>$row['name'], 'email'=>$row['email'], 'msg' => $app_lang['login_success'], 'auth_id' => $auth_id, 'success'=>'1');
				}
    		}

		}
		else if($user_type=='Facebook' || $user_type=='facebook'){
			// register with facebook

			$sql="SELECT * FROM tbl_users WHERE (`email` = '$email' OR `auth_id`='$auth_id') AND `user_type`='Facebook'";
			$res=mysqli_query($mysqli,$sql);
			$num_rows = mysqli_num_rows($res);
 			$row = mysqli_fetch_assoc($res);

    		if($num_rows == 0)
    		{
    			// data is not available
    			$data = array(
					'user_type'=>'Facebook',
					'name'  => addslashes(trim($get_method['name'])),
					'email'  =>  addslashes(trim($get_method['email'])),
					'password'  =>  trim($get_method['password']),
					'phone'  =>  addslashes(trim($get_method['phone'])),
					'registered_on'  =>  strtotime(date('d-m-Y h:i:s A')),
					'status'  =>  '1'
				);

				$qry = Insert('tbl_users',$data);

				$user_id=mysqli_insert_id($mysqli);

				$sql_activity_log="SELECT * FROM tbl_active_log WHERE `user_id`='$user_id'";
                $res_activity_log=mysqli_query($mysqli, $sql_activity_log);

                if(mysqli_num_rows($res_activity_log) == 0){
                    // insert active log

                    $data_log = array(
                        'user_id'  =>  $user_id,
                        'date_time'  =>  strtotime(date('d-m-Y h:i:s A'))
                    );

                    $qry = Insert('tbl_active_log',$data_log);

                }
                else{
                    // update active log
                    $data_log = array(
                        'date_time'  =>  strtotime(date('d-m-Y h:i:s A'))
                    );

                    $update=Update('tbl_active_log', $data_log, "WHERE user_id = '$user_id'");
                }

                mysqli_free_result($res_activity_log);

				$message='<div style="background-color: #eee;" align="center"><br />
							  <table style="font-family: OpenSans,sans-serif; color: #666666;" border="0" width="600" cellspacing="0" cellpadding="0" align="center" bgcolor="#FFFFFF">
							    <tbody>
							      <tr>
							        <td colspan="2" bgcolor="#FFFFFF" align="center" ><img src="'.$file_path.'images/'.APP_LOGO.'" alt="logo" style="width:100px;height:auto"/></td>
							      </tr>
							      <br>
							      <br>
							      <tr>
							        <td colspan="2" bgcolor="#FFFFFF" align="center" style="padding-top:25px;">
							          <img src="'.$file_path.'assets/images/thankyoudribble.gif" alt="header" auto-height="100" width="50%"/>
							        </td>
							      </tr>
							      <tr>
							        <td width="600" valign="top" bgcolor="#FFFFFF">
							          <table style="font-family:OpenSans,sans-serif; color: #666666; font-size: 10px; padding: 15px;" border="0" width="100%" cellspacing="0" cellpadding="0" align="left">
							            <tbody>
							              <tr>
							                <td valign="top">
							                  <table border="0" align="left" cellpadding="0" cellspacing="0" style="font-family:OpenSans,sans-serif; color: #666666; font-size: 10px; width:100%;">
							                    <tbody>
							                      <tr>
							                        <td>
							                          <p style="color: #717171; font-size: 24px; margin-top:0px; margin:0 auto; text-align:center;"><strong>'.$app_lang['welcome_lbl'].', '.addslashes(trim($get_method['name'])).'</strong></p>
							                          <br>
							                          <p style="color:#15791c; font-size:18px; line-height:32px;font-weight:500;margin-bottom:30px; margin:0 auto; text-align:center;">'.$app_lang['facebook_register_msg'].'<br /></p>
							                          <br/>
							                          <p style="color:#999; font-size:17px; line-height:32px;font-weight:500;">'.$app_lang['thank_you_lbl'].' '.APP_NAME.'</p>
							                            </td>
							                      </tr>
							                    </tbody>
							                  </table>
							                </td>
							              </tr>
							            </tbody>
							          </table>
							        </td>
							      </tr>
							      <tr>
							        <td style="color: #262626; padding: 20px 0; font-size: 18px; border-top:5px solid #52bfd3;" colspan="2" align="center" bgcolor="#ffffff">'.$app_lang['email_copyright'].' '.APP_NAME.'.</td>
							      </tr>
							    </tbody>
							  </table>
							</div>';

				$set['ONLINE_MP3'][]=array('user_id' => strval($user_id),'name'=>$get_method['name'],'email'=>$get_method['email'], 'success'=>'1', 'msg' =>'', 'auth_id' => $auth_id);
    		}
    		else{
    			$data = array(
		            'auth_id'  =>  $auth_id,
		        );

		        $update=Update('tbl_users', $data, "WHERE id = '".$row['id']."'");

		        $user_id=$row['id'];

				$sql_activity_log="SELECT * FROM tbl_active_log WHERE `user_id`='$user_id'";
                $res_activity_log=mysqli_query($mysqli, $sql_activity_log);

                if(mysqli_num_rows($res_activity_log) == 0){
                    // insert active log

                    $data_log = array(
                        'user_id'  =>  $user_id,
                        'date_time'  =>  strtotime(date('d-m-Y h:i:s A'))
                    );

                    $qry = Insert('tbl_active_log',$data_log);

                }
                else{
                    // update active log
                    $data_log = array(
                        'date_time'  =>  strtotime(date('d-m-Y h:i:s A'))
                    );

                    $update=Update('tbl_active_log', $data_log, "WHERE user_id = '$user_id'");
                }

                mysqli_free_result($res_activity_log);

				if($row['status']==0)
				{
					$set['ONLINE_MP3'][]=array('msg' =>$app_lang['account_deactive'],'success'=>'0');
				}
				else
				{
					$set['ONLINE_MP3'][]=array('user_id' => $row['id'], 'name'=>$row['name'], 'email'=>$row['email'], 'msg' => $app_lang['login_success'], 'auth_id' => $auth_id, 'success'=>'1');
				}
    		}

		}
		else{
			// for normal registration

			$sql = "SELECT * FROM tbl_users WHERE email = '$email'";
			$result = mysqli_query($mysqli, $sql);
			$row = mysqli_fetch_assoc($result);

			if (!filter_var($get_method['email'], FILTER_VALIDATE_EMAIL))
			{
				$set['ONLINE_MP3'][]=array('msg' => $app_lang['invalid_email_format'],'success'=>'0');
			}
			else if($row['email']!="")
			{
				$set['ONLINE_MP3'][]=array('msg' => $app_lang['email_exist'],'success'=>'0');
			}
			else
			{
				$data = array(
					'user_type'=>'Normal',
					'name'  => addslashes(trim($get_method['name'])),
					'email'  =>  addslashes(trim($get_method['email'])),
					'password'  =>  md5(trim($get_method['password'])),
					'phone'  =>  addslashes(trim($get_method['phone'])),
					'registered_on'  =>  strtotime(date('d-m-Y h:i:s A')),
					'status'  =>  '1'
				);

				$qry = Insert('tbl_users',$data);

				$message='<div style="background-color: #eee;" align="center"><br />
							  <table style="font-family: OpenSans,sans-serif; color: #666666;" border="0" width="600" cellspacing="0" cellpadding="0" align="center" bgcolor="#FFFFFF">
							    <tbody>
							      <tr>
							        <td colspan="2" bgcolor="#FFFFFF" align="center" ><img src="'.$file_path.'images/'.APP_LOGO.'" alt="logo" style="width:100px;height:auto"/></td>
							      </tr>
							      <br>
							      <br>
							      <tr>
							        <td colspan="2" bgcolor="#FFFFFF" align="center" style="padding-top:25px;">
							          <img src="'.$file_path.'assets/images/thankyoudribble.gif" alt="header" auto-height="100" width="50%"/>
							        </td>
							      </tr>
							      <tr>
							        <td width="600" valign="top" bgcolor="#FFFFFF">
							          <table style="font-family:OpenSans,sans-serif; color: #666666; font-size: 10px; padding: 15px;" border="0" width="100%" cellspacing="0" cellpadding="0" align="left">
							            <tbody>
							              <tr>
							                <td valign="top">
							                  <table border="0" align="left" cellpadding="0" cellspacing="0" style="font-family:OpenSans,sans-serif; color: #666666; font-size: 10px; width:100%;">
							                    <tbody>
							                      <tr>
							                        <td>
							                        	<p style="color: #717171; font-size: 24px; margin-top:0px; margin:0 auto; text-align:center;"><strong>'.$app_lang['welcome_lbl'].', '.addslashes(trim($get_method['name'])).'</strong></p>
							                          	<br>
							                          	<p style="color:#15791c; font-size:18px; line-height:32px;font-weight:500;margin-bottom:30px; margin:0 auto; text-align:center;">'.$app_lang['normal_register_msg'].'<br /></p>
							                          	<br/>
							                          	<p style="color:#999; font-size:17px; line-height:32px;font-weight:500;">'.$app_lang['thank_you_lbl'].' '.APP_NAME.'</p>
							                            </td>
							                      </tr>
							                    </tbody>
							                  </table>
							                </td>
							              </tr>
							            </tbody>
							          </table>
							        </td>
							      </tr>
							      <tr>
							        <td style="color: #262626; padding: 20px 0; font-size: 18px; border-top:5px solid #52bfd3;" colspan="2" align="center" bgcolor="#ffffff">'.$app_lang['email_copyright'].' '.APP_NAME.'.</td>
							      </tr>
							    </tbody>
							  </table>
							</div>';

				$set['ONLINE_MP3'][]=array('msg' => $app_lang['register_success'],'success'=>'1');
			}

		}

		send_email($to,$recipient_name,$subject,$message);

		header( 'Content-Type: application/json; charset=utf-8' );
	    echo $val= str_replace('\\/', '/', json_encode($set,JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
  	}
	else if($get_method['method_name']=="user_login")
	{

		$email= htmlentities(trim($get_method['email']));
		$password = htmlentities(trim($get_method['password']));

		$auth_id = htmlentities(trim($get_method['auth_id']));

		$user_type = htmlentities(trim($get_method['type']));

		if($user_type=='normal' OR $user_type=='Normal'){

			// simple login
			$qry = "SELECT * FROM tbl_users WHERE email = '$email' AND (`user_type`='Normal' OR `user_type`='normal') AND `id` <> 0";
			$result = mysqli_query($mysqli,$qry);
			$num_rows = mysqli_num_rows($result);

			if($num_rows > 0){
				$row = mysqli_fetch_assoc($result);

				if($row['status']==1){
					if($row['password']==md5($password)){

						$user_id=$row['id'];

						$sql_activity_log="SELECT * FROM tbl_active_log WHERE `user_id`='$user_id'";
	                    $res_activity_log=mysqli_query($mysqli, $sql_activity_log);

	                    if(mysqli_num_rows($res_activity_log) == 0){
	                        // insert active log

	                        $data_log = array(
	                            'user_id'  =>  $user_id,
	                            'date_time'  =>  strtotime(date('d-m-Y h:i:s A'))
	                        );

	                        $qry = Insert('tbl_active_log',$data_log);

	                    }
	                    else{
	                        // update active log
	                        $data_log = array(
	                            'date_time'  =>  strtotime(date('d-m-Y h:i:s A'))
	                        );

	                        $update=Update('tbl_active_log', $data_log, "WHERE user_id = '$user_id'");
	                    }

	                    mysqli_free_result($res_activity_log);

						$set['ONLINE_MP3'][]=array('user_id' => $row['id'], 'name'=>$row['name'], 'email'=>$row['email'], 'msg' => $app_lang['login_success'], 'auth_id' => '', 'success'=>'1');
					}
					else{
						// invalid password
						$set['ONLINE_MP3'][]=array('msg' =>$app_lang['invalid_password'],'success'=>'0');
					}
				}
				else{
					// account is deactivated
					$set['ONLINE_MP3'][]=array('msg' =>$app_lang['account_deactive'],'success'=>'0');
				}

			}
			else{
				// email not found
				$set['ONLINE_MP3'][]=array('msg' =>$app_lang['email_not_found'],'success'=>'0');
			}
		}
		else if($user_type=='google' OR $user_type=='Google'){

			// login with google

			$sql = "SELECT * FROM tbl_users WHERE (`email` = '$email' OR `auth_id`='$auth_id') AND (`user_type`='Google' OR `user_type`='google')";

			$res=mysqli_query($mysqli, $sql);

 			if(mysqli_num_rows($res) > 0){
 				$row = mysqli_fetch_assoc($res);

 				if($row['status']==0){
					$set['ONLINE_MP3'][]=array('msg' => $app_lang['account_deactive'],'success'=>'0');
				}
				else
				{
					$user_id=$row['id'];

					$sql_activity_log="SELECT * FROM tbl_active_log WHERE `user_id`='$user_id'";
                    $res_activity_log=mysqli_query($mysqli, $sql_activity_log);

                    if(mysqli_num_rows($res_activity_log) == 0){
                        // insert active log

                        $data_log = array(
                            'user_id'  =>  $user_id,
                            'date_time'  =>  strtotime(date('d-m-Y h:i:s A'))
                        );

                        $qry = Insert('tbl_active_log',$data_log);

                    }
                    else{
                        // update active log
                        $data_log = array(
                            'date_time'  =>  strtotime(date('d-m-Y h:i:s A'))
                        );

                        $update=Update('tbl_active_log', $data_log, "WHERE user_id = '$user_id'");
                    }

                    mysqli_free_result($res_activity_log);

					$set['ONLINE_MP3'][]=array('user_id' => $row['id'], 'name'=>$row['name'], 'email'=>$row['email'], 'msg' => $app_lang['login_success'], 'auth_id' => $auth_id, 'success'=>'1');

					$data = array(
			            'auth_id'  =>  $auth_id
			        );

			        $updatePlayerID=Update('tbl_users', $data, "WHERE `id` = '".$row['id']."'");
				}

 			}
 			else{
 				$set['ONLINE_MP3'][]=array('msg' => $app_lang['email_not_found'],'success'=>'0');
 			}
		}
		else if($user_type=='facebook' OR $user_type=='Facebook'){

			// login with google

			$sql = "SELECT * FROM tbl_users WHERE (`email` = '$email' OR `auth_id`='$auth_id') AND (`user_type`='Facebook' OR `user_type`='facebook')";

			$res=mysqli_query($mysqli, $sql);

 			if(mysqli_num_rows($res) > 0){
 				$row = mysqli_fetch_assoc($res);

 				if($row['status']==0){
					$set['ONLINE_MP3'][]=array('msg' => $app_lang['account_deactive'],'success'=>'0');
				}
				else
				{

					$user_id=$row['id'];

					$sql_activity_log="SELECT * FROM tbl_active_log WHERE `user_id`='$user_id'";
                    $res_activity_log=mysqli_query($mysqli, $sql_activity_log);

                    if(mysqli_num_rows($res_activity_log) == 0){
                        // insert active log

                        $data_log = array(
                            'user_id'  =>  $user_id,
                            'date_time'  =>  strtotime(date('d-m-Y h:i:s A'))
                        );

                        $qry = Insert('tbl_active_log',$data_log);

                    }
                    else{
                        // update active log
                        $data_log = array(
                            'date_time'  =>  strtotime(date('d-m-Y h:i:s A'))
                        );

                        $update=Update('tbl_active_log', $data_log, "WHERE user_id = '$user_id'");
                    }

                    mysqli_free_result($res_activity_log);

					$set['ONLINE_MP3'][]=array('user_id' => $row['id'], 'name'=>$row['name'], 'email'=>$row['email'], 'msg' => $app_lang['login_success'], 'auth_id' => $auth_id, 'success'=>'1');

					$data = array(
			            'auth_id'  =>  $auth_id
			        );

			        $updatePlayerID=Update('tbl_users', $data, "WHERE `id` = '".$row['id']."'");
				}

 			}
 			else{
 				$set['ONLINE_MP3'][]=array('msg' => $app_lang['email_not_found'],'success'=>'0');
 			}

		}
		else{
			$set['ONLINE_MP3'][]=array('success'=>'0', 'msg' =>$app_lang['invalid_user_type']);
		}

		header( 'Content-Type: application/json; charset=utf-8' );
		echo $val= str_replace('\\/', '/', json_encode($set,JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name'] == "user_profile")
	{
		$jsonObj= array();

		$user_id=$get_method['user_id'];

		$qry = "SELECT * FROM tbl_users WHERE id = '$user_id'";

		$result = mysqli_query($mysqli,$qry);

		$row = mysqli_fetch_assoc($result);

		$data['success']="1";
		$data['user_id'] = $row['id'];
		$data['name'] = $row['name'];
		$data['email'] = ($row['email']!='') ? $row['email'] : '';
		$data['phone'] = ($row['phone']!='') ? $row['phone'] : '';

		array_push($jsonObj,$data);

		$set['ONLINE_MP3'] = $jsonObj;

		header( 'Content-Type: application/json; charset=utf-8' );
		echo $val= str_replace('\\/', '/', json_encode($set,JSON_UNESCAPED_UNICODE));
		die();
	}
	else if($get_method['method_name'] == "user_profile_update")
	{

		$jsonObj= array();

		$sql = "SELECT * FROM tbl_users WHERE `id` = '".$get_method['user_id']."'";
		$result = mysqli_query($mysqli,$sql);
		$row = mysqli_fetch_assoc($result);

		if (!filter_var($get_method['email'], FILTER_VALIDATE_EMAIL))
		{
			$set['ONLINE_MP3'][]=array('msg' => $app_lang['invalid_email_format'],'success'=>'0');

			header( 'Content-Type: application/json; charset=utf-8' );
			$json = json_encode($set);
			echo $json;
			 exit;
		}
		else if($row['email']==$get_method['email'] AND $row['id']!=$get_method['user_id'])
		{
			$set['ONLINE_MP3'][]=array('msg' => $app_lang['email_exist'],'success'=>'0');


		}else{

			$data = array(
				'name'  =>  cleanInput($get_method['name']),
				'email'  =>  trim($get_method['email']),
				'phone'  =>  cleanInput($get_method['phone']),
			);

			if($get_method['password']!=""){
				$data = array_merge($data, array("password" => md5(trim($get_method['password']))));
			}

			$user_edit=Update('tbl_users', $data, "WHERE id = '".$get_method['user_id']."'");

			$set['ONLINE_MP3'][] = array('msg' => $app_lang['update_success'], 'success' => '1');
		}


		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else if($get_method['method_name']=="forgot_pass")
  	{
  		$email=addslashes(trim($get_method['user_email']));

		$qry = "SELECT * FROM tbl_users WHERE email = '$email' AND `user_type`='Normal' AND `id` <> 0";
		$result = mysqli_query($mysqli,$qry);
		$row = mysqli_fetch_assoc($result);

		if($row['email']!="")
		{
			$password=generateRandomPassword(7);

			$new_password=md5($password);

			$to = $row['email'];
			$recipient_name=$row['name'];
			// subject
			$subject = str_replace('###', APP_NAME, $app_lang['forgot_password_sub_lbl']);

			$message='<div style="background-color: #f9f9f9;" align="center"><br />
					  <table style="font-family: OpenSans,sans-serif; color: #666666;" border="0" width="600" cellspacing="0" cellpadding="0" align="center" bgcolor="#FFFFFF">
					    <tbody>
					      <tr>
					        <td colspan="2" bgcolor="#FFFFFF" align="center"><img src="'.$file_path.'images/'.APP_LOGO.'" alt="header" style="width:100px;height:auto"/></td>
					      </tr>
					      <tr>
					        <td width="600" valign="top" bgcolor="#FFFFFF"><br>
					          <table style="font-family:OpenSans,sans-serif; color: #666666; font-size: 10px; padding: 15px;" border="0" width="100%" cellspacing="0" cellpadding="0" align="left">
					            <tbody>
					              <tr>
					                <td valign="top"><table border="0" align="left" cellpadding="0" cellspacing="0" style="font-family:OpenSans,sans-serif; color: #666666; font-size: 10px; width:100%;">
					                    <tbody>
					                      <tr>
					                        <td>
					                        	<p style="color: #262626; font-size: 24px; margin-top:0px;"><strong>'.$app_lang['dear_lbl'].' '.$row['name'].'</strong></p>
					                          <p style="color:#262626; font-size:20px; line-height:32px;font-weight:500;margin-top:5px;"><br>'.$app_lang['your_password_lbl'].': <span style="font-weight:400;">'.$password.'</span></p>
					                          <p style="color:#262626; font-size:17px; line-height:32px;font-weight:500;margin-bottom:30px;">'.$app_lang['thank_you_lbl'].' '.APP_NAME.'</p>

					                        </td>
					                      </tr>
					                    </tbody>
					                  </table></td>
					              </tr>

					            </tbody>
					          </table></td>
					      </tr>
					      <tr>
					        <td style="color: #262626; padding: 20px 0; font-size: 18px; border-top:5px solid #52bfd3;" colspan="2" align="center" bgcolor="#ffffff">'.$app_lang['email_copyright'].' '.APP_NAME.'.</td>
					      </tr>
					    </tbody>
					  </table>
					</div>';

			send_email($to,$recipient_name,$subject,$message);

			$sql="UPDATE tbl_users SET `password`='$new_password' WHERE `id`='".$row['id']."'";
	      	mysqli_query($mysqli,$sql);

			$set['ONLINE_MP3'][]=array('msg' => $app_lang['password_sent_mail'],'success'=>'1');
		}
		else
		{

			$set['ONLINE_MP3'][]=array('msg' => $app_lang['email_not_found'],'success'=>'0');

		}

		header( 'Content-Type: application/json; charset=utf-8' );
		echo $val= str_replace('\\/', '/', json_encode($set,JSON_UNESCAPED_UNICODE));
		die();

	}

	else if($get_method['method_name']=="favourite_post")
	{
		$jsonObj= array();

		$post_id=$get_method['post_id'];
		$type=$get_method['type'];
		$user_id=$get_method['user_id'];

		$sql="SELECT * FROM tbl_favourite WHERE `post_id`='$post_id' AND `user_id`='$user_id' AND `type`='$type'";
		$res=mysqli_query($mysqli, $sql);

		if(mysqli_num_rows($res) == 0){

			$data = array(
                'post_id'  =>  $post_id,
                'user_id'  =>  $user_id,
                'type'  =>  $type,
                'created_at'  =>  strtotime(date('d-m-Y h:i:s A')),
            );

            $qry = Insert('tbl_favourite',$data);

			$set['ONLINE_MP3'][]=array('msg'=>$app_lang['favourite_success'],'success'=>'1');
		}
		else{
			$deleteSql="DELETE FROM tbl_favourite WHERE `post_id`='$post_id' AND `user_id`='$user_id' AND `type`='$type'";

			if(mysqli_query($mysqli, $deleteSql)){
				$set['ONLINE_MP3'][]=array('msg'=>$app_lang['favourite_remove_success'],'success'=>'0');
			}
			else{
				$set['ONLINE_MP3'][]=array('msg'=>$app_lang['favourite_remove_error'],'success'=>'1');
			}
		}

		header( 'Content-Type: application/json; charset=utf-8' );
		$json = json_encode($set);
		echo $json;
		exit;

	}
	else if($get_method['method_name']=="get_favourite_post")
	{
		$jsonObj= array();

		$type=trim($get_method['type']);
		$user_id=trim($get_method['user_id']);

		$page_limit = 10;

		switch ($type) {
			case 'song':
				{
					$query_rec="SELECT COUNT(*) as num FROM tbl_mp3
								LEFT JOIN tbl_favourite ON tbl_mp3.`id`= tbl_favourite.`post_id`
								LEFT JOIN tbl_category ON tbl_mp3.`cat_id`=tbl_category.`cid`
								WHERE tbl_mp3.`status`='1' AND tbl_category.`status`='1' AND tbl_favourite.`user_id`='$user_id' AND tbl_favourite.`type`='$type' ORDER BY tbl_favourite.`id` DESC";

					$total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query_rec));

					$limit = ($get_method['page'] - 1) * $page_limit;

					$jsonObj = array();

					$query="SELECT tbl_mp3.*, tbl_category.* FROM tbl_mp3
							LEFT JOIN tbl_favourite ON tbl_mp3.`id`= tbl_favourite.`post_id`
							LEFT JOIN tbl_category ON tbl_mp3.`cat_id`=tbl_category.`cid`
							WHERE tbl_mp3.`status`='1' AND tbl_category.`status`='1' AND tbl_favourite.`user_id`='$user_id' AND tbl_favourite.`type`='$type' ORDER BY tbl_favourite.`id` DESC LIMIT $limit, $page_limit";

					$sql = mysqli_query($mysqli, $query) or die(mysqli_error($mysqli));

					while ($data = mysqli_fetch_assoc($sql)) {

						$row['total_songs'] = $total_pages['num'];
						$row['id'] = $data['id'];
						$row['cat_id'] = $data['cat_id'];
						$row['mp3_type'] = $data['mp3_type'];
						$row['mp3_title'] = $data['mp3_title'];

						$mp3_file=$data['mp3_url'];
						if($data['mp3_type']=='local'){
							$mp3_file=$file_path.'uploads/'.basename($data['mp3_url']);
						}

						$row['mp3_url'] = $mp3_file;

						$row['mp3_thumbnail_b'] = $file_path . 'images/' . $data['mp3_thumbnail'];
						$row['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data['mp3_thumbnail'];

						$row['mp3_artist'] = $data['mp3_artist'];
						$row['mp3_description'] = $data['mp3_description'];
						$row['total_rate'] = $data['total_rate'];
						$row['rate_avg'] = $data['rate_avg'];
						$row['total_views'] = $data['total_views'];
						$row['total_download'] = $data['total_download'];

						$row['is_favourite'] = is_favourite($data['id'],$user_id);

						$row['cid'] = $data['cid'];
						$row['category_name'] = $data['category_name'];
						$row['category_image'] = $file_path . 'images/' . $data['category_image'];
						$row['category_image_thumb'] = $file_path . 'images/thumbs/' . $data['category_image'];

						array_push($jsonObj, $row);
					}
				}
				break;

			default:
				{
				}
				break;
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header( 'Content-Type: application/json; charset=utf-8' );
		$json = json_encode($set);
		echo $json;
		exit;
	}

	else if($get_method['method_name']=="get_recent_songs")
	{
		$jsonObj= array();

		$ids=trim($get_method['songs_ids']);

		$user_id=trim($get_method['user_id']);

		$page_limit = 10;

		$query_rec = "SELECT COUNT(*) as num FROM tbl_mp3
				LEFT JOIN tbl_category ON tbl_mp3.`cat_id`=tbl_category.`cid`
				WHERE tbl_mp3.`status`='1' AND tbl_category.`status`='1' AND tbl_mp3.`id` IN ($ids) ORDER BY tbl_mp3.`id` DESC";

		$total_pages = mysqli_fetch_array(mysqli_query($mysqli,$query_rec));

		$limit=($get_method['page']-1) * $page_limit;

		$jsonObj= array();

		$query="SELECT * FROM tbl_mp3
				LEFT JOIN tbl_category ON tbl_mp3.`cat_id`= tbl_category.`cid`
				WHERE tbl_mp3.`status`='1' AND tbl_category.`status`='1' AND tbl_mp3.`id` IN ($ids) ORDER BY tbl_mp3.`id` DESC LIMIT $limit, $page_limit";

		$sql = mysqli_query($mysqli,$query)or die(mysqli_error());

		while ($data = mysqli_fetch_assoc($sql)) {

			$row['total_songs'] = $total_pages['num'];
			$row['id'] = $data['id'];
			$row['cat_id'] = $data['cat_id'];
			$row['mp3_type'] = $data['mp3_type'];
			$row['mp3_title'] = $data['mp3_title'];

			$mp3_file=$data['mp3_url'];

			if($data['mp3_type']=='local'){
				$mp3_file=$file_path.'uploads/'.basename($data['mp3_url']);
			}

			$row['mp3_url'] = $mp3_file;

			$row['mp3_thumbnail_b'] = $file_path . 'images/' . $data['mp3_thumbnail'];
			$row['mp3_thumbnail_s'] = $file_path . 'images/thumbs/' . $data['mp3_thumbnail'];

			$row['mp3_artist'] = $data['mp3_artist'];
			$row['mp3_description'] = $data['mp3_description'];
			$row['total_rate'] = $data['total_rate'];
			$row['rate_avg'] = $data['rate_avg'];
			$row['total_views'] = $data['total_views'];
			$row['total_download'] = $data['total_download'];

			$row['is_favourite'] = is_favourite($data['id'],$user_id);

			$row['cid'] = $data['cid'];
			$row['category_name'] = $data['category_name'];
			$row['category_image'] = $file_path . 'images/' . $data['category_image'];
			$row['category_image_thumb'] = $file_path . 'images/thumbs/' . $data['category_image'];

			array_push($jsonObj, $row);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header( 'Content-Type: application/json; charset=utf-8' );
		$json = json_encode($set);
		echo $json;
		exit;
	}

	else if($get_method['method_name'] == "app_details") {
		$jsonObj = array();

		$query = "SELECT * FROM tbl_settings WHERE id='1'";
		$sql = mysqli_query($mysqli, $query);

		while ($data = mysqli_fetch_assoc($sql)) {

			$row['app_name'] = $data['app_name'];
			$row['app_logo'] = $data['app_logo'];
			$row['app_version'] = $data['app_version'];
			$row['app_author'] = $data['app_author'];
			$row['app_contact'] = $data['app_contact'];
			$row['app_email'] = $data['app_email'];
			$row['app_website'] = $data['app_website'];
			$row['app_download_url'] = $data['app_download_url'];
			$row['app_description'] = stripslashes($data['app_description']);
			$row['app_developed_by'] = $data['app_developed_by'];

			$row['app_privacy_policy'] = stripslashes($data['app_privacy_policy']);

			$row['package_name'] = $data['package_name'];
			$row['publisher_id'] = $data['publisher_id'];

			$row['interstital_ad'] = $data['interstital_ad'];
			$row['interstital_ad_type'] = $data['interstital_ad_type'];

			$row['interstital_ad_id'] = ($data['interstital_ad_type'] == 'facebook') ? $data['interstital_facebook_id'] : $data['interstital_ad_id'];

			$row['interstital_ad_click'] = $data['interstital_ad_click'];

			$row['banner_ad'] = $data['banner_ad'];
			$row['banner_ad_type'] = $data['banner_ad_type'];

			$row['banner_ad_id'] = ($data['banner_ad_type'] == 'facebook') ? $data['banner_facebook_id'] : $data['banner_ad_id'];

			$row['native_ad'] = $data['native_ad'];
			$row['native_ad_type'] = $data['native_ad_type'];

			$row['native_ad_id'] = ($data['native_ad_type'] == 'facebook') ? $data['native_facebook_id'] : $data['native_ad_id'];

			$row['native_position'] = $data['native_position'];

			$row['song_download'] = $data['song_download'];

			$row['app_update_status'] = $data['app_update_status'];
            $row['app_new_version'] = $data['app_new_version'];
            $row['app_update_desc'] = stripslashes($data['app_update_desc']);
            $row['app_redirect_url'] = $data['app_redirect_url'];
            $row['cancel_update_status'] = $data['cancel_update_status'];

			array_push($jsonObj, $row);
		}

		$set['ONLINE_MP3'] = $jsonObj;

		header('Content-Type: application/json; charset=utf-8');
		echo $val = str_replace('\\/', '/', json_encode($set, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
		die();
	}
	else {
		$get_method = checkSignSalt($_POST['data']);
	}
?>
