<?php 
	
	$page_title=(isset($_GET['artist_id'])) ? '编辑歌手' : '添加歌手';

	include("includes/header.php");

	require("includes/function.php");
	require("language/language.php");

	require_once("thumbnail_images.class.php");


	if(isset($_POST['submit']) and isset($_GET['add']))
	{
		$ext = pathinfo($_FILES['artist_image']['name'], PATHINFO_EXTENSION);

        $artist_image=rand(0,99999)."_artist.".$ext;

        //Main Image
        $tpath1='images/'.$artist_image;   

        if($ext!='png')  {
          $pic1=compress_image($_FILES["artist_image"]["tmp_name"], $tpath1, 80);
        }
        else{
          $tmp = $_FILES['artist_image']['tmp_name'];
          move_uploaded_file($tmp, $tpath1);
        }

        //Thumb Image 
        $thumbpath='images/thumbs/'.$artist_image;   
        $thumb_pic1=create_thumb_image($tpath1,$thumbpath,'300','300'); 


        $data = array( 
            'artist_name'  =>  cleanInput($_POST['artist_name']),
            'artist_image'  =>  $artist_image
        );    

        $qry = Insert('tbl_artist',$data);  

        $_SESSION['msg']="10";
        $_SESSION['class']='success';
        header( "Location:manage_artist.php");
        exit;
	}

	if(isset($_GET['artist_id']))
	{

		$qry="SELECT * FROM tbl_artist where id='".$_GET['artist_id']."'";
		$result=mysqli_query($mysqli,$qry);
		$row=mysqli_fetch_assoc($result);

	}
	if(isset($_POST['submit']) and isset($_POST['artist_id']))
	{
		if($_FILES['artist_image']['name']!="")
        {
            if($row['artist_image']!="")
            {
                unlink('images/thumbs/'.$row['artist_image']);
                unlink('images/'.$row['artist_image']);
            }

            $ext = pathinfo($_FILES['artist_image']['name'], PATHINFO_EXTENSION);

            $artist_image=rand(0,99999)."_artist.".$ext;

            //Main Image
            $tpath1='images/'.$artist_image;   

            if($ext!='png')  {
              $pic1=compress_image($_FILES["artist_image"]["tmp_name"], $tpath1, 80);
            }
            else{
              $tmp = $_FILES['artist_image']['tmp_name'];
              move_uploaded_file($tmp, $tpath1);
            }

            //Thumb Image 
            $thumbpath='images/thumbs/'.$artist_image;   
            $thumb_pic1=create_thumb_image($tpath1,$thumbpath,'300','300');

        }
        else{
            $artist_image=$row['artist_image'];
        }
          
        $data = array( 
            'artist_name'  =>  cleanInput($_POST['artist_name']),
            'artist_image'  =>  $artist_image
        );

        $edit=Update('tbl_artist', $data, "WHERE id = '".$_POST['artist_id']."'");

        $_SESSION['msg']="11";
        $_SESSION['class']='success'; 
        
        if(isset($_GET['redirect'])){
          header("Location:".$_GET['redirect']);
        }
        else{
          header( "Location:add_artist.php?artist_id=".$_POST['artist_id']);
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
					<input  type="hidden" name="artist_id" value="<?php echo $_GET['artist_id'];?>" />
					<div class="section">
						<div class="section-body">
							<div class="form-group">
								<label class="col-md-3 control-label">歌手名称 :-</label>
								<div class="col-md-6">
									<input type="text" name="artist_name" id="artist_name" value="<?php if(isset($_GET['artist_id'])){echo $row['artist_name'];}?>" class="form-control" required="required">
								</div>
							</div>
							<div class="form-group">
								<label class="col-md-3 control-label">歌手图片 :-
									<p class="control-label-help">(推荐分辨率尺寸: 300x300,400x400 或者正方形图片)</p>
								</label>
								<div class="col-md-6">
				                  <div class="fileupload_block">
				                    <input type="file" name="artist_image" value="fileupload" accept=".png, .jpg, .JPG .PNG" onchange="fileValidation()" id="fileupload">
				                    <?php if(isset($_GET['artist_id'])) {?>
				                      <div class="fileupload_img" id="uploadPreview"><img type="image" src="images/<?php echo $row['artist_image'];?>" alt="image" style="width: 120px;height: 120px;"/></div>
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
