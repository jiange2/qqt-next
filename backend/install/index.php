<!DOCTYPE html>
<html>
	<head>
		<meta charset="utf-8"/>
		<title>Android Online MP3 - Installer</title>
		<meta name="viewport" content="width=device-width, initial-scale=1">
		<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.4.1/css/bootstrap.min.css">
		<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/bulma/0.7.5/css/bulma.min.css"/>
		<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.css"/>

		<style type="text/css">
			body, html {
				background: #F7F7F7;
			}
			.install_btn button{
				font-size: 16px !important;
				font-weight: 600;
			}
			.install_btn button{
				font-size: 16px !important;
				font-weight: 600;
			}
			.btn_ios{
				background: #000000;
				border-color: #000000;
				color: #FFF !important;
			}
			.btn_ios:hover{
				background: rgba(0,0,0,0.9);
				border-color: #000000 !important;
			}
			.btn_bundle{
				background: rgba(28, 108, 195, 1);
				border-color: #1c6cc3 !important;
				color: #FFF !important;
			}
			.btn_bundle:hover{
				background: rgba(28, 108, 195, 0.9);
				border-color: #1c6cc3 !important;
			}
		</style>
	</head>
	<body>
		<div class="container"> 
			<div class="section">
				<div class="column is-6 is-offset-3">
					<center>
						<h1 class="title" style="padding-top: 20px">Android Online MP3 Install Options</h1><br>
					</center>
					<div class="box">
						<div class='notification is-warning' style='padding:12px;'><strong>Note:</strong> Please select any one for install Android Online MP3</div>

					<div class="install_btn">
						<button class="btn_android btn btn-success btn-block btn-lg" onclick="window.location.href='android_install.php'" style="border-radius: 0px">
							<i class="fa fa-android icon" aria-hidden="true"></i> Android Online MP3 Install
						</button>
						<button class="btn_bundle btn btn-block btn-lg" onclick="window.location.href='bundle_install.php'" style="border-radius: 0px">
							<i class="fa fa-archive icon" aria-hidden="true"></i> Bundle Android Online MP3 Install
						</button>
					</div>
					<div style="clear: both;"></div>
					<br/>
					</div>
				</div>
			</div>
		</div>
	</body>
</html>