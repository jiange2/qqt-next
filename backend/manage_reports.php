<?php 
    
    $page_title="管理举报";

    include("includes/header.php");
    require("includes/function.php");
    require("language/language.php");


    $sql_query="SELECT tbl_reports.*, tbl_users.`name`, tbl_users.`email`, tbl_mp3.`mp3_title` 
                FROM tbl_reports
                LEFT JOIN tbl_mp3 ON tbl_reports.`song_id`=tbl_mp3.`id`
                LEFT JOIN tbl_users ON tbl_reports.`user_id`=tbl_users.`id`
                ORDER BY tbl_reports.`id` DESC";

    $result=mysqli_query($mysqli,$sql_query);

?>
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
                <th>名称</th>
                <th>电子邮件</th>
                <th>歌曲</th>
                <th>举报</th> 
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
                <td style="word-wrap: break-all;"><?php echo $row['email'];?></td>
                <td style="word-wrap: break-all;"><?php echo $row['mp3_title'];?></td>
                <td style="word-wrap: break-all;"><?php echo $row['report'];?></td>                  
                <td nowrap="">
                  <a href="javascript:void(0)" data-id="<?php echo $row['id'];?>" data-toggle="tooltip" data-tooltip="删除" class="btn btn-danger btn_delete btn_delete_a">
                    <i class="fa fa-trash"></i>
                  </a>
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

  $(".btn_delete_a").click(function(e){

      e.preventDefault();

      var _ids=$(this).data("id");
      var _table='tbl_reports';

      swal({
          title: "你确定删除这个吗?",
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
              data:{id:_ids,'action':'removeData','tbl_nm':_table,"tbl_id":"id"},
              success:function(res){
                console.log(res);
                if(res.status=='1'){
                  swal({
                      title: "成功", 
                      text: "举报被删除.", 
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
