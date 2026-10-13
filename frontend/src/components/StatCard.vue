<template>
  <section class="stat-card" :class="`stat-card--${item.tone || 'neutral'}`">
    <header class="stat-head">
      <span class="stat-icon" :class="`stat-icon--${item.tone || 'neutral'}`">
        <el-icon>
          <component :is="item.icon" />
        </el-icon>
      </span>
      <span class="stat-label" :title="item.label">{{ item.label }}</span>
    </header>
    <div class="stat-value">
      <span class="stat-number">{{ item.value }}</span>
      <span v-if="item.unit" class="stat-unit">{{ item.unit }}</span>
    </div>
    <div v-if="item.note" class="stat-note" :title="item.note">{{ item.note }}</div>
  </section>
</template>

<script setup>
defineProps({
  item: {
    type: Object,
    required: true
  }
});
</script>
