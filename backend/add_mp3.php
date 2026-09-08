<?php

    $page_title="添加音乐";

    include("includes/header.php");

    require("includes/function.php");
    require("language/language.php");

    $cat_qry="SELECT * FROM tbl_category ORDER BY category_name";
    $cat_result=mysqli_query($mysqli,$cat_qry); 

    $album_qry="SELECT * FROM tbl_album ORDER BY album_name";
    $album_result=mysqli_query($mysqli,$album_qry); 

    $art_qry="SELECT * FROM tbl_artist ORDER BY artist_name";
    $art_result=mysqli_query($mysqli,$art_qry); 

    if(isset($_POST['submit']))
    {

      $mp3_type=trim($_POST['mp3_type']);

      if($mp3_type=='server_url'){
          $mp3_url=htmlentities(trim($_POST['mp3_url']));
      }
      else{
          $path = "uploads/"; //set your folder path

          $mp3_local=rand(0,99999)."_".str_replace(" ", "-", $_FILES['mp3_local']['name']);

          $tmp = $_FILES['mp3_local']['tmp_name'];

          if (move_uploaded_file($tmp, $path.$mp3_local)) 
          {
            $mp3_url=$mp3_local;
          } else {
            echo "上传音乐错误 !!";
            exit;
          }
      }

      if($_FILES['mp3_thumbnail']['name']!="")
      {
          $ext = pathinfo($_FILES['mp3_thumbnail']['name'], PATHINFO_EXTENSION);

          $mp3_thumbnail=rand(0,99999)."_mp3_thumb.".$ext;

          //Main Image
          $tpath1='images/'.$mp3_thumbnail;   

          if($ext!='png')  {
            $pic1=compress_image($_FILES["mp3_thumbnail"]["tmp_name"], $tpath1, 80);
          }
          else{
            $tmp = $_FILES['mp3_thumbnail']['tmp_name'];
            move_uploaded_file($tmp, $tpath1);
          }

          //Thumb Image 
          $thumbpath='images/thumbs/'.$mp3_thumbnail;   
          $thumb_pic1=create_thumb_image($tpath1,$thumbpath,'200','200'); 
      }
      else{
          $mp3_thumbnail='';
      }
      if($_FILES['mp3_lrc_url']['name']!="")
      {

          unlink('lrc/'.$row['mp3_lrc_url']);
          unlink('lrc/thumbs/'.$row['mp3_lrc_url']);

          $ext = pathinfo($_FILES['mp3_lrc_url']['name'], PATHINFO_EXTENSION);

          $mp3_lrc_url=rand(0,99999)."_mp3_thumb.".$ext;

          //Main Image
          $tpath1='lrc/'.$mp3_lrc_url;   

            $tmp = $_FILES['mp3_lrc_url']['tmp_name'];
            move_uploaded_file($tmp, $tpath1);

          //Thumb Image 
          $thumbpath='lrc/thumbs/'.$mp3_lrc_url;   
        //   $thumb_pic1=create_thumb_image($tpath1,$thumbpath,'200','200'); 
      }
      else{
          $mp3_lrc_url='';
      }

      $data = array( 
          'cat_id'  =>  trim($_POST['cat_id']),
          'album_id'  =>  trim($_POST['album_id']),
          'mp3_title'  =>  htmlentities(trim($_POST['mp3_title'])),
          'mp3_type'  =>  $mp3_type,
          'mp3_url'  =>  $mp3_url,
          'mp3_thumbnail'  =>  $mp3_thumbnail,
          'mp3_lrc_url'  =>  $mp3_lrc_url,
          'mp3_duration'  =>  '-',
          'mp3_artist'  => implode(',', $_POST['mp3_artist']),
          'mp3_description'  =>  trim($_POST['mp3_description']),
          'mp3_lrc_txt'  =>  trim($_POST['mp3_lrc_txt'])
      );    

      $qry = Insert('tbl_mp3',$data); 

      $_SESSION['msg']="10";
      $_SESSION['class']="success";
      header( "Location:manage_mp3.php");
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
          <form action="" name="add_form" method="post" class="form form-horizontal" enctype="multipart/form-data">

            <div class="section">
              <div class="section-body">
               <div class="form-group">
                <label class="col-md-3 control-label">标题 :-</label>
                <div class="col-md-6">
                  <input type="text" name="mp3_title" id="mp3_title" value="" class="form-control" required>
                </div>
              </div>
              <div class="form-group">
                <label class="col-md-3 control-label">分类 :-</label>
                <div class="col-md-6">
                  <select name="cat_id" id="cat_id" class="select2" required>
                    <option value="">--选择分类--</option>
                    <?php
                    while($cat_row=mysqli_fetch_array($cat_result))
                    {
                     ?>          						 
                     <option value="<?php echo $cat_row['cid'];?>"><?php echo $cat_row['category_name'];?></option>	          							 
                     <?php
                   }
                   ?>
                 </select>
               </div>
             </div>
             <div class="form-group">
              <label class="col-md-3 control-label">专辑 :-</label>
              <div class="col-md-6">
                <select name="album_id" id="album_id" class="select2">
                  <option value="">--选择专辑--</option>
                  <?php
                  while($album_row=mysqli_fetch_array($album_result))
                  {
                    ?>                       
                    <option value="<?php echo $album_row['aid'];?>"><?php echo $album_row['album_name'];?></option>                           
                    <?php
                  }
                  ?>
                </select>
              </div>
            </div>
            <div class="form-group">
              <label class="col-md-3 control-label">歌手 :-</label>
              <div class="col-md-6">
                <select name="mp3_artist[]" id="mp3_artist" class="select2 form-control" required multiple="multiple">
                  <option value="">--选择歌手--</option>
                  <?php
                  while($art_row=mysqli_fetch_array($art_result))
                  {
                    ?>                       
                    <option value="<?php echo $art_row['artist_name'];?>"><?php echo $art_row['artist_name'];?></option>                           
                    <?php
                  }
                  ?>
                </select>
              </div>
            </div>
            <div class="form-group">
              <label class="col-md-3 control-label">上传方式 :-</label>
              <div class="col-md-6">                       
                <select name="mp3_type" id="mp3_type" style="width:280px; height:25px;" class="select2" required>
                    <option value="server_url">音乐直连</option>
                    <option value="local">本地上传</option>
                </select>
              </div>
            </div>
            <div id="mp3_url_display" class="form-group">
              <label class="col-md-3 control-label">音乐链接 :-</label>
              <div class="col-md-6">
                <input type="text" name="mp3_url" id="mp3_url" value="" class="form-control">
              </div>
            </div>
            <div id="mp3_local_display" class="form-group" style="display:none;">
              <label class="col-md-3 control-label">上传音乐 :-</label>
              <div class="col-md-6">
                <input type="file" name="mp3_local" id="mp3_local" value=""  accept=".mp3"  class="form-control">

                <div id="uploadPreview" style="display: none;background: rgba(0,0,0,0.5);text-align: center;margin-bottom: 15px;padding: 1em">
                  <audio id="audio" controls src=""></audio>  
                </div>
                
              </div>
              
            </div>
            <div id="thumbnail" class="form-group">
              <label class="col-md-3 control-label">图片:- 
                <p class="control-label-help">(推荐尺寸: 300x300,400x400 或者正方形图片)</p>
              </label>
              <div class="col-md-6">
                <div class="fileupload_block">
                  <input type="file" name="mp3_thumbnail" value="" id="fileupload" accept=".png, .jpg, .jpeg" onchange="fileValidation()">
                  <div class="fileupload_img" id="imagePreview"><img type="image" src="assets/images/add-image.png" alt="category image" /></div>
                </div>
              </div>
            </div>
            <div class="form-group">
              <label class="col-md-3 control-label">歌曲描述 :-</label>
              <div class="col-md-6">                    

                <textarea name="mp3_description" id="mp3_description" class="form-control"></textarea>

                <script>
                  CKEDITOR.replace('mp3_description',{
                    filebrowserBrowseUrl : 'filemanager/dialog.php?type=2&editor=ckeditor&fldr=&akey=viaviweb',
                    filebrowserUploadUrl : 'filemanager/dialog.php?type=2&editor=ckeditor&fldr=&akey=viaviweb',
                    filebrowserImageBrowseUrl : 'filemanager/dialog.php?type=1&editor=ckeditor&fldr=&akey=viaviweb'
                  });
                </script>

              </div> 
           </div>
           <div id="thumbnail1" class="form-group">
              <label class="col-md-3 control-label">歌词文件:-
                <p class="control-label-help">(lrc文件)</p>
              </label>
              <div class="col-md-6">
                <div class="fileupload_block">
                  <input type="file" name="mp3_lrc_url" value="" id="fileuploadLrc" accept=".lrc" onchange="fileuploadLrc()">
                  <div class="fileupload_img" id="imagePreviewLrc">请上传lrc文件</div>
                </div>
              </div>
            </div>
            <div class="form-group">
              <label class="col-md-3 control-label">歌曲歌词 :-</label>
              <div class="col-md-6">                    
                <textarea name="mp3_lrc_txt" id="mp3_lrc_txt" class="form-control"></textarea>
                <script>
                  CKEDITOR.replace('mp3_lrc_txt',{
                    filebrowserBrowseUrl : 'filemanager/dialog.php?type=2&editor=ckeditor&fldr=&akey=viaviweb',
                    filebrowserUploadUrl : 'filemanager/dialog.php?type=2&editor=ckeditor&fldr=&akey=viaviweb',
                    filebrowserImageBrowseUrl : 'filemanager/dialog.php?type=1&editor=ckeditor&fldr=&akey=viaviweb'
                  });
                </script>

              </div>
            </div>
            <br>
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

<script src="assets/js/moment.min.js"></script>

<script type="text/javascript">

  $(document).ready(function(e) {
    $("#mp3_type").change(function(){
      var type=$("#mp3_type").val();
      if(type=="server_url")
      {
          $("#mp3_url_display").show();
          $("#thumbnail").show();
          $("#mp3_local_display").hide();
          $("#mp3_local").val('');
          $("#audio").attr('src','');
      }
      else
      {
          $("#mp3_url_display").hide();               
          $("#mp3_local_display").show();
          $("#thumbnail").show();
      }
    });
  });

  var objectUrl;
  
  $("#mp3_local").change(function(e){
      var file = e.currentTarget.files[0];
     
      $("#filesize").text(file.size);
      
      objectUrl = URL.createObjectURL(file);
      $("#audio").prop("src", objectUrl);
      $("#uploadPreview").show();

  });

  function fileValidation(){
    var fileInput = document.getElementById('fileupload');
    var filePath = fileInput.value;
    var allowedExtensions = /(\.png|.jpg|.jpeg|.PNG|.JPG|.JPEG)$/i;
    if(!allowedExtensions.exec(filePath)){
        if(filePath!='')
          alert('支持图片格式 .png, .jpg, .jpeg .PNG, .JPG, .JPEG only.');
        fileInput.value = '';
        return false;
    }else{
        //image preview
        if (fileInput.files && fileInput.files[0]) {

            var reader = new FileReader();
            reader.onload = function(e) {
                document.getElementById('imagePreview').innerHTML = '<img src="'+e.target.result+'" style="width:95px;height:95px;"/>';
            };
            reader.readAsDataURL(fileInput.files[0]);
            
        }
    }
  }
   function fileValidationLrc(){
    var fileInput = document.getElementById('fileuploadLrc');
    var filePath = fileInput.value;
    var allowedExtensions = /(\.lrc)$/i;
    if(!allowedExtensions.exec(filePath)){
        if(filePath!='')
          alert('支持歌词格式 .lrc');
        fileInput.value = '';
        return false;
    }else{
        //image preview
        if (fileInput.files && fileInput.files[0]) {

            var reader = new FileReader();
            reader.onload = function(e) {
                document.getElementById('imagePreviewLrc').innerHTML = '<span>'+e.target.result+'</span>';
            };
            reader.readAsDataURL(fileInput.files[0]);
            
        }
    }
  }
</script>       
