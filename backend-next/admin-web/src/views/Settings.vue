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

      <el-tab-pane label="下载二维码">
        <el-alert
          v-if="!qr.token"
          type="info"
          :closable="false"
          show-icon
          title="尚未生成下载二维码"
          description="生成后，扫码走令牌入口下载；到期或重新生成后，已发出的二维码立即失效。下载文件仍为「版本更新」里的跳转地址。"
        />
        <template v-else>
          <el-alert
            v-if="qrExpired"
            type="warning"
            :closable="false"
            show-icon
            title="下载二维码已过期"
            :description="`到期时间 ${fmtTime(qr.expiresAt)}，已发出的二维码链接全部失效，请重新生成。`"
          />
          <div v-else class="qr-card">
            <img v-if="qrDataUrl" :src="qrDataUrl" class="qr-img" alt="下载二维码" />
            <div class="qr-side">
              <el-tag type="success" size="small">有效</el-tag>
              <p>到期时间：{{ fmtTime(qr.expiresAt) }}</p>
              <p>剩余 {{ remaining }}</p>
              <p class="qr-url">{{ qr.url }}</p>
              <el-button :disabled="!qrDataUrl" @click="saveQrImage">保存二维码图片</el-button>
            </div>
          </div>
        </template>

        <el-form label-width="140px" style="margin-top: 16px">
          <el-form-item label="有效期至">
            <el-date-picker v-model="qrExpires" type="datetime" placeholder="选择到期时间" style="width: 220px" />
            <el-button-group style="margin-left: 8px">
              <el-button @click="presetQrExpiry(1)">1 天后</el-button>
              <el-button @click="presetQrExpiry(7)">7 天后</el-button>
              <el-button @click="presetQrExpiry(30)">30 天后</el-button>
            </el-button-group>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :disabled="!qrExpires" :loading="qrSaving" @click="generateQr">
              {{ qr.token && !qrExpired ? "重新生成二维码" : "生成二维码" }}
            </el-button>
          </el-form-item>
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
import { computed, onUnmounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import QRCode from "qrcode";
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

// ---------------- 下载二维码（独立于设置表单，单独读写 /admin/download-qr）----------------

type QrState = { token: string; url: string; expiresAt: string | null; expired: boolean };

const qr = reactive<QrState>({ token: "", url: "", expiresAt: null, expired: true });
const qrExpires = ref<Date | null>(null);
const qrSaving = ref(false);
const qrDataUrl = ref("");
const now = ref(Date.now());

const qrExpired = computed(
  () => !qr.expiresAt || new Date(qr.expiresAt).getTime() <= now.value,
);

const remaining = computed(() => {
  if (qrExpired.value || !qr.expiresAt) return "";
  const ms = new Date(qr.expiresAt).getTime() - now.value;
  const d = Math.floor(ms / 86400000);
  const h = Math.floor((ms % 86400000) / 3600000);
  const m = Math.floor((ms % 3600000) / 60000);
  if (d > 0) return `${d} 天 ${h} 小时`;
  if (h > 0) return `${h} 小时 ${m} 分`;
  return `${Math.max(1, m)} 分`;
});

function fmtTime(v: string | null): string {
  return v ? new Date(v).toLocaleString("zh-CN", { hour12: false }) : "—";
}

function presetQrExpiry(days: number): void {
  qrExpires.value = new Date(Date.now() + days * 86400000);
}

async function loadQr() {
  const { data } = await api.get<QrState>("/admin/download-qr");
  Object.assign(qr, data);
}

async function generateQr() {
  if (!qrExpires.value) return;
  if (qr.token && !qrExpired.value) {
    try {
      await ElMessageBox.confirm(
        "重新生成后，已发出的二维码立即失效（旧码扫码 404），确定继续？",
        "重新生成二维码",
        { type: "warning" },
      );
    } catch {
      return;
    }
  }
  qrSaving.value = true;
  try {
    const { data } = await api.put<QrState>("/admin/download-qr", {
      expiresAt: qrExpires.value.getTime(),
    });
    Object.assign(qr, data);
    ElMessage.success("二维码已生成");
  } finally {
    qrSaving.value = false;
  }
}

function saveQrImage(): void {
  if (!qrDataUrl.value) return;
  const a = document.createElement("a");
  a.href = qrDataUrl.value;
  a.download = "倾轻听-下载二维码.png";
  document.body.appendChild(a);
  a.click();
  a.remove();
}

watch(
  () => qr.url,
  (url) => {
    if (!url) {
      qrDataUrl.value = "";
      return;
    }
    QRCode.toDataURL(url, { width: 360, margin: 1 })
      .then((dataUrl) => {
        qrDataUrl.value = dataUrl;
      })
      .catch(() => {
        qrDataUrl.value = "";
      });
  },
  { immediate: true },
);

const nowTimer = setInterval(() => {
  now.value = Date.now();
}, 30000);
onUnmounted(() => clearInterval(nowTimer));

load();
loadQr();
</script>

<style scoped>
.qr-card {
  display: flex;
  gap: 20px;
  align-items: center;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
.qr-img { width: 180px; height: 180px; display: block; }
.qr-side {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  color: #606266;
}
.qr-url { word-break: break-all; color: #909399; font-size: 12px; }
</style>
