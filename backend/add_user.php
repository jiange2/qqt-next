<?php 
    
    $page_title=(!isset($_GET['user_id'])) ? '添加用户' : '编辑用户';

    include('includes/header.php');

    include('includes/function.php');
    include('language/language.php');

    if(isset($_POST['submit']) and isset($_GET['add']))
    {

        $sql="SELECT * FROM tbl_users WHERE `email` = '".trim($_POST['email'])."'";
        $res=mysqli_query($mysqli, $sql);

        if(mysqli_num_rows($res) == 0){

            $data = array(
                'user_type'=>'Normal',  
                'name'  =>  cleanInput($_POST['name']),
                'email'  =>  cleanInput($_POST['email']),
                'password'  =>  md5(trim($_POST['password'])),
                'phone'  =>  cleanInput($_POST['phone']),
                'registered_on'  =>  strtotime(date('d-m-Y h:i:s A')),
            );

            $qry = Insert('tbl_users',$data);

            $_SESSION['class']="success";
            $_SESSION['msg']="10";
        }
        else{
            $_SESSION['class']="warn";
            $_SESSION['msg']="email_exist";
        }

        header("location:manage_users.php");	 
        exit;
    }

    if(isset($_GET['user_id']))
    {
        $user_qry="SELECT * FROM tbl_users where id='".$_GET['user_id']."'";
        $user_result=mysqli_query($mysqli,$user_qry);
        $user_row=mysqli_fetch_assoc($user_result);
    }

    if(isset($_POST['submit']) and isset($_POST['user_id']))
    {

        if(!filter_var($_POST['email'], FILTER_VALIDATE_EMAIL)) 
        {
            $_SESSION['class']="warn";
            $_SESSION['msg']="invalid_email_format";
            header("Location:add_user.php?user_id=".$_POST['user_id']);
            exit;
        }
        else{

            $email=cleanInput($_POST['email']);

            $sql="SELECT * FROM tbl_users WHERE `email` = '$email' AND `id` <> '".$_POST['user_id']."'";

            $res=mysqli_query($mysqli, $sql);

            if(mysqli_num_rows($res) == 0){
                $data = array(
                    'name'  =>  cleanInput($_POST['name']),
                    'email'  =>  cleanInput($_POST['email']),
                    'phone'  =>  cleanInput($_POST['phone']),
                );

                if(isset($_POST['password']) && $_POST['password']!="")
                {

                    $password=md5(trim($_POST['password']));

                    $data = array_merge($data, array("password"=>$password));
                }

                $user_edit=Update('tbl_users', $data, "WHERE id = '".$_POST['user_id']."'");

                $_SESSION['class']="success";

                $_SESSION['msg']="11";
            }
            else{
                $_SESSION['class']="warn";
                $_SESSION['msg']="email_exist";
                
                header("Location:add_user.php?user_id=".$_POST['user_id']);
                exit;
            }
        }

        if(isset($_GET['redirect'])){
          header("Location:".$_GET['redirect']);
        }
        else{
          header("Location:add_user.php?user_id=".$_POST['user_id']);
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
        <form action="" method="post" class="form form-horizontal" enctype="multipart/form-data" >
          <input  type="hidden" name="user_id" value="<?php echo $_GET['user_id'];?>" />
          <div class="section">
            <div class="section-body">
              <div class="form-group">
                <label class="col-md-3 control-label">名称 :-</label>
                <div class="col-md-6">
                  <input type="text" name="name" id="name" value="<?php if(isset($_GET['user_id'])){echo $user_row['name'];}?>" class="form-control" required>
                </div>
              </div>
              <div class="form-group">
                <label class="col-md-3 control-label">电子邮件 :-</label>
                <div class="col-md-6">
                  <input type="email" name="email" id="email" value="<?php if(isset($_GET['user_id'])){echo $user_row['email'];}?>" class="form-control" <?=(isset($_GET['user_id']) AND $user_row['user_type']!='Normal') ? 'readonly' : ''?>>
                </div>
              </div>
              <?php 
                if(!isset($_GET['user_id']) OR $user_row['user_type']=='Normal')
                {
              ?>
              <div class="form-group">
                <label class="col-md-3 control-label">密码 :-</label>
                <div class="col-md-6">
                  <input type="password" name="password" id="password" value="" class="form-control" <?php if(!isset($_GET['user_id'])){?>required<?php }?>>
                </div>
              </div>
              <?php } ?>
              <div class="form-group">
                <label class="col-md-3 control-label">手机号码 :-</label>
                <div class="col-md-6">
                  <input type="text" name="phone" id="phone" value="<?php if(isset($_GET['user_id'])){echo $user_row['phone'];}?>" class="form-control">
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
   

<?php include('includes/footer.php');?>
