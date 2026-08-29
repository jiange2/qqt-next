<?php
    
  $page_title=(isset($_GET['banner_id'])) ? '编辑横幅' : '添加横幅';

  include("includes/header.php");

  require("includes/function.php");
  require("language/language.php");

  require_once("thumbnail_images.class.php");

  // 校验：横幅必须挂接一组歌曲或填写一个跳转地址，二者至少其一
  $banner_error='';
  $selected_songs=array();
  if(isset($_POST['submit']) and (isset($_GET['add']) or isset($_POST['banner_id'])))
  {
      $selected_songs=(isset($_POST['banner_songs']) and is_array($_POST['banner_songs'])) ? array_filter($_POST['banner_songs']) : array();
      if(empty($selected_songs) and trim($_POST['link']) === '')
      {
          $banner_error='请至少选择一组歌曲或填写跳转地址';
      }
  }

  if(isset($_POST['submit']) and isset($_GET['add']) and $banner_error === '')
  {
  
      $banner_image=rand(0,99999)."_".$_FILES['banner_image']['name'];

      //Main Image
      $tpath1='images/'.$banner_image;        
      $pic1=compress_image($_FILES["banner_image"]["tmp_name"], $tpath1, 80);

      //Thumb Image 
      $thumbpath='images/thumbs/'.$banner_image;   
      $thumb_pic1=create_thumb_image($tpath1,$thumbpath,'300','300');   


      $data = array( 
          'banner_title'  =>  cleanInput($_POST['banner_title']),
          'banner_sort_info'  =>  addslashes(trim($_POST['banner_sort_info'])),
          'banner_image'  =>  $banner_image,
          'banner_songs'  =>  implode(',',$selected_songs),
          'link'  =>  cleanInput($_POST['link'])
      );

      $qry = Insert('tbl_banner',$data);  

      $_SESSION['msg']="10";
      $_SESSION['class']='success';

      header( "Location:manage_banners.php");
      exit;
  }
  
  if(isset($_GET['banner_id']))
  {
       
      $qry="SELECT * FROM tbl_banner where bid='".$_GET['banner_id']."'";
      $result=mysqli_query($mysqli,$qry);
      $row=mysqli_fetch_assoc($result);

  }
  
  if(isset($_GET['banner_id']) and trim($row['banner_songs']) !== ''){
      $mp3_qry="SELECT * FROM tbl_mp3 WHERE tbl_mp3.`id` IN (".$row['banner_songs'].") ORDER BY tbl_mp3.id DESC"; 
  }
  else{
      $mp3_qry="SELECT * FROM tbl_mp3 ORDER BY tbl_mp3.`id` DESC LIMIT 0, 10"; 
  }
        
  $mp3_result=mysqli_query($mysqli,$mp3_qry); 
  
  if(isset($_POST['submit']) and isset($_POST['banner_id']) and $banner_error === '')
  {
     if($_FILES['banner_image']['name']!="")
     {
        if($row['banner_image']!="")
        {
            unlink('images/thumbs/'.$row['banner_image']);
            unlink('images/'.$row['banner_image']);
        }

        $banner_image=rand(0,99999)."_".$_FILES['banner_image']['name'];

        //Main Image
        $tpath1='images/'.$banner_image;        
        $pic1=compress_image($_FILES["banner_image"]["tmp_name"], $tpath1, 80);

        //Thumb Image 
        $thumbpath='images/thumbs/'.$banner_image;   
        $thumb_pic1=create_thumb_image($tpath1,$thumbpath,'300','300');

        $data = array(
          'banner_title'  =>  cleanInput($_POST['banner_title']),
          'banner_sort_info'  =>  addslashes(trim($_POST['banner_sort_info'])),
          'banner_image'  =>  $banner_image,
          'banner_songs'  =>  implode(',',$selected_songs),
          'link'  =>  cleanInput($_POST['link'])
        );

        $update=Update('tbl_banner', $data, "WHERE bid = '".$_POST['banner_id']."'");
     }
     else
     {

        $data = array(
          'banner_title'  =>  cleanInput($_POST['banner_title']),
          'banner_sort_info'  =>  addslashes(trim($_POST['banner_sort_info'])),
          'banner_songs'  =>  implode(',',$selected_songs),
          'link'  =>  cleanInput($_POST['link'])
        );  

        $update=Update('tbl_banner', $data, "WHERE bid = '".$_POST['banner_id']."'");
     }
 
      $_SESSION['msg']="11";
      $_SESSION['class']='success'; 
      
      if(isset($_GET['redirect'])){
        header("Location:".$_GET['redirect']);
      }
      else{
        header( "Location:add_banner.php?banner_id=".$_POST['banner_id']);
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
        <?php if($banner_error){ ?>
          <div class="alert alert-danger"><?=$banner_error?></div>
        <?php } ?>
        <form action="" name="" method="post" class="form form-horizontal" enctype="multipart/form-data">
          <input  type="hidden" name="banner_id" value="<?php echo $_GET['banner_id'];?>" />

          <div class="section">
            <div class="section-body">
           
              <div class="form-group">
                <label class="col-md-3 control-label">横幅标题 :-</label>
                <div class="col-md-6">
                  <input type="text" name="banner_title" id="banner_title" value="<?php if(isset($_GET['banner_id'])){echo $row['banner_title'];}?>" class="form-control" required>
                </div>
              </div>
              <div class="form-group">
                <label class="col-md-3 control-label">横幅谢谢 :-</label>
                <div class="col-md-6">
                  <input type="text" name="banner_sort_info" id="banner_sort_info" value="<?php if(isset($_GET['banner_id'])){echo $row['banner_sort_info'];}?>" class="form-control" required>
                </div>
              </div>
              <div class="form-group">
                <label class="col-md-3 control-label">选择图片 :-
                  <p class="control-label-help">(推荐尺寸: 300x300, 400x400 或者正方形图片)</p>
                </label>
                <div class="col-md-6">
                  <div class="fileupload_block">
                    <input type="file" name="banner_image" value="fileupload" accept=".png, .jpg, .JPG .PNG" onchange="fileValidation()" id="fileupload">
                    <?php if(isset($_GET['banner_id'])) {?>
                      <div class="fileupload_img" id="uploadPreview"><img type="image" src="images/<?php echo $row['banner_image'];?>" alt="image" style="width: 120px;height: 120px;"/></div>
                    <?php }else{?>
                      <div class="fileupload_img" id="uploadPreview"><img type="image" src="assets/images/square.jpg" alt="image" style="width: 120px;height: 120px" /></div>
                      <?php } ?>
                       
                  </div>
                </div>
              </div>
              <div class="form-group">
                <label class="col-md-3 control-label">跳转地址 :-</label>
                <div class="col-md-6">
                     <input type="text" name="link" id="link" value="<?php if(isset($_GET['banner_id'])){echo $row['link'];}?>" class="form-control">
                     <p class="control-label-help">(可选，未选择歌曲时点击横幅跳转该地址)</p>
                </div>
              </div>
              <div class="form-group">
                <label class="col-md-3 control-label">歌曲 :-</label>
                <div class="col-md-6">
                    <?php if(isset($_GET['banner_id'])){?>
                    <input type="hidden" value="<?=$row['banner_songs']?>" id="search">
                    <?php }else{?>
                    <input type="hidden" value="" id="search">
                    <?php }?>  
                    
                  <select name="banner_songs[]" id="banner_songs" class="select2 form-control" multiple="multiple">
                    <option value="">--选择歌曲--</option>
                    <?php
                        while($mp3_row=mysqli_fetch_array($mp3_result))
                        {
                    ?>   
                    <?php if(isset($_GET['banner_id'])){
                    
                       // 已选歌曲可能不在初始候选里，确保其选项存在且被选中，避免保存时丢失
                       echo '<option value="'.$mp3_row['id'].'"';
                       $songs_list=explode(",", $row['banner_songs']);
                       foreach($songs_list as $song_id){ if($mp3_row['id']==$song_id){ echo ' selected="selected"'; }}
                       echo '>'.$mp3_row['mp3_title'].'</option>';
                    
                    ?>
                    
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

    $(function(){
      // 提交前校验：歌曲与跳转地址至少填一项（与服务端校验一致）
      $('form.form-horizontal').on('submit', function(e){
        var songs = $('#banner_songs').val();
        var hasSongs = songs && songs.length > 0 && !(songs.length === 1 && songs[0] === '');
        var hasLink = $.trim($('#link').val()) !== '';
        if(!hasSongs && !hasLink){
          e.preventDefault();
          alert('请至少选择一组歌曲或填写跳转地址');
        }
      });

      $('.select2').select2({        ajax: {
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