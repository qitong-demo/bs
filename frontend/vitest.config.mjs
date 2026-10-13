import { defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: "jsdom",
    globals: true,
    include: ["src/**/*.spec.js"],
    // 使用线程池并限制并发：不依赖子进程，便于在受限环境与 CI 中稳定运行。
    pool: "threads",
    maxWorkers: 1,
    fileParallelism: false,
    reporters: ["default"]
  }
});
