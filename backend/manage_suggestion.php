<?php 

    $page_title="建议歌曲";

    include("includes/header.php");
    require("includes/function.php");
    require("language/language.php");

    $sql_query="SELECT suggest.*, user.`name` FROM tbl_song_suggest suggest 
          LEFT JOIN tbl_users user
          ON suggest.`user_id`=user.`id` ORDER BY suggest.`id` DESC";

    $result=mysqli_query($mysqli,$sql_query) or die(mysqli_error($mysqli));
?>

<link rel="stylesheet" type="text/css" href="assets/css/stylish-tooltip.css">

<div class="row">
  <div class="col-xs-12">
    <div class="card mrg_bottom">
      <div class="page_title_block">
        <div class="col-md-5 col-xs-12">
          <div class="page_title"><?=$page_title?></div>
        </div>
      </div>
      <div class="clearfix"></div>
         <div class="col-md-12 mrg-top">
          <table class="datatable table table-striped table-bordered table-hover">
            <thead>
              <tr>
                <th>#</th>
                <th>用户</th>
                <th>音乐标题</th>
                <th>图片</th> 
                <th>信息</th> 
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
             <?php

             $i=1;
             while($row=mysqli_fetch_array($result))
             {
              ?>
              <tr>
                <td width="50"><?=$i++?></td>
                <td style="word-wrap: break-all;"><?php echo $row['name'];?></td>
                <td style="word-wrap: break-all;"><?php echo $row['song_title'];?></td>
                <td nowrap="">
                  <?php 
                  if(file_exists('images/'.$row['song_image'])){
                    ?>
                    <span class="mytooltip tooltip-effect-3">
                      <span class="tooltip-item">
                        <img src="images/<?php echo $row['song_image'];?>" alt="no image" style="width: 100px;height: auto;max-height: 100px;border-radius: 5px">
                      </span> 
                      <span class="tooltip-content clearfix">
                        <a href="images/<?php echo $row['song_image'];?>" target="_blank"><img src="images/<?php echo $row['song_image'];?>" alt="no image" /></a>
                      </span>
                    </span>
                  <?php }else{
                    ?>
                    <img src="" alt="no image" style="width: 100px;height: auto;border-radius: 5px">
                    <?php
                  } ?>
                </td>
                <td style="word-wrap: break-all;"><?php echo $row['message'];?></td> 

                <td>
                  <a href="" class="btn btn-danger btn_delete btn_delete_a" data-id="<?php echo $row['id'];?>"  data-toggle="tooltip" data-tooltip="删除"><i class="fa fa-trash"></i></a>
                </td>
              </tr>
              <?php
                }
            ?>
          </tbody>
        </table>
      </div>
     <div class="clearfix"></div>
   </div>
 </div>
</div>            


<?php include("includes/footer.php");?>

<script type="text/javascript">
  $('a[data-toggle="tooltip"]').tooltip({
    animated: 'fade',
    placement: 'bottom',
    html: true
  });

  $(".btn_delete_a").click(function(e){

      e.preventDefault();

      var _ids=$(this).data("id");
      var _table='tbl_song_suggest';

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
                      text: "建议歌曲被删除.", 
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
