<?php

  $page_title="管理专辑"; 

  include("includes/header.php");

  require("includes/function.php");
  require("language/language.php");


  $tableName="tbl_album";   
  $targetpage = "manage_album.php"; 
  $limit = 12; 

  $keyword='';

  if(!isset($_GET['keyword'])){
    $query = "SELECT COUNT(*) as num FROM $tableName";
  }
  else{

    $keyword=addslashes(trim($_GET['keyword']));

    $query = "SELECT COUNT(*) as num FROM $tableName WHERE `album_name` LIKE '%$keyword%'";

    $targetpage = "manage_album.php?keyword=".$_GET['keyword'];

  }

  $total_pages = mysqli_fetch_array(mysqli_query($mysqli,$query));
  $total_pages = $total_pages['num'];

  $stages = 3;
  $page=0;
  if(isset($_GET['page'])){
    $page = mysqli_real_escape_string($mysqli,$_GET['page']);
  }
  if($page){
    $start = ($page - 1) * $limit; 
  }else{
    $start = 0; 
  } 

  if(!isset($_GET['keyword'])){
    $sql_query="SELECT * FROM tbl_album ORDER BY tbl_album.`aid` DESC LIMIT $start, $limit"; 
  }
  else{

    $sql_query="SELECT * FROM tbl_album WHERE `album_name` LIKE '%$keyword%' ORDER BY tbl_album.`aid` DESC LIMIT $start, $limit"; 
  }

  $result=mysqli_query($mysqli,$sql_query) or die(mysqli_error($mysqli));


  function get_total_songs($album_id)
  { 
    global $mysqli;   

    $qry_songs="SELECT COUNT(*) as num FROM tbl_mp3 WHERE album_id='".$album_id."'";

    $total_songs = mysqli_fetch_array(mysqli_query($mysqli,$qry_songs));
    $total_songs = $total_songs['num'];

    return $total_songs;
  }

?>

<link rel="stylesheet" type="text/css" href="https://cdnjs.cloudflare.com/ajax/libs/limonte-sweetalert2/8.11.8/sweetalert2.css">

<style type="text/css">
  .swal2-popup{
    width: 32em;
    font-size: inherit;
  }
  .swal2-label{
    font-weight: normal;
  }
  .swal2-actions{
    margin: 0px;
  }
  .swal2-icon{
    margin: 1em auto 1.875em;
  }
</style>

<div class="row">
  <div class="col-xs-12">
    <div class="card mrg_bottom">
      <div class="page_title_block">
        <div class="col-md-5 col-xs-12">
          <div class="page_title"><?=$page_title?></div>
        </div>
        <div class="col-md-7 col-xs-12">
          <div class="search_list">
            <div class="search_block">
              <form method="get" action="">
                <input class="form-control input-sm" placeholder="在这里搜索..." aria-controls="DataTables_Table_0" type="search" name="keyword" value="<?php if(isset($_GET['keyword'])){ echo $_GET['keyword'];} ?>" required="required">
                <button type="submit" class="btn-search"><i class="fa fa-search"></i></button>
              </form>  
            </div>
            <div class="add_btn_primary"> <a href="add_album.php?add=yes">添加专辑</a> </div>
          </div>
        </div>
      </div>
      <div class="clearfix"></div>
         <div class="col-md-12 mrg-top">
          <div class="row">
            <?php 
            $i=0;
            while($row=mysqli_fetch_array($result))
            {         
              ?>
              <div class="col-lg-3 col-sm-6 col-xs-12">
                <div class="block_wallpaper">           
                  <div class="wall_image_title">
                    <h2>
                      <a href="javascript:void(0)">
                        <?php echo $row['album_name'];?> <span>(<?php echo get_total_songs($row['aid']);?>)</span>
                      </a>
                    </h2>
                    <ul>                
                      <li><a href="add_album.php?album_id=<?php echo $row['aid'];?>&redirect=<?=$redirectUrl?>" data-toggle="tooltip" data-tooltip="编辑"><i class="fa fa-edit"></i></a></li>
                      
                      <li>
                        <a href="javascript:void(0)" class="btn_delete_a" data-id="<?php echo $row['aid'];?>"  data-toggle="tooltip" data-tooltip="删除"><i class="fa fa-trash"></i></a>
                      </li>
                      
                      <?php if($row['status']!="0"){?>
                        <li><div class="row toggle_btn"><a href="javascript:void(0)" data-id="<?php echo $row['aid'];?>" data-action="deactive" data-column="status" data-toggle="tooltip" data-tooltip="启用"><img src="assets/images/btn_enabled.png" alt="" /></a></div></li>

                      <?php }else{?>
                        <li><div class="row toggle_btn"><a href="javascript:void(0)" data-id="<?php echo $row['aid'];?>" data-action="active" data-column="status" data-toggle="tooltip" data-tooltip="禁用"><img src="assets/images/btn_disabled.png" alt="" /></a></div></li>
                      <?php }?>


                    </ul>
                  </div>
                  <span><img src="images/<?php echo $row['album_image'];?>" /></span>
                </div>
              </div>
              <?php

              $i++;
            }
            ?>     

          </div>
        </div>
        <div class="col-md-12 col-xs-12">
          <div class="pagination_item_block">
            <nav>
              <?php include("pagination.php")?>
            </nav>
          </div>
        </div>
        <div class="clearfix"></div>
      </div>
    </div>
  </div>


  <?php include("includes/footer.php");?>       

  <script src="https://cdnjs.cloudflare.com/ajax/libs/limonte-sweetalert2/8.11.8/sweetalert2.js"></script>


  <script type="text/javascript">

    $(".toggle_btn a").on("click",function(e){
      e.preventDefault();

      var _for=$(this).data("action");
      var _id=$(this).data("id");
      var _column=$(this).data("column");
      var _table='tbl_album';

      $.ajax({
        type:'post',
        url:'processData.php',
        dataType:'json',
        data:{id:_id,for_action:_for,column:_column,table:_table,'action':'toggle_status','tbl_id':'aid'},
        success:function(res){
          console.log(res);
          if(res.status=='1'){
            location.reload();
          }
        }
      });

    });

    $(".btn_delete_a").click(function(e){
      e.preventDefault();

      var _ids = $(this).data("id");

      if(_ids!='')
      {
        Swal.fire({
          title: '你确定要删除吗?',
          type:'warning',
          input: 'checkbox',
          inputPlaceholder: '你想删除相关的歌曲吗?',
          showCancelButton: true,
          confirmButtonClass: "btn-danger btn_edit",
          cancelButtonClass: "btn-warning btn_edit",
          cancelButtonText: "放弃",
          confirmButtonText: "确定",
          closeOnConfirm: false,
          closeOnCancel: false,
          showLoaderOnConfirm: true
        }).then(function(result) {
          if (result.value) {

            $.ajax({
              type:'post',
              url:'processData.php',
              dataType:'json',
              data:{id:_ids,'action':'multi_delete','yes_no':"yes",'tbl_nm':'tbl_album'},
              success:function(res){
                console.log(res);
                if(res.status=='1'){
                  Swal.fire({type: 'success', text: '专辑被删除.'}).then(function(){ 
                   location.reload();
                 }
                 );
                }
                else if(res.status=='-2'){
                  alert(res.message);
                }
              }
            });

          } else if (result.value === 0) {
            $.ajax({
              type:'post',
              url:'processData.php',
              dataType:'json',
              data:{id:_ids,'action':'multi_delete','yes_no':"no",'tbl_nm':'tbl_album'},
              success:function(res){
                console.log(res);
                if(res.status=='1'){
                  Swal.fire({type: 'success', text: '专辑被删除.'}).then(function(){ 
                   location.reload();
                 }
                 );
                }
                else if(res.status=='-2'){
                  alert(res.message);
                }
              }
            });

          } else {

          }
        });
      }
    });

  </script>       
