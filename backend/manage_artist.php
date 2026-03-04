<?php 
  
  $page_title="管理歌手";

  include("includes/header.php");
  require("includes/function.php");
  require("language/language.php");

  $tableName="tbl_artist";   
  $targetpage = "manage_artist.php"; 
  $limit = 12; 

  $keyword='';

  if(!isset($_GET['keyword'])){
    $query = "SELECT COUNT(*) as num FROM $tableName";
  }
  else{

    $keyword=addslashes(trim($_GET['keyword']));

    $query = "SELECT COUNT(*) as num FROM $tableName WHERE `artist_name` LIKE '%$keyword%'";

    $targetpage = "manage_artist.php?keyword=".$_GET['keyword'];

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
    $sql_query="SELECT * FROM tbl_artist ORDER BY tbl_artist.`id` DESC LIMIT $start, $limit"; 
  }
  else{

    $sql_query="SELECT * FROM tbl_artist WHERE `artist_name` LIKE '%$keyword%' ORDER BY tbl_artist.`id` DESC LIMIT $start, $limit"; 
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
                <input class="form-control input-sm" placeholder="在这里搜索..." aria-controls="DataTables_Table_0" type="search" name="keyword" value="<?php if(isset($_GET['keyword'])){ echo $_GET['keyword'];} ?>" required="required">
                <button type="submit" class="btn-search"><i class="fa fa-search"></i></button>
              </form>
            </div>
            <div class="add_btn_primary"> <a href="add_artist.php?add=yes">添加歌手</a> </div>
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
                      <a href="javascript:void(0)"><?php echo $row['artist_name'];?></a>
                    </h2>
                    <ul>                
                      <li>
                        <a href="add_artist.php?artist_id=<?php echo $row['id'];?>&redirect=<?=$redirectUrl?>" data-toggle="tooltip" data-tooltip="编辑"><i class="fa fa-edit"></i></a>
                      </li>
                      <li>
                        <a href="javascript:void(0)" class="btn_delete_a" data-id="<?php echo $row['id'];?>"  data-toggle="tooltip" data-tooltip="删除"><i class="fa fa-trash"></i></a>
                      </li>

                    </ul>
                  </div>
                  <span><img src="images/<?php echo $row['artist_image'];?>" /></span>
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

  <script type="text/javascript">

    $(".btn_delete_a").click(function(e){

        e.preventDefault();

        var _ids=$(this).data("id");
        var _table='tbl_artist';

        swal({
            title: "你确定要删除他吗?",
            type: "warning",
            showCancelButton: true,
            cancelButtonClass: "btn-warning",
            confirmButtonClass: "btn-danger",
            confirmButtonText: "是的",
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
                        text: "歌手被删除.", 
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
