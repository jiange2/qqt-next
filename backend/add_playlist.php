<?php
    
  $page_title=(isset($_GET['playlist_id'])) ? '编辑播放列表' : '添加播放列表';

  include("includes/header.php");

  require("includes/function.php");
  require("language/language.php");

  require_once("thumbnail_images.class.php");

  if(isset($_POST['submit']) and isset($_GET['add']))
  {
  
      $playlist_image=rand(0,99999)."_".$_FILES['playlist_image']['name'];

      //Main Image
      $tpath1='images/'.$playlist_image;        
      $pic1=compress_image($_FILES["playlist_image"]["tmp_name"], $tpath1, 80);

      //Thumb Image 
      $thumbpath='images/thumbs/'.$playlist_image;   
      $thumb_pic1=create_thumb_image($tpath1,$thumbpath,'300','300');   


      $data = array( 
          'playlist_name'  =>  cleanInput($_POST['playlist_name']),
          'playlist_image'  =>  $playlist_image,
          'playlist_songs'  =>  implode(',',$_POST['playlist_songs'])
      );    

      $qry = Insert('tbl_playlist',$data);  

      $_SESSION['msg']="10";
      $_SESSION['class']='success';

      header( "Location:manage_playlist.php");
      exit;
  }
  
  if(isset($_GET['playlist_id']))
  {
       
      $qry="SELECT * FROM tbl_playlist where pid='".$_GET['playlist_id']."'";
      $result=mysqli_query($mysqli,$qry);
      $row=mysqli_fetch_assoc($result);

  }
  
  if(isset($_GET['playlist_id'])){
      $mp3_qry="SELECT * FROM tbl_mp3 WHERE tbl_mp3.`id` IN (".$row['playlist_songs'].") ORDER BY tbl_mp3.id DESC"; 
  }
  else{
      $mp3_qry="SELECT * FROM tbl_mp3 ORDER BY tbl_mp3.`id` DESC LIMIT 0, 10"; 
  }
        
  $mp3_result=mysqli_query($mysqli,$mp3_qry); 
  
  if(isset($_POST['submit']) and isset($_POST['playlist_id']))
  {
     if($_FILES['playlist_image']['name']!="")
     {
        if($row['playlist_image']!="")
        {
            unlink('images/thumbs/'.$row['playlist_image']);
            unlink('images/'.$row['playlist_image']);
        }

        $playlist_image=rand(0,99999)."_".$_FILES['playlist_image']['name'];

        //Main Image
        $tpath1='images/'.$playlist_image;        
        $pic1=compress_image($_FILES["playlist_image"]["tmp_name"], $tpath1, 80);

        //Thumb Image 
        $thumbpath='images/thumbs/'.$playlist_image;   
        $thumb_pic1=create_thumb_image($tpath1,$thumbpath,'300','300');

        $data = array(
          'playlist_name'  =>  cleanInput($_POST['playlist_name']),
          'playlist_image'  =>  $playlist_image,
          'playlist_songs'  =>  implode(',',$_POST['playlist_songs'])
        );

        $update=Update('tbl_playlist', $data, "WHERE pid = '".$_POST['playlist_id']."'");
     }
     else
     {

        $data = array(
          'playlist_name'  =>  cleanInput($_POST['playlist_name']),
          'playlist_songs'  =>  implode(',',$_POST['playlist_songs'])
        );  

        $update=Update('tbl_playlist', $data, "WHERE pid = '".$_POST['playlist_id']."'");
     }
 
    $_SESSION['msg']="11";
    $_SESSION['class']='success'; 
    
    if(isset($_GET['redirect'])){
      header("Location:".$_GET['redirect']);
    }
    else{
      header( "Location:add_playlist.php?playlist_id=".$_POST['playlist_id']);
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
        <form action="" name="" method="post" class="form form-horizontal" enctype="multipart/form-data">
          <input  type="hidden" name="playlist_id" value="<?php echo $_GET['playlist_id'];?>" />

          <div class="section">
            <div class="section-body">
           
              <div class="form-group">
                <label class="col-md-3 control-label">播放列表名称 :-</label>
                <div class="col-md-6">
                  <input type="text" name="playlist_name" id="playlist_name" value="<?php if(isset($_GET['playlist_id'])){echo $row['playlist_name'];}?>" class="form-control" required>
                </div>
              </div>
              <div class="form-group">
                <label class="col-md-3 control-label">选择图片 :-
                  <p class="control-label-help">(推荐尺寸: 300x300, 400x400 或者正方形图片)</p>
                </label>
                <div class="col-md-6">
                  <div class="fileupload_block">
                    <input type="file" name="playlist_image" value="fileupload" accept=".png, .jpg, .JPG .PNG" onchange="fileValidation()" id="fileupload">
                    <?php if(isset($_GET['playlist_id'])) {?>
                      <div class="fileupload_img" id="uploadPreview"><img type="image" src="images/<?php echo $row['playlist_image'];?>" alt="image" style="width: 120px;height: 120px;"/></div>
                    <?php }else{?>
                      <div class="fileupload_img" id="uploadPreview"><img type="image" src="assets/images/square.jpg" alt="image" style="width: 120px;height: 120px" /></div>
                      <?php } ?>
                       
                  </div>
                </div>
              </div>
              <div class="form-group">
                <label class="col-md-3 control-label">歌曲 :-</label>
                <div class="col-md-6">
                    <?php if(isset($_GET['playlist_id'])){?>
                    <input type="hidden" value="<?=$row['playlist_songs']?>" id="search">
                    <?php }else{?>
                    <input type="hidden" value="" id="search">
                    <?php }?>  
                    
                  <select name="playlist_songs[]" id="playlist_songs" class="select2 form-control" required multiple="multiple">
                    <option value="">--选择歌曲--</option>
                    <?php
                        while($mp3_row=mysqli_fetch_array($mp3_result))
                        {
                    ?>   
                    <?php if(isset($_GET['playlist_id'])){?>

                       <option value="<?php echo $mp3_row['id'];?>" <?php $songs_list=explode(",", $row['playlist_songs']);foreach($songs_list as $song_id){ if($mp3_row['id']==$song_id){ echo 'selected="selected"'; }}?>><?php echo $mp3_row['mp3_title'];?></option>

                    <?php }else{?>  

                      <option value="<?php echo $mp3_row['id'];?>"><?php echo $mp3_row['mp3_title'];?></option>
                        
                    <?php }?>   
                     
                    <?php
                      }
                    ?>
                  </select>
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

    //$(".select2").val($("#search").val()).trigger("change")
    $(function(){
      $('.select2').select2({
        ajax: {
          url: 'getData.php',
          dataType: 'json',
          delay: 250,
          data: function (params) {
            var query = {
              type: 'song',
              search: params.term,
              page: params.page || 1
            }
            return query;
          },
          processResults: function (data, params) {
             params.page = params.page || 1;
              return {
                  results: data.items,
                  pagination: {
                      more: (params.page * 5) < data.total_count
                  }
              };
          },
          cache: true
        }
      });
    });


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