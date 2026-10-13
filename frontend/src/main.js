import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";
import App from "./App.vue";
import router from "./router";
import "./styles.css";

const app = createApp(App);

Object.entries(ElementPlusIconsVue).forEach(([name, component]) => {
  app.component(name, component);
});

app.use(createPinia());
app.use(router);
app.use(ElementPlus);
app.mount("#root");
