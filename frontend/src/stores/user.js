import { defineStore } from "pinia";
import { getCityList, locateCity } from "../api/city";

/** 用户「手动」选择过的城市代码存放键：只要存在，就不再请求浏览器定位。 */
const CITY_STORAGE_KEY = "friendly_station_city_code";

export const useUserStore = defineStore("user", {
  state: () => ({
    token: localStorage.getItem("friendly_station_token") || "",
    role: "普通用户",
    roleCode: localStorage.getItem("friendly_station_role") || "USER",
    /** 当前生效的城市行政区划代码，例如杭州市 330100；空串表示尚未确定。 */
    cityCode: "",
    /** 当前生效的城市名称，例如「杭州市」；空串表示尚未确定。 */
    cityName: "",
    /** 平台已覆盖的城市列表，来自 GET /api/city/list，第一项为平台默认城市。 */
    cities: [],
    /** 定位得到的用户真实所在城市名（可能平台未覆盖），用于给出明确提示。 */
    locatedCityName: "",
    /** 定位到的城市是否在平台已覆盖范围内；手动选择时视为 true。 */
    cityCovered: true,
    user: {
      name: "骑手小王",
      jobType: "外卖骑手",
      area: "西湖区"
    }
  }),
  getters: {
    /** 当前城市对象（含 centerLng / centerLat），用于地图定位与文案展示。 */
    currentCity(state) {
      return state.cities.find((item) => item.cityCode === state.cityCode) || null;
    },
    /**
     * 定位城市未覆盖时的提示文案；一切正常时为空串。
     * 场景：用户在苏州打开平台 → 提示「您所在的城市暂未覆盖，已展示最近的上海市」。
     */
    coverageHint(state) {
      if (state.cityCovered || !state.locatedCityName) return "";
      return `您所在的城市（${state.locatedCityName}）暂未覆盖，已为您展示最近的${state.cityName || "已覆盖城市"}。`;
    }
  },
  actions: {
    setSession(payload) {
      this.token = payload.token;
      localStorage.setItem("friendly_station_token", payload.token);
      this.user = { ...this.user, name: payload.realName || payload.username, roleCode: payload.role };
      this.roleCode = payload.role;
      localStorage.setItem("friendly_station_role", payload.role);
      this.role = {
        USER: "普通用户",
        STATION_ADMIN: "驿站管理员",
        PLATFORM_ADMIN: "平台管理员",
        SUPER_ADMIN: "超级管理员"
      }[payload.role] || "普通用户";
    },
    clearSession() {
      this.token = "";
      localStorage.removeItem("friendly_station_token");
      localStorage.removeItem("friendly_station_role");
    },
    setRole(role) {
      this.role = role;
    },
    /** 拉取平台已覆盖的城市列表，用于顶栏下拉与定位兜底。 */
    async loadCities() {
      const list = await getCityList();
      this.cities = Array.isArray(list) ? list : [];
      return this.cities;
    },
    /**
     * 用户在顶栏「手动」切换城市时调用。
     * 写入 localStorage 表示这是用户的选择，下次打开不再被浏览器定位覆盖。
     */
    setCity(cityCode) {
      this.applyCity(cityCode);
      // 手动选的城市一定在已覆盖列表里，因此清掉「未覆盖」提示。
      this.cityCovered = true;
      this.locatedCityName = "";
      if (cityCode) {
        localStorage.setItem(CITY_STORAGE_KEY, cityCode);
      } else {
        localStorage.removeItem(CITY_STORAGE_KEY);
      }
    },
    /**
     * 城市确定逻辑：定位优先，拒绝才回退。顺序严格如下，且任何失败都静默兜底、不阻塞页面。
     *
     * 1. 用户手动选过（localStorage 有 cityCode）且该城市仍在列表中 → 直接采用，不再请求定位。
     *    理由：尊重用户已有的主动选择，避免每次打开页面都被浏览器定位覆盖。
     * 2. 否则若浏览器支持定位 → 请求当前位置（低精度即可，8 秒超时，10 分钟缓存），
     *    成功后用经纬度调用 /api/city/locate：后端用高德逆地理编码得到**用户真实所在城市**，
     *    并判断该城市是否在平台已覆盖范围内。
     *    - 已覆盖 → 直接用该城市；
     *    - 未覆盖（例如用户在苏州）→ 降级用后端返回的最近已覆盖城市，同时记下真实城市名，
     *      由 coverageHint 给用户明确提示，而不是静默把用户当成别的城市的人。
     * 3. 定位被拒绝 / 超时 / 失败 → 回退到 cities[0]（平台默认城市，后端按 sortNo 排序）。
     *    理由：定位不可用时仍要有可用城市，保证首页数据与地图能正常展示。
     * 4. cities 为空（列表接口失败）→ 保持空，由调用方处理（例如只提示，不渲染地图）。
     */
    async resolveCity() {
      // 1. 尊重用户手动选择
      const stored = localStorage.getItem(CITY_STORAGE_KEY) || "";
      if (stored && this.cities.some((item) => item.cityCode === stored)) {
        this.applyCity(stored);
        this.cityCovered = true;
        this.locatedCityName = "";
        return this.cityCode;
      }

      // 2. 定位优先（带「是否已覆盖」判断）
      if (typeof navigator !== "undefined" && navigator.geolocation) {
        try {
          const position = await new Promise((resolve, reject) => {
            navigator.geolocation.getCurrentPosition(resolve, reject, {
              enableHighAccuracy: false,
              timeout: 8000,
              maximumAge: 600000
            });
          });
          const location = await locateCity(position.coords.longitude, position.coords.latitude);
          const target = location?.covered ? location.city : location?.nearest;
          if (target?.cityCode) {
            this.applyCity(target.cityCode);
            this.cityCovered = Boolean(location?.covered);
            this.locatedCityName = location?.cityName || "";
            return this.cityCode;
          }
        } catch {
          // 用户拒绝授权、超时、接口失败等一律静默忽略，继续走第 3 步兜底。
        }
      }

      // 3. 回退平台默认城市（列表为空时保持空，由调用方处理）
      const fallback = this.cities[0] || null;
      this.cityCode = fallback?.cityCode || "";
      this.cityName = fallback?.cityName || "";
      this.cityCovered = true;
      this.locatedCityName = "";
      return this.cityCode;
    },
    /** 按城市代码同步 cityCode 与 cityName。 */
    applyCity(cityCode) {
      this.cityCode = cityCode || "";
      this.cityName = this.cities.find((item) => item.cityCode === cityCode)?.cityName || "";
    }
  }
});
