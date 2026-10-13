import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    host: "127.0.0.1",
    port: 5174,
    strictPort: true,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true
      }
    }
  },
  build: {
    // 把体积较大的第三方库拆成独立文件，便于浏览器缓存并降低首屏单文件体积。
    chunkSizeWarningLimit: 1100,
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ["vue", "vue-router", "pinia"],
          element: ["element-plus", "@element-plus/icons-vue"],
          charts: ["echarts"]
        }
      }
    }
  }
});
