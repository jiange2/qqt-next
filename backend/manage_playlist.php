<?php 

    $page_title="管理播放列表";

    include("includes/header.php");
    require("includes/function.php");
    require("language/language.php");


    $tableName="tbl_playlist";   
    $targetpage = "manage_playlist.php"; 
    $limit = 12; 

    $keyword='';

    if(!isset($_GET['keyword'])){
      $query = "SELECT COUNT(*) as num FROM $tableName";
    }
    else{

      $keyword=addslashes(trim($_GET['keyword']));

      $query = "SELECT COUNT(*) as num FROM $tableName WHERE `playlist_name` LIKE '%$keyword%'";

      $targetpage = "manage_playlist.php?keyword=".$_GET['keyword'];

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
      $sql_query="SELECT * FROM tbl_playlist ORDER BY tbl_playlist.`pid` DESC LIMIT $start, $limit"; 
    }
    else{

      $sql_query="SELECT * FROM tbl_playlist WHERE `playlist_name` LIKE '%$keyword%' ORDER BY tbl_playlist.`pid` DESC LIMIT $start, $limit"; 
    }

    $result=mysqli_query($mysqli,$sql_query) or die(mysqli_error($mysqli));
?>

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
                <input class="form-control input-sm" placeholder="这里搜索..." aria-controls="DataTables_Table_0" type="search" name="keyword" value="<?php if (isset($_GET['keyword'])) { echo $_GET['keyword']; } ?>" required="required">
                <button type="submit" class="btn-search"><i class="fa fa-search"></i></button>
              </form>
            </div>
            <div class="add_btn_primary"> <a href="add_playlist.php?add=yes">添加播放列表</a> </div>
          </div>
        </div>
      </div>
      <div class="clearfix"></div>
      <div class="col-md-12 mrg-top">
        <div class="row">
          <?php
          $i = 0;
          while ($row = mysqli_fetch_array($result)) {
          ?>
            <div class="col-lg-3 col-sm-6 col-xs-12">
              <div class="block_wallpaper">
                <div class="wall_image_title">
                  <h2>
                    <a href="javascript:void(0)" title="<?=$row['playlist_name']?>">
                      <?php 
                        if(strlen($row['playlist_name']) > 20){
                          echo substr(stripslashes($row['playlist_name']), 0, 20).'...';  
                        }else{
                          echo $row['playlist_name'];
                        }
                      ?>
                    </a>
                  </h2>
                  <ul>
                    <li><a href="add_playlist.php?playlist_id=<?php echo $row['pid']; ?>&redirect=<?=$redirectUrl?>" data-toggle="tooltip" data-tooltip="编辑"><i class="fa fa-edit"></i></a></li>

                    <li>
                      <a href="" class="btn_delete_a" data-id="<?php echo $row['pid']; ?>" data-toggle="tooltip" data-tooltip="删除"><i class="fa fa-trash"></i></a>
                    </li>

                    <?php if ($row['status'] != "0") { ?>
                      <li>
                        <div class="row toggle_btn"><a href="javascript:void(0)" data-id="<?php echo $row['pid']; ?>" data-action="deactive" data-column="status" data-toggle="tooltip" data-tooltip="启用"><img src="assets/images/btn_enabled.png" alt="" /></a></div>
                      </li>

                    <?php } else { ?>
                      <li>
                        <div class="row toggle_btn"><a href="javascript:void(0)" data-id="<?php echo $row['pid']; ?>" data-action="active" data-column="status" data-toggle="tooltip" data-tooltip="禁用"><img src="assets/images/btn_disabled.png" alt="" /></a></div>
                      </li>
                    <?php } ?>

                  </ul>
                </div>
                <span><img src="images/<?php echo $row['playlist_image']; ?>" /></span>
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
            <?php include("pagination.php") ?>
          </nav>
        </div>
      </div>
      <div class="clearfix"></div>
    </div>
  </div>
</div>

<?php include("includes/footer.php"); ?>

<script type="text/javascript">
  $(".toggle_btn a").on("click",function(e){
    e.preventDefault();

    var _for=$(this).data("action");
    var _id=$(this).data("id");
    var _column=$(this).data("column");
    var _table='tbl_playlist';

    $.ajax({
      type:'post',
      url:'processData.php',
      dataType:'json',
      data:{id:_id,for_action:_for,column:_column,table:_table,'action':'toggle_status','tbl_id':'pid'},
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

    var _ids=$(this).data("id");
    var _table='tbl_playlist';

    swal({
        title: "你确定要删除吗?",
        type: "warning",
        showCancelButton: true,
        cancelButtonClass: "btn-warning",
        confirmButtonClass: "btn-danger",
        confirmButtonText: "确定",
        cancelButtonText: "放弃",
        closeOnConfirm: false,
        closeOnCancel: false,
        showLoaderOnConfirm: true
      },
      function(isConfirm) {
        if (isConfirm) {

          $.ajax({
            type:'post',
            url:'processData.php',
            dataType:'json',
            data:{id:_ids,'action':'multi_delete','tbl_nm':_table},
            success:function(res){
              console.log(res);
              if(res.status=='1'){
                swal({
                    title: "成功", 
                    text: "播放列表被删除.", 
                    type: "success"
                },function() {
                    location.reload();
                });
              }
              else if(res.status=='-2'){
                swal(res.message);
              }
            }
          });
        }
        else{
          swal.close();
        }
    });
  });
</script>