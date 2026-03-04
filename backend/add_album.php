<?php
		
	$page_title=(isset($_GET['album_id'])) ? '编辑专辑' : '添加专辑';

	include("includes/header.php");
	require("includes/function.php");
	require("language/language.php");

	require_once("thumbnail_images.class.php");

	$art_qry="SELECT * FROM tbl_artist ORDER BY artist_name";
	$art_result=mysqli_query($mysqli,$art_qry);
	 
	if(isset($_POST['submit']) and isset($_GET['add']))
	{

		try {

			$ext = pathinfo($_FILES['album_image']['name'], PATHINFO_EXTENSION);

	        $album_image=rand(0,99999)."_album.".$ext;

	        //Main Image
	        $tpath1='images/'.$album_image;   

	        if($ext!='png')  {
	          $pic1=compress_image($_FILES["album_image"]["tmp_name"], $tpath1, 80);
	        }
	        else{
	          $tmp = $_FILES['album_image']['tmp_name'];
	          move_uploaded_file($tmp, $tpath1);
	        }

	        //Thumb Image 
	        $thumbpath='images/thumbs/'.$album_image;   
	        $thumb_pic1=create_thumb_image($tpath1,$thumbpath,'300','300'); 


	        $data = array( 
	            'album_name'  =>  cleanInput($_POST['album_name']),
	            'album_image'  =>  $album_image,
	            'artist_ids'  => implode(',', $_POST['artist_ids'])
	        );    

	        $qry = Insert('tbl_album',$data);
			
		} catch (Exception $e) {
			print_r($e);
		}

		$_SESSION['msg']="10";
        $_SESSION['class']='success';
        header( "Location:manage_album.php");
        exit;
	}
	
	if(isset($_GET['album_id']))
	{
			 
		$qry="SELECT * FROM tbl_album where aid='".$_GET['album_id']."'";
		$result=mysqli_query($mysqli,$qry);
		$row=mysqli_fetch_assoc($result);

	}
	if(isset($_POST['submit']) and isset($_POST['album_id']))
	{
		 
		if($_FILES['album_image']['name']!="")
        {
            if($row['album_image']!="")
            {
                unlink('images/thumbs/'.$row['album_image']);
                unlink('images/'.$row['album_image']);
            }

            $ext = pathinfo($_FILES['album_image']['name'], PATHINFO_EXTENSION);

            $album_image=rand(0,99999)."_album.".$ext;

            //Main Image
            $tpath1='images/'.$album_image;   

            if($ext!='png')  {
              $pic1=compress_image($_FILES["album_image"]["tmp_name"], $tpath1, 80);
            }
            else{
              $tmp = $_FILES['album_image']['tmp_name'];
              move_uploaded_file($tmp, $tpath1);
            }

            //Thumb Image 
            $thumbpath='images/thumbs/'.$album_image;   
            $thumb_pic1=create_thumb_image($tpath1,$thumbpath,'300','300');

        }
        else{
            $album_image=$row['album_image'];
        }
          
        $data = array( 
            'album_name'  =>  cleanInput($_POST['album_name']),
            'album_image'  =>  $album_image,
            'artist_ids'  => implode(',', $_POST['artist_ids'])
        );

        $edit=Update('tbl_album', $data, "WHERE aid = '".$_POST['album_id']."'");
        echo mysqli_error($mysqli);

        $_SESSION['msg']="11";
        $_SESSION['class']='success'; 
        
        if(isset($_GET['redirect'])){
          header("Location:".$_GET['redirect']);
        }
        else{
          header( "Location:add_album.php?album_id=".$_POST['album_id']);
        }
        exit;
	}

?>
<div class="row">
	<div class="col-md-12">
		<div class="card">
			<div class="page_title_block">
				<div class="col-md-5 col-xs-12">
					<div class="page_title"><?=$page_title?></div>
				</div>
			</div>
			<div class="clearfix"></div>

			<div class="card-body mrg_bottom"> 
				<form action="" method="post" class="form form-horizontal" enctype="multipart/form-data">
					<input  type="hidden" name="album_id" value="<?php echo $_GET['album_id'];?>" />

					<div class="section">
						<div class="section-body">
					 
							<div class="form-group">
								<label class="col-md-3 control-label">专辑名称 :-</label>
								<div class="col-md-6">
									<input type="text" name="album_name" id="album_name" value="<?php if(isset($_GET['album_id'])){echo $row['album_name'];}?>" class="form-control" required>
								</div>
							</div>
							<div class="form-group">
								<label class="col-md-3 control-label">歌手 :-</label>
								<div class="col-md-6">
									<select name="artist_ids[]" id="artist_ids" class="select2 form-control" required multiple="multiple">
										<?php
											while($art_row=mysqli_fetch_array($art_result))
											{
										?>                       
										<option value="<?php echo $art_row['id'];?>" <?php if(in_array($art_row['id'], explode(",",$row['artist_ids']))){?>selected<?php }?>><?php echo $art_row['artist_name'];?></option>                           
										<?php
											}
										?>
									</select>
								</div>
							</div>
							<div class="form-group">
								<label class="col-md-3 control-label">专辑图片 :-
									<p class="control-label-help">(推荐尺寸: 300x300,400x400 或者正方形图片)</p>
								</label>
								<div class="col-md-6">
				                  <div class="fileupload_block">
				                    <input type="file" name="album_image" value="fileupload" accept=".png, .jpg, .JPG .PNG" onchange="fileValidation()" id="fileupload">
				                    <?php if(isset($_GET['album_id'])) {?>
				                      <div class="fileupload_img" id="uploadPreview"><img type="image" src="images/<?php echo $row['album_image'];?>" alt="image" style="width: 120px;height: 120px;"/></div>
				                    <?php }else{?>
				                      <div class="fileupload_img" id="uploadPreview"><img type="image" src="assets/images/square.jpg" alt="image" style="width: 120px;height: 120px" /></div>
				                      <?php } ?>
				                       
				                  </div>
				                </div>
							</div>
							<div class="form-group">
								<div class="col-md-9 col-md-offset-3">
									<button type="submit" name="submit" class="btn btn-primary">保存</button>
								</div>
							</div>
						</div>
					</div>
				</form>
			</div>
		</div>
	</div>
</div>
				
<?php include("includes/footer.php");?>

<script type="text/javascript">
  function fileValidation(){
    var fileInput = document.getElementById('fileupload');
    var filePath = fileInput.value;
    var allowedExtensions = /(\.png|.PNG|.jpg|.JPG)$/i;
    if(!allowedExtensions.exec(filePath)){
        alert('支持图片格式 .png, .jpg, .PNG, .JPG only.');
        fileInput.value = '';
        return false;
    }else{
        //image preview
        if (fileInput.files && fileInput.files[0]) {
            var reader = new FileReader();
            reader.onload = function(e) {
                document.getElementById('uploadPreview').innerHTML = '<img src="'+e.target.result+'" style="width:120px;height:120px"/>';
            };
            reader.readAsDataURL(fileInput.files[0]);
        }
    }
  }
</script>         

