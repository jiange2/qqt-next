<template>
  <div v-loading="loading" style="max-width: 760px">
    <el-tabs>
      <el-tab-pane label="基础信息">
        <el-form label-width="140px">
          <el-form-item label="包名"><el-input v-model="f.packageName" /></el-form-item>
          <el-form-item label="应用名"><el-input v-model="f.appName" /></el-form-item>
          <el-form-item label="应用 Logo"><el-input v-model="f.appLogo" /></el-form-item>
          <el-form-item label="应用邮箱"><el-input v-model="f.appEmail" /></el-form-item>
          <el-form-item label="版本号"><el-input v-model="f.appVersion" /></el-form-item>
          <el-form-item label="作者"><el-input v-model="f.appAuthor" /></el-form-item>
          <el-form-item label="联系方式"><el-input v-model="f.appContact" /></el-form-item>
          <el-form-item label="网站"><el-input v-model="f.appWebsite" /></el-form-item>
          <el-form-item label="开发者"><el-input v-model="f.appDevelopedBy" /></el-form-item>
          <el-form-item label="应用描述"><el-input v-model="f.appDescription" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="隐私政策"><el-input v-model="f.appPrivacyPolicy" type="textarea" :rows="4" /></el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="广告">
        <el-form label-width="140px">
          <el-form-item label="Publisher ID"><el-input v-model="f.publisherId" /></el-form-item>
          <el-form-item label="插屏开关"><el-input v-model="f.interstitalAd" placeholder="true / false" /></el-form-item>
          <el-form-item label="插屏广告类型">
            <el-radio-group v-model="f.interstitalAdType">
              <el-radio value="admob">AdMob</el-radio>
              <el-radio value="facebook">Facebook</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="AdMob 插屏 ID"><el-input v-model="f.interstitalAdId" /></el-form-item>
          <el-form-item label="插屏点击数"><el-input v-model="f.interstitalAdClick" /></el-form-item>
          <el-form-item label="Facebook 插屏 ID"><el-input v-model="f.interstitalFacebookId" /></el-form-item>
          <el-form-item label="横幅开关"><el-input v-model="f.bannerAd" placeholder="true / false" /></el-form-item>
          <el-form-item label="横幅广告类型">
            <el-radio-group v-model="f.bannerAdType">
              <el-radio value="admob">AdMob</el-radio>
              <el-radio value="facebook">Facebook</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="AdMob 横幅 ID"><el-input v-model="f.bannerAdId" /></el-form-item>
          <el-form-item label="Facebook 横幅 ID"><el-input v-model="f.bannerFacebookId" /></el-form-item>
          <el-form-item label="原生广告开关"><el-input v-model="f.nativeAd" placeholder="true / false" /></el-form-item>
          <el-form-item label="原生广告类型">
            <el-radio-group v-model="f.nativeAdType">
              <el-radio value="admob">AdMob</el-radio>
              <el-radio value="facebook">Facebook</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="原生广告 ID"><el-input v-model="f.nativeAdId" /></el-form-item>
          <el-form-item label="Facebook 原生 ID"><el-input v-model="f.nativeFacebookId" /></el-form-item>
          <el-form-item label="原生位置"><el-input-number v-model="f.nativePosition" :min="1" /></el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="版本更新">
        <el-form label-width="140px">
          <el-form-item label="更新开关"><el-input v-model="f.appUpdateStatus" placeholder="true / false" /></el-form-item>
          <el-form-item label="新版本号"><el-input-number v-model="f.appNewVersion" :step="0.1" /></el-form-item>
          <el-form-item label="更新描述"><el-input v-model="f.appUpdateDesc" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="跳转地址"><el-input v-model="f.appRedirectUrl" /></el-form-item>
          <el-form-item label="允许取消更新"><el-input v-model="f.cancelUpdateStatus" placeholder="true / false" /></el-form-item>
          <el-form-item label="下载开关"><el-input v-model="f.songDownload" placeholder="true / false" /></el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="API / OneSignal">
        <el-form label-width="140px">
          <!-- 分类排序/分类歌曲排序两个死配置已从表单移除（backend-next ADR 0007/0009），settings 表列保留 -->
          <el-form-item label="最新歌曲条数"><el-input-number v-model="f.apiLatestLimit" :min="1" /></el-form-item>
          <el-form-item label="OneSignal App ID"><el-input v-model="f.onesignalAppId" /></el-form-item>
          <el-form-item label="OneSignal REST Key"><el-input v-model="f.onesignalRestKey" show-password /></el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="隐私模式">
        <el-form label-width="140px">
          <el-form-item label="隐私模式开关">
            <el-switch v-model="f.privacyMode" active-value="true" inactive-value="false" active-text="开启" inactive-text="关闭" />
          </el-form-item>
          <div class="hint">开启后 App 端仅显示标记为「公开」的内容。歌曲、专辑、分类的隐私标记独立于上下架状态。新上传的内容默认为隐私。</div>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <div style="margin-top: 12px">
      <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { api } from "../api";

type Settings = {
  packageName: string; onesignalAppId: string; onesignalRestKey: string;
  appName: string; appLogo: string; appEmail: string; appVersion: string;
  appAuthor: string; appContact: string; appWebsite: string;
  appDescription: string; appDevelopedBy: string; appPrivacyPolicy: string;
  apiLatestLimit: number;
  publisherId: string; interstitalAd: string; interstitalAdId: string;
  interstitalAdClick: string; bannerAd: string; bannerAdId: string;
  bannerAdType: string; bannerFacebookId: string; interstitalAdType: string;
  interstitalFacebookId: string; nativeAd: string; nativeAdType: string;
  nativeAdId: string; nativeFacebookId: string; nativePosition: number;
  appUpdateStatus: string; appNewVersion: number; appUpdateDesc: string;
  appRedirectUrl: string; cancelUpdateStatus: string; songDownload: string;
  privacyMode: string;
};

const empty: Settings = {
  packageName: "", onesignalAppId: "", onesignalRestKey: "", appName: "", appLogo: "",
  appEmail: "", appVersion: "", appAuthor: "", appContact: "", appWebsite: "",
  appDescription: "", appDevelopedBy: "", appPrivacyPolicy: "",
  apiLatestLimit: 10,
  publisherId: "", interstitalAd: "false", interstitalAdId: "", interstitalAdClick: "",
  bannerAd: "false", bannerAdId: "", bannerAdType: "admob", bannerFacebookId: "",
  interstitalAdType: "admob", interstitalFacebookId: "", nativeAd: "false",
  nativeAdType: "admob", nativeAdId: "", nativeFacebookId: "", nativePosition: 5,
  appUpdateStatus: "false", appNewVersion: 1, appUpdateDesc: "", appRedirectUrl: "",
  cancelUpdateStatus: "false", songDownload: "true", privacyMode: "false",
};

const f = reactive<Settings>({ ...empty });
const loading = ref(false);
const saving = ref(false);

async function load() {
  loading.value = true;
  try {
    const { data } = await api.get<Settings>("/admin/settings");
    Object.assign(f, data);
  } finally {
    loading.value = false;
  }
}

async function save() {
  saving.value = true;
  try {
    const payload: Record<string, unknown> = { ...f };
    delete payload.id;
    await api.put("/admin/settings", payload);
    ElMessage.success("设置已保存");
  } finally {
    saving.value = false;
  }
}

load();
</script>
