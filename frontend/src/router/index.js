import { createRouter, createWebHistory } from "vue-router";
import DashboardView from "../views/DashboardView.vue";
import LoginView from "../views/LoginView.vue";
import AdminView from "../views/AdminView.vue";

const routes = [
  {
    path: "/login",
    name: "login",
    component: LoginView,
    meta: { title: "登录" }
  },
  {
    path: "/",
    name: "dashboard",
    component: DashboardView,
    meta: { title: "首页", requiresAuth: true }
  },
  {
    path: "/admin",
    name: "admin",
    component: AdminView,
    meta: { title: "管理工作台", requiresAuth: true, adminOnly: true }
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to) => {
  const token = localStorage.getItem("friendly_station_token");
  if (to.meta.requiresAuth && !token) return { name: "login", query: { redirect: to.fullPath } };
  if (to.meta.adminOnly && !["STATION_ADMIN", "PLATFORM_ADMIN"].includes(localStorage.getItem("friendly_station_role"))) return { name: "dashboard" };
  if (to.name === "login" && token) return { name: "dashboard" };
  return true;
});

export default router;
