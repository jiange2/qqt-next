<?php

    $page_title = "管理音乐";

    include("includes/header.php");
    require("includes/function.php");
    require("language/language.php");

    $tableName = "tbl_mp3";
    $targetpage = "manage_mp3.php";
    $limit = 12;

    $keyword = '';

    if (isset($_GET['category'])) {

      $category = trim($_GET['category']);
      $album = '';

      $targetpage = "manage_mp3.php?category=" . $category;

      $query = "SELECT COUNT(*) as num FROM $tableName WHERE `cat_id`=" . $category;

      if (isset($_GET['album'])) {

        $album = $_GET['album'];

        $query = "SELECT COUNT(*) as num FROM $tableName WHERE `cat_id`= $category  AND `album_id` = $album";

        $targetpage = "manage_mp3.php?category=" . $category . "&album=" . $album;
      }

      $total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query));
      $total_pages = $total_pages['num'];

      $stages = 3;
      $page = 0;
      if (isset($_GET['page'])) {
        $page = mysqli_real_escape_string($mysqli, $_GET['page']);
      }
      if ($page) {
        $start = ($page - 1) * $limit;
      } else {
        $start = 0;
      }

      if (isset($_GET['album'])) {
        $sql_query = "SELECT tbl_mp3.*, tbl_category.`cid`, tbl_category.`category_name` FROM tbl_mp3 LEFT JOIN tbl_category ON tbl_mp3.`cat_id` = tbl_category.`cid` WHERE tbl_mp3.`cat_id` = '$category' AND tbl_mp3.`album_id` = '$album' ORDER BY tbl_mp3.`id` DESC LIMIT $start, $limit";
      } else {
        $sql_query = "SELECT tbl_mp3.*, tbl_category.`cid`, tbl_category.`category_name` FROM tbl_mp3 LEFT JOIN tbl_category ON tbl_mp3.`cat_id` = tbl_category.`cid` WHERE tbl_mp3.`cat_id` = '$category' ORDER BY tbl_mp3.`id` DESC LIMIT $start, $limit";
      }
    } else if (isset($_GET['album'])) {

      $album = trim($_GET['album']);

      $query = "SELECT COUNT(*) as num FROM $tableName WHERE `album_id` = $album";

      $targetpage = "manage_mp3.php?album=" . $album;

      $total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query));
      $total_pages = $total_pages['num'];

      $stages = 3;
      $page = 0;
      if (isset($_GET['page'])) {
        $page = mysqli_real_escape_string($mysqli, $_GET['page']);
      }
      if ($page) {
        $start = ($page - 1) * $limit;
      } else {
        $start = 0;
      }

      $sql_query = "SELECT tbl_mp3.*, tbl_category.`cid`, tbl_category.`category_name` FROM tbl_mp3 LEFT JOIN tbl_category ON tbl_mp3.`cat_id` = tbl_category.`cid` WHERE tbl_mp3.`album_id` = '$album' ORDER BY tbl_mp3.`id` DESC LIMIT $start, $limit";
    } 
    else if (isset($_GET['keyword'])) {

      $keyword = addslashes(trim($_GET['keyword']));

      $query = "SELECT COUNT(*) as num FROM $tableName WHERE (`mp3_title` LIKE '%$keyword%' OR `mp3_description` LIKE '%$keyword%')";

      $targetpage = "manage_mp3.php?keyword=" . $_GET['keyword'];

      $total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query));
      $total_pages = $total_pages['num'];

      $stages = 3;
      $page = 0;
      if (isset($_GET['page'])) {
        $page = mysqli_real_escape_string($mysqli, $_GET['page']);
      }
      if ($page) {
        $start = ($page - 1) * $limit;
      } else {
        $start = 0;
      }

      $sql_query = "SELECT tbl_mp3.*, tbl_category.`cid`, tbl_category.`category_name` FROM tbl_mp3 LEFT JOIN tbl_category ON tbl_mp3.`cat_id` = tbl_category.`cid` WHERE (`mp3_title` LIKE '%$keyword%' OR `mp3_description` LIKE '%$keyword%') ORDER BY tbl_mp3.`id` DESC LIMIT $start, $limit";
    } 
    else {
      $query = "SELECT COUNT(*) as num FROM $tableName";
      $total_pages = mysqli_fetch_array(mysqli_query($mysqli, $query));
      $total_pages = $total_pages['num'];

      $stages = 3;
      $page = 0;
      if (isset($_GET['page'])) {
        $page = mysqli_real_escape_string($mysqli, $_GET['page']);
      }
      if ($page) {
        $start = ($page - 1) * $limit;
      } else {
        $start = 0;
      }

      $sql_query = "SELECT tbl_mp3.`id`,tbl_mp3.`mp3_title`,tbl_mp3.`total_views`,tbl_mp3.`total_download`,tbl_mp3.`mp3_thumbnail`, tbl_category.`cid`, tbl_category.`category_name` FROM tbl_mp3 LEFT JOIN tbl_category ON tbl_mp3.`cat_id` = tbl_category.`cid` ORDER BY tbl_mp3.`id` DESC LIMIT $start, $limit";
    }

    $result = mysqli_query($mysqli, $sql_query) or die(mysqli_error($mysqli));

    function get_total_songs($cat_id)
    {

      global $mysqli;
      $qry_songs = "SELECT COUNT(*) as num FROM tbl_mp3 WHERE cat_id='" . $cat_id . "'";
      $total_songs = mysqli_fetch_array(mysqli_query($mysqli, $qry_songs));
      $total_songs = $total_songs['num'];
      return $total_songs;
    }
?>

<div class="row">
  <div class="col-xs-12">
    <div class="card mrg_bottom">
      <div class="page_title_block">
        <div class="col-md-5 col-xs-12">
          <div class="page_title"><?= $page_title ?></div>
        </div>
        <div class="col-md-7 col-xs-12">
          <div class="search_list">
            <div class="search_block">
              <form method="get" action="">
                <input class="form-control input-sm" placeholder="在这里搜索..." aria-controls="DataTables_Table_0" type="search" name="keyword" value="<?php if (isset($_GET['keyword'])) { echo $_GET['keyword']; } ?>" required="required">
                <button type="submit" class="btn-search"><i class="fa fa-search"></i></button>
              </form>
            </div>
            <div class="add_btn_primary"> <a href="add_mp3.php">添加音乐</a> </div>
          </div>
        </div>
        <div class="clearfix"></div>
        <form id="filterForm" accept="" method="GET">
          <div class="col-md-3 col-xs-12">
            <select name="category" class="form-control select2 filter" data-type="category" style="padding: 5px 10px;height: 40px;">
              <option value="">所有分类</option>
              <?php
              if (!isset($_GET['category'])){
                $sql = "SELECT * FROM tbl_category ORDER BY category_name LIMIT 10";
              }
              else{
               $sql = "SELECT * FROM tbl_category WHERE `cid`='".$_GET['category']."' ORDER BY category_name LIMIT 10"; 
              }
              $res = mysqli_query($mysqli, $sql);
              while ($row = mysqli_fetch_array($res)) {
              ?>
                <option value="<?php echo $row['cid']; ?>" <?php if (isset($_GET['category']) && $_GET['category'] == $row['cid']) { echo 'selected'; } ?>><?php echo $row['category_name'], ' (' . get_total_songs($row['cid']) . ')'; ?></option>
              <?php
              }
              mysqli_free_result($row);
              ?>
            </select>
          </div>
          <div class="col-md-3 col-xs-12">
            <select name="album" class="form-control select2 filter" data-type="album" style="padding: 5px 10px;height: 40px;">
              <option value="">所有专辑</option>
              <?php
              if (!isset($_GET['album'])){
                $sql_album = "SELECT * FROM tbl_album ORDER BY album_name LIMIT 10";
              }
              else{
                $sql_album = "SELECT * FROM tbl_album WHERE `aid`='".$_GET['album']."' ORDER BY album_name LIMIT 10";
              }
              
              $res_album = mysqli_query($mysqli, $sql_album);
              while ($row_album = mysqli_fetch_array($res_album)) {
              ?>
                <option value="<?php echo $row_album['aid']; ?>" <?php if (isset($_GET['album']) && $_GET['album'] == $row_album['aid']) { echo 'selected'; } ?>><?php echo $row_album['album_name']; ?></option>
              <?php
              }
              ?>
            </select>
          </div>
        </form>

        <div class="col-md-4 col-xs-12 text-right" style="float: right;">
          <div>
              <div class="checkbox" style="width: 95px;margin-top: 5px;margin-left: 10px;right: 100px;position: absolute;">
                <input type="checkbox" id="checkall_input">
                <label for="checkall_input">
                    选择所有
                </label>
              </div>
              <div class="dropdown" style="float:right">
                <button class="btn btn-primary dropdown-toggle btn_cust" type="button" data-toggle="dropdown">操作
                <span class="caret"></span></button>
                <ul class="dropdown-menu" style="right:0;left:auto;">
                  <li><a href="" class="actions" data-action="enable">启用</a></li>
                  <li><a href="" class="actions" data-action="disable">禁用</a></li>
                  <li><a href="" class="actions" data-action="delete">删除 !</a></li>
                </ul>
              </div>
          </div>
        </div>

      </div>
      <div class="clearfix"></div>

      <div class="col-md-12 mrg-top">
        <div class="row">
          <?php
          $i = 0;
          while ($row = mysqli_fetch_assoc($result)) {
            $mp3_file = $row['mp3_url'] ?? '';

            if (!empty($row['mp3_type']) && $row['mp3_type'] == 'local') {
              $mp3_file = $file_path . 'uploads/' . basename($row['mp3_url']);
            }

          ?>
            <div class="col-lg-4 col-sm-6 col-xs-12">
              <div class="block_wallpaper">
                <div class="wall_category_block">
                  <h2>
                    <?php
                    if (strlen($row['category_name']) > 18) {
                      echo substr(stripslashes($row['category_name']), 0, 18) . '...';
                    } else {
                      echo $row['category_name'];
                    }
                    ?>
                  </h2>

                  <div class="checkbox" style="float: right;">
                    <input type="checkbox" name="post_ids[]" id="checkbox<?php echo $i; ?>" value="<?php echo $row['id']; ?>" class="post_ids">
                    <label for="checkbox<?php echo $i; ?>">
                    </label>
                  </div>
                </div>
                <div class="wall_image_title">
                  <p>
                    <?php
                    if (strlen($row['mp3_title']) > 30) {
                      echo substr(stripslashes($row['mp3_title']), 0, 29) . '...';
                    } else {
                      echo $row['mp3_title'];
                    }
                    ?>
                  </p>
                  <ul>
                    <li><a href="javascript:void(0)" data-toggle="tooltip" data-tooltip="<?php echo thousandsNumberFormat($row['total_views']); ?> 聆听"><i class="fa fa-eye"></i></a></li>

                    <li><a href="javascript:void(0)" data-toggle="tooltip" data-tooltip="<?php echo thousandsNumberFormat($row['total_download']); ?> 下载"><i class="fa fa-download"></i></a></li>

                    <li><a href="javascript:void(0)" data-toggle="tooltip" data-tooltip="<?php echo $row['rate_avg']; ?> 评价"><i class="fa fa-star"></i></a></li>

                    <li><a href="edit_mp3.php?mp3_id=<?php echo $row['id']; ?>&redirect=<?= $redirectUrl ?>" data-toggle="tooltip" data-tooltip="编辑"><i class="fa fa-edit"></i></a></li>

                    <li>
                      <a href="" class="btn_delete_a" data-id="<?php echo $row['id']; ?>" data-toggle="tooltip" data-tooltip="删除"><i class="fa fa-trash"></i></a>
                    </li>

                    <?php if($row['status']!="0"){?>
                      <li><div class="row toggle_btn"><a href="javascript:void(0)" data-id="<?php echo $row['id'];?>" data-action="deactive" data-column="status" data-toggle="tooltip" data-tooltip="启用"><img src="assets/images/btn_enabled.png" alt="" /></a></div></li>

                    <?php }else{?>
                      <li><div class="row toggle_btn"><a href="javascript:void(0)" data-id="<?php echo $row['id'];?>" data-action="active" data-column="status" data-toggle="tooltip" data-tooltip="禁用"><img src="assets/images/btn_disabled.png" alt="" /></a></div></li>
                    <?php }?>

                  </ul>
                </div>
                <span><img src="images/<?php echo $row['mp3_thumbnail']; ?>" /></span>
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
    var _table='tbl_mp3';

    $.ajax({
      type:'post',
      url:'processData.php',
      dataType:'json',
      data:{id:_id,for_action:_for,column:_column,table:_table,'action':'toggle_status','tbl_id':'id'},
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
      var _table='tbl_mp3';

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
                      text: "歌曲被删除.", 
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

  $(".actions").click(function(e){
      e.preventDefault();

      var _ids = $.map($('.post_ids:checked'), function(c){return c.value; });
      var _action=$(this).data("action");

      if(_ids!='')
      {
        swal({
          title: "你真的想执行吗?",
          type: "warning",
          showCancelButton: true,
          confirmButtonClass: "btn-danger btn_edit",
          cancelButtonClass: "btn-warning btn_edit",
          confirmButtonText: "确定",
          cancelButtonText: "放弃",
          closeOnConfirm: false,
          closeOnCancel: false,
          showLoaderOnConfirm: true
        },
        function(isConfirm) {
          if (isConfirm) {

            var _table='tbl_mp3';

            $.ajax({
              type:'post',
              url:'processData.php',
              dataType:'json',
              data:{id:_ids,for_action:_action,table:_table,'action':'multi_action'},
              success:function(res){
                  console.log(res);
                  $('.notifyjs-corner').empty();
                  if(res.status=='1'){
                    swal({
                        title: "成功", 
                        text: "删除成功", 
                        type: "success"
                    },function() {
                        location.reload();
                    });
                  }
                }
            });
          }
          else{
            swal.close();
          }

        });
      }
      else{
        swal("抱歉没有选择数据 !!")
      }
   });


  var totalItems=0;

  $("#checkall_input").click(function () {

    totalItems=0;

    $('input:checkbox').not(this).prop('checked', this.checked);
    $.each($("input[name='post_ids[]']:checked"), function(){
      totalItems=totalItems+1;
    });

    if($('input:checkbox').prop("checked") == true){
      $('.notifyjs-corner').empty();
      $.notify(
        'Total '+totalItems+' item checked',
        { position:"top center",className: 'success'}
      );
    }
    else if($('input:checkbox'). prop("checked") == false){
      totalItems=0;
      $('.notifyjs-corner').empty();
    }
  });

  var noteOption = {
      clickToHide : false,
      autoHide : false,
  }

  $.notify.defaults(noteOption);

  $(".post_ids").click(function(e){

      if($(this).prop("checked") == true){
        totalItems=totalItems+1;
      }
      else if($(this). prop("checked") == false){
        totalItems = totalItems-1;
      }

      if(totalItems==0){
        $('.notifyjs-corner').empty();
        exit;
      }

      $('.notifyjs-corner').empty();

      $.notify(
        'Total '+totalItems+' item checked',
        { position:"top center",className: 'success'}
      );
  });


  $(".filter").on("change", function(e) {
    $("#filterForm *").filter(":input").each(function() {
      if ($(this).val() == '')
        $(this).prop("disabled", true);
    });
    $("#filterForm").submit();
  });


  $(function(){
    $('.select2').select2({
      ajax: {
        url: 'getData.php',
        dataType: 'json',
        delay: 250,
        data: function (params) {
          var query = {
            type: $(this).data("type"),
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
</script>