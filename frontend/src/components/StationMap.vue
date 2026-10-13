<template>
  <div class="station-map-wrap" :style="{ height: height }">
    <div ref="mapEl" class="station-map-canvas"></div>

    <!-- 地图不可用（未配置 Key / 脚本加载失败 / 离线）时的降级提示，不报错、不白屏 -->
    <div v-if="mapError" class="station-map-overlay station-map-overlay-error">
      <p>地图暂时不可用（{{ mapError }}）。可用驿站详情里的「在高德地图中搜索」查看导航。</p>
    </div>
    <!-- 地图可用但没有点位坐标时的说明 -->
    <div v-else-if="ready && coordinateStations.length === 0" class="station-map-overlay">
      <p>来源未提供点位坐标，暂无法在地图上标点；可用驿站详情里的「在高德地图中搜索」导航。</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { openStateMeta } from "../utils/business-hours";

const props = defineProps({
  /** 当前城市：{ cityName, centerLng, centerLat }，缺失时用平台默认城市坐标兜底。 */
  city: { type: Object, default: null },
  /** 驿站数组，元素含 stationName、address、longitude、latitude、status。 */
  stations: { type: Array, default: () => [] },
  height: { type: String, default: "320px" },
  /**
   * 需要聚焦的驿站 id（列表点击后传到地图）。
   * 变化时地图会飞过去并打开该驿站的信息窗，实现「列表 ↔ 地图」双向联动。
   */
  focusId: { type: [Number, String], default: null }
});

/**
 * 高德地图 JS API（Web 端 Key）。
 * 说明：Web 端 Key 本来就会出现在浏览器里，靠「安全密钥 + 域名白名单」保护；
 * 高德 Web **服务** Key（地理编码等）只在后端使用，绝不进前端。
 */
const AMAP_KEY = import.meta.env.VITE_AMAP_JS_KEY || "";
const AMAP_SECRET = import.meta.env.VITE_AMAP_JS_SECRET || "";

/** 全国默认视角（杭州），仅在 city 缺少经纬度时兜底，避免地图空白。 */
const FALLBACK_CENTER = [120.1551, 30.2741];
const DEFAULT_ZOOM = 11;
/** 聚焦单个驿站时的放大级别，比城市全景更近。 */
const FOCUS_ZOOM = 15;

const mapEl = ref(null);
const mapError = ref("");
const ready = ref(false);
let map = null;
let infoWindow = null;
let markers = [];

/** 把后端字段转成有限数字；缺失或非法值返回 null。 */
function toCoordinate(value) {
  if (value === null || value === undefined || value === "") return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

/** 只有同时具备经纬度的驿站才能落到地图上。 */
const coordinateStations = computed(() =>
  (props.stations || [])
    .map((item) => ({
      record: item,
      lng: toCoordinate(item?.longitude),
      lat: toCoordinate(item?.latitude)
    }))
    .filter((item) => item.lng !== null && item.lat !== null)
);

/** 高德使用 GCJ-02 坐标，与坐标回填所用的地理编码结果一致，无需转换。 */
const cityCenter = computed(() => {
  const lng = toCoordinate(props.city?.centerLng);
  const lat = toCoordinate(props.city?.centerLat);
  return lng === null || lat === null ? FALLBACK_CENTER : [lng, lat];
});

/** 动态加载高德 JS API；已加载过则直接复用。 */
function loadAmap() {
  if (window.AMap) {
    return Promise.resolve(window.AMap);
  }
  if (!AMAP_KEY) {
    return Promise.reject(new Error("未配置 VITE_AMAP_JS_KEY"));
  }
  if (AMAP_SECRET) {
    // 必须在脚本加载前设置，否则 JSAPI 会因为缺少安全密钥而拒绝服务。
    window._AMapSecurityConfig = { securityJsCode: AMAP_SECRET };
  }
  return new Promise((resolve, reject) => {
    const existing = document.querySelector("script[data-amap-loader]");
    if (existing) {
      existing.addEventListener("load", () => resolve(window.AMap));
      existing.addEventListener("error", () => reject(new Error("脚本加载失败")));
      return;
    }
    const script = document.createElement("script");
    script.dataset.amapLoader = "true";
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${AMAP_KEY}`;
    script.async = true;
    script.onload = () => (window.AMap ? resolve(window.AMap) : reject(new Error("脚本已加载但未注入 AMap")));
    script.onerror = () => reject(new Error("脚本加载失败"));
    document.head.appendChild(script);
  });
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
  }[char]));
}

function popupHtml(station) {
  const name = escapeHtml(station?.stationName || "友好驿站");
  const address = escapeHtml(station?.address || "地址待核验");
  // 按当前时间判断营业状态，而不是直接用来源里的静态 status。
  const meta = openStateMeta(station?.openTime, new Date(), station?.status);
  const status = meta.key === "CLOSED_STATUS" && !station?.status ? "" : meta.label;
  const geocoded = station?.source?.geocodeSource ? "（坐标为高德地图派生）" : "";
  return `<div class="station-map-popup">
      <strong>${name}</strong>
      <p>${address}</p>
      <span>${status}${geocoded}</span>
    </div>`;
}

/** 渲染点位标记；没有坐标的驿站不参与打点。 */
function renderMarkers() {
  if (!map || !window.AMap) return;
  map.remove(markers);
  markers = [];
  if (coordinateStations.value.length === 0) return;
  if (!infoWindow) {
    infoWindow = new window.AMap.InfoWindow({ offset: new window.AMap.Pixel(0, -30) });
  }
  markers = coordinateStations.value.map((item) => {
    const marker = new window.AMap.Marker({
      position: [item.lng, item.lat],
      title: item.record?.stationName || "",
      anchor: "bottom-center"
    });
    marker.on("click", () => {
      infoWindow.setContent(popupHtml(item.record));
      infoWindow.open(map, [item.lng, item.lat]);
    });
    return marker;
  });
  map.add(markers);
}

async function initMap() {
  if (map || !mapEl.value) return;
  try {
    const AMap = await loadAmap();
    // 组件可能在等待脚本期间被卸载，这里再确认一次容器还在。
    if (!mapEl.value) return;
    map = new AMap.Map(mapEl.value, {
      zoom: DEFAULT_ZOOM,
      center: cityCenter.value,
      viewMode: "2D",
      resizeEnable: true
    });
    ready.value = true;
    renderMarkers();
  } catch (error) {
    mapError.value = error?.message || "未知错误";
  }
}

/** 城市切换时重新定位到新城市中心。 */
watch(cityCenter, (center) => {
  if (map) {
    map.setZoomAndCenter(DEFAULT_ZOOM, center);
  }
});

watch(coordinateStations, renderMarkers);

/** 列表点击驿站后，地图飞过去并弹出信息窗。 */
watch(
  () => props.focusId,
  (id) => {
    if (!map || id === null || id === undefined) return;
    const hit = coordinateStations.value.find((item) => String(item.record?.id) === String(id));
    if (!hit) return;
    if (!infoWindow) {
      infoWindow = new window.AMap.InfoWindow({ offset: new window.AMap.Pixel(0, -30) });
    }
    map.setZoomAndCenter(FOCUS_ZOOM, [hit.lng, hit.lat], false, 260);
    infoWindow.setContent(popupHtml(hit.record));
    infoWindow.open(map, [hit.lng, hit.lat]);
  }
);

onMounted(initMap);

onBeforeUnmount(() => {
  markers = [];
  infoWindow = null;
  if (map) {
    map.clearMap();
    map.destroy();
    map = null;
  }
});
</script>

<style scoped>
.station-map-wrap {
  position: relative;
  width: 100%;
  overflow: hidden;
  /* 中性浅色底：地图不可用时也不会出现空白灰块。 */
  background: #eef2f5;
  border: 1px solid #dce8ee;
  border-radius: 5px;
}

.station-map-canvas {
  width: 100%;
  height: 100%;
  background: #eef2f5;
}

.station-map-overlay {
  position: absolute;
  z-index: 20;
  right: 8px;
  bottom: 22px;
  left: 8px;
  padding: 6px 9px;
  /* 半透明底 + 小字号：只作说明，尽量少遮挡地图。 */
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e2ebf0;
  border-radius: 5px;
  pointer-events: none;
}

.station-map-overlay-error {
  background: rgba(255, 247, 237, 0.95);
  border-color: #f5d5b0;
}

.station-map-overlay p {
  margin: 0;
  color: #556172;
  font-size: 11px;
  line-height: 1.5;
}
</style>

<style>
/* 弹窗内容由高德插入到地图容器内，需用全局样式覆盖。 */
.station-map-popup strong {
  display: block;
  color: #1f2937;
  font-size: 13px;
}

.station-map-popup p {
  margin: 4px 0 0;
  color: #556172;
  font-size: 12px;
  line-height: 1.5;
}

.station-map-popup span {
  display: inline-block;
  margin-top: 4px;
  color: #18bf8f;
  font-size: 12px;
}
</style>
