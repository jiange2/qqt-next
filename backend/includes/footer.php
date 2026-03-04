<footer class="app-footer">
  <div class="row">
    <div class="col-xs-12">
      <div class="footer-copyright"><?php echo date('Y'); ?> <a href="" target="_blank"></a></div>
    </div>
  </div>
</footer>
</div>
</div>
<script type="text/javascript" src="assets/js/vendor.js"></script>
<script type="text/javascript" src="assets/js/app.js"></script>

<script src="assets/js/notify.min.js"></script>

<link rel="stylesheet" href="//code.jquery.com/ui/1.12.1/themes/base/jquery-ui.css">
<script src="https://code.jquery.com/ui/1.12.1/jquery-ui.js"></script>

<script type="text/javascript" src="assets/sweetalert/sweetalert.min.js"></script>

<script type="text/javascript">
  function render_upload_image(input, target) {

    if (input.files && input.files[0]) {
      var reader = new FileReader();

      reader.onload = function(e) {
        target.attr('src', e.target.result);
      }

      reader.readAsDataURL(input.files[0]);
    }
  }

  function isImage(filename) {
    var ext = getExtension(filename);
    switch (ext.toLowerCase()) {
      case 'jpg':
      case 'jpeg':
      case 'png':
      case 'svg':
      case 'gif':
        return true;
    }
    return false;
  }

  function getExtension(filename) {
    var parts = filename.split('.');
    return parts[parts.length - 1];
  }
</script>

<?php if (isset($_SESSION['msg'])) { ?>
  <script type="text/javascript">
    $('.notifyjs-corner').empty();
    $.notify(
      '<?php echo $client_lang[$_SESSION["msg"]]; ?>', {
        position: "top center",
        className: '<?= $_SESSION["class"] ?>'
      }
    );
  </script>
<?php
  unset($_SESSION['msg']);
  unset($_SESSION['class']);
}
?>

</body>

</html>