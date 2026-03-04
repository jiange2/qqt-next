<?php 

    $page_title="仪表盘";
    include("includes/header.php");
    include("includes/function.php");

    $qry_cat = "SELECT COUNT(*) as num FROM tbl_category";
    $total_category = mysqli_fetch_array(mysqli_query($mysqli, $qry_cat));
    $total_category = $total_category['num'];

    $qry_art = "SELECT COUNT(*) as num FROM tbl_artist";
    $total_artist = mysqli_fetch_array(mysqli_query($mysqli, $qry_art));
    $total_artist = $total_artist['num'];

    $qry_mp3 = "SELECT COUNT(*) as num FROM tbl_mp3";
    $total_mp3 = mysqli_fetch_array(mysqli_query($mysqli, $qry_mp3));
    $total_mp3 = $total_mp3['num'];


    $qry_album = "SELECT COUNT(*) as num FROM tbl_album";
    $total_album = mysqli_fetch_array(mysqli_query($mysqli, $qry_album));
    $total_album = $total_album['num'];


    $qry_playlist = "SELECT COUNT(*) as num FROM tbl_playlist";
    $total_playlist = mysqli_fetch_array(mysqli_query($mysqli, $qry_playlist));
    $total_playlist = $total_playlist['num'];

    $qry_users = "SELECT COUNT(*) as num FROM tbl_users";
    $total_users = mysqli_fetch_array(mysqli_query($mysqli, $qry_users));
    $total_users = $total_users['num'];

    $qry_banner = "SELECT COUNT(*) as num FROM tbl_banner";
    $total_banner = mysqli_fetch_array(mysqli_query($mysqli, $qry_banner));
    $total_banner = $total_banner['num'];

    $qry_song_suggest = "SELECT COUNT(*) as num FROM tbl_song_suggest";
    $total_song_suggest = mysqli_fetch_array(mysqli_query($mysqli, $qry_song_suggest));
    $total_song_suggest = $total_song_suggest['num'];


    $qry_reports = "SELECT COUNT(*) as num FROM tbl_reports";
    $total_reports = mysqli_fetch_array(mysqli_query($mysqli, $qry_reports));
    $total_reports = $total_reports['num'];

    $countStr='';
    $no_data_status=false;
    $count=$monthCount=0;

    for ($mon=1; $mon<=12; $mon++) {

        if(date('n') < $mon){
          break;
        }

        if(isset($_GET['filterByYear'])){

          $year=$_GET['filterByYear'];

          $month = date('M', mktime(0,0,0,$mon, 1, $year));

          $sql_user="SELECT `id` FROM tbl_users WHERE `id` <> 0 AND DATE_FORMAT(FROM_UNIXTIME(`registered_on`), '%c') = '$mon' AND DATE_FORMAT(FROM_UNIXTIME(`registered_on`), '%Y') = '$year'";
        }
        else{

          $month = date('M', mktime(0,0,0,$mon, 1, date('Y')));

          $sql_user="SELECT `id` FROM tbl_users WHERE `id` <> 0 AND DATE_FORMAT(FROM_UNIXTIME(`registered_on`), '%c') = '$mon'";
        }

        $count=mysqli_num_rows(mysqli_query($mysqli, $sql_user));

        $countStr.="['".$month."', ".$count."], ";

        if($count!=0){
          $monthCount++;
        }

    }

    if($monthCount!=0){
      $no_data_status=false;
    }
    else{
      $no_data_status=true;
    }

    $countStr=rtrim($countStr, ", ");

?>

<?php 
  $sql_smtp="SELECT * FROM tbl_smtp_settings WHERE id='1'";
  $res_smtp=mysqli_query($mysqli,$sql_smtp);
  $row_smtp=mysqli_fetch_assoc($res_smtp);

  $smtp_warning=true;

  if(!empty($row_smtp))
  {

    if($row_smtp['smtp_type']=='server'){
      if($row_smtp['smtp_host']!='' AND $row_smtp['smtp_email']!=''){
        $smtp_warning=false;
      }
      else{
        $smtp_warning=true;
      }  
    }
    else if($row_smtp['smtp_type']=='gmail'){
      if($row_smtp['smtp_ghost']!='' AND $row_smtp['smtp_gemail']!=''){
        $smtp_warning=false;
      }
      else{
        $smtp_warning=true;
      }  
    }
  }

  if($smtp_warning)
  {
?>
<div class="row">
  <div class="col-lg-12 col-md-12 col-sm-12 col-xs-12">
    <div class="alert alert-danger alert-dismissible fade in" role="alert">
    <h4 id="oh-snap!-you-got-an-error!"><i class="fa fa-exclamation-triangle"></i> SMTP 还没有设置<a class="anchorjs-link" href="#oh-snap!-you-got-an-error!"><span class="anchorjs-icon"></span></a></h4>
        <p style="margin-bottom: 10px">没有配置SMTP否则 <strong>忘记密码</strong> 或者 <strong>电子邮件</strong> 功能将不能工作.</p> 
    </div>
  </div>
</div>
<?php } ?>

<div class="row">
  <div class="col-lg-4 col-md-6 col-sm-6 col-xs-12"> <a href="manage_category.php" class="card card-banner card-alicerose-light">
      <div class="card-body"> <i class="icon fa fa-sitemap fa-4x"></i>
        <div class="content">
          <div class="title">音乐分类</div>
          <div class="value"><span class="sign"></span><?php echo thousandsNumberFormat($total_category); ?></div>
        </div>
      </div>
    </a>
  </div>
  <div class="col-lg-4 col-md-6 col-sm-6 col-xs-12"> <a href="manage_artist.php" class="card card-banner card-green-light">
      <div class="card-body"> <i class="icon fa fa-buysellads fa-4x"></i>
        <div class="content">
          <div class="title">音乐歌手</div>
          <div class="value"><span class="sign"></span><?php echo thousandsNumberFormat($total_artist); ?></div>
        </div>
      </div>
    </a>
  </div>
  <div class="col-lg-4 col-md-6 col-sm-6 col-xs-12"> <a href="manage_album.php" class="card card-banner card-yellow-light">
      <div class="card-body"> <i class="icon fa fa-image fa-4x"></i>
        <div class="content">
          <div class="title">音乐专辑</div>
          <div class="value"><span class="sign"></span><?php echo thousandsNumberFormat($total_album); ?></div>
        </div>
      </div>
    </a>
  </div>
  <div class="col-lg-4 col-md-6 col-sm-6 col-xs-12"> <a href="manage_mp3.php" class="card card-banner card-blue-light">
      <div class="card-body"> <i class="icon fa fa-music fa-4x"></i>
        <div class="content">
          <div class="title"> MP3歌曲</div>
          <div class="value"><span class="sign"></span><?php echo thousandsNumberFormat($total_mp3); ?></div>
        </div>
      </div>
    </a>
  </div>
  <div class="col-lg-4 col-md-6 col-sm-6 col-xs-12"> <a href="manage_playlist.php" class="card card-banner card-orange-light">
      <div class="card-body"> <i class="icon fa fa-list fa-4x"></i>
        <div class="content">
          <div class="title">播放列表</div>
          <div class="value"><span class="sign"></span><?php echo thousandsNumberFormat($total_playlist); ?></div>
        </div>
      </div>
    </a>
  </div>
  <div class="col-lg-4 col-md-6 col-sm-6 col-xs-12"> <a href="manage_users.php" class="card card-banner card-pink-light">
      <div class="card-body"> <i class="icon fa fa-users fa-4x"></i>
        <div class="content">
          <div class="title">用户管理</div>
          <div class="value"><span class="sign"></span><?php echo thousandsNumberFormat($total_users); ?></div>
        </div>
      </div>
    </a>
  </div>
  <div class="col-lg-4 col-md-6 col-sm-6 col-xs-12"> <a href="manage_banners.php" class="card card-banner card-skyeblue-light">
      <div class="card-body"> <i class="icon fa fa-sliders fa-4x"></i>
        <div class="content">
          <div class="title">首页横幅</div>
          <div class="value"><span class="sign"></span><?php echo thousandsNumberFormat($total_banner); ?></div>
        </div>
      </div>
    </a>
  </div>
  <div class="col-lg-4 col-md-6 col-sm-6 col-xs-12"> <a href="manage_suggestion.php" class="card card-banner card-alicerose-light">
      <div class="card-body"> <i class="icon fa fa-comments fa-4x"></i>
        <div class="content">
          <div class="title">建议歌曲</div>
          <div class="value"><span class="sign"></span><?php echo thousandsNumberFormat($total_song_suggest); ?></div>
        </div>
      </div>
    </a>
  </div>
  <div class="col-lg-4 col-md-6 col-sm-6 col-xs-12"> <a href="manage_reports.php" class="card card-banner card-aliceblue-light">
      <div class="card-body"> <i class="icon fa fa-bug fa-4x"></i>
        <div class="content">
          <div class="title">举报管理</div>
          <div class="value"><span class="sign"></span><?php echo thousandsNumberFormat($total_reports); ?></div>
        </div>
      </div>
    </a>
  </div>
</div>

<div class="row">
  <div class="col-lg-12">
    <div class="container-fluid" style="background: #FFF;box-shadow: 0px 5px 10px 0px #CCC;border-radius: 2px;">
      <div class="col-lg-10">
        <h3>用户分析</h3>
        <p>新的注册</p>
      </div>
      <div class="col-lg-2" style="padding-top: 20px">
        <form method="get" id="graphFilter">
          <select class="form-control" name="filterByYear" style="box-shadow: none;height: auto;border-radius: 0px;font-size: 16px;">
            <?php 
              $currentYear=date('Y');
              $minYear=2020;

              for ($i=$currentYear; $i >= $minYear ; $i--) { 
                ?>
                <option value="<?=$i?>" <?=(isset($_GET['filterByYear']) && $_GET['filterByYear']==$i) ? 'selected' : ''?>><?=$i?></option>
                <?php
              }
            ?>
          </select>
        </form>
      </div>
      <div class="col-lg-12">
        <?php 
          if($no_data_status){
            ?>
            <h3 class="text-muted text-center" style="padding-bottom: 2em">没有查找到数据 !</h3>
            <?php
          }
          else{
            ?>
            <div id="registerChart">
              <p style="text-align: center;"><i class="fa fa-spinner fa-spin" style="font-size:3em;color:#aaa;margin-bottom:50px" aria-hidden="true"></i></p>
            </div>
            <?php    
          }
        ?>
      </div>
    </div>
  </div>
</div>


<?php include("includes/footer.php"); ?>

<?php 
    if(!$no_data_status){
?>

<script type="text/javascript" src="https://www.gstatic.com/charts/loader.js"></script>

<script type="text/javascript">
    google.charts.load('current', {packages: ['corechart', 'line']});
    google.charts.setOnLoadCallback(drawChart);

    function drawChart() {

      var data = new google.visualization.DataTable();
      data.addColumn('string', 'Month');
      data.addColumn('number', 'Users');

      data.addRows([<?=$countStr?>]);

      var options = {
        curveType: 'function',
        fontSize: 15,
        hAxis: {
          title: "Months of <?=(isset($_GET['filterByYear'])) ? $_GET['filterByYear'] : date('Y')?>",
          titleTextStyle: {
            color: '#000',
            bold:'true',
            italic: false
          },
        },
        vAxis: {
          title: "Nos of Users",
          titleTextStyle: {
            color: '#000',
            bold:'true',
            italic: false,
          },
          gridlines: { count: 5},
          format: '#',
          viewWindowMode: "explicit", viewWindow:{ min: 0 },
        },
        height: 400,
        chartArea:{
          left:100,top:20,width:'100%',height:'auto'
        },
        legend: {
            position: 'none'
        },
        lineWidth:4,
        animation: {
          startup: true,
          duration: 1200,
          easing: 'out',
        },
        pointSize: 5,
        pointShape: "circle",

      };
      var chart = new google.visualization.LineChart(document.getElementById('registerChart'));

      chart.draw(data, options);
    }

    $(document).ready(function () {
        $(window).resize(function(){
            drawChart();
        });
    });
</script>

<?php } ?>

<script type="text/javascript">
  
  // filter of graph
  $("select[name='filterByYear']").on("change",function(e){
    $("#graphFilter").submit();
  });

</script>