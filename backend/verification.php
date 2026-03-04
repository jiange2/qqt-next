<?php

    $page_title = "包名设置";

    include("includes/connection.php");
    include("includes/header.php");
    require("includes/function.php");
    require("language/language.php");

    $qry = "SELECT * FROM tbl_settings where id='1'";
    $result = mysqli_query($mysqli, $qry);
    $settings_row = mysqli_fetch_assoc($result);

    if (isset($_POST['verify_purchase_submit'])) {

        $data = array(
            'package_name' => trim($_POST['package_name'])
        );

        $settings_edit = Update('tbl_settings', $data, "WHERE id = '1'");

        $_SESSION['class'] = "success";
        $_SESSION['msg'] = "11";
        header("Location:verification.php");
        exit;
    }

?>

<div class="row">
    <div class="col-md-12">
        <div class="card">
            <div class="page_title_block">
                <div class="col-md-5 col-xs-12">
                    <div class="page_title"><?= $page_title ?></div>
                </div>
            </div>
            <div class="clearfix"></div>
            <div class="card-body mrg_bottom">

                <form action="" name="verify_purchase" method="post" class="form form-horizontal" enctype="multipart/form-data" id="api_form">
                    <input type="hidden" class="current_tab" name="current_tab">
                    <div class="section">
                        <div class="section-body">
                            
                            <div class="form-group">
                                <label class="col-md-4 control-label">Android 包名 :-
                                    <p class="control-label-help">(需要与源代码里面的名称一样)</p>
                                </label>
                                <div class="col-md-6">
                                    <input type="text" name="package_name" id="package_name" value="<?php echo $settings_row['package_name']; ?>" class="form-control" placeholder="com.example.myapp">
                                </div>
                            </div>

                            <div class="form-group">
                                <div class="col-md-9 col-md-offset-4">
                                    <button type="submit" name="verify_purchase_submit" class="btn btn-primary">保存</button>
                                </div>
                            </div>
                        </div>
                    </div>

                </form>
                <br />
                <div class="alert alert-danger alert-dismissible fade in" role="alert">
                    <h4 id="oh-snap!-you-got-an-error!">提示:<a class="anchorjs-link" href="#oh-snap!-you-got-an-error!"><span class="anchorjs-icon"></span></a></h4>
                    <p style="margin-bottom: 10px"><i class="fa fa-hand-o-right"></i> 需要与代码里面的包名需要一样不然程序不能工作</p>
                </div>
            </div>
        </div>
    </div>
</div>


<?php include("includes/footer.php"); ?>