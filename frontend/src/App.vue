<script setup>
import { RouterView } from 'vue-router'
import AppHeader from './components/AppHeader.vue'
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main">
      <RouterView />
    </main>
    <footer class="app-footer">
      <div class="container footer-inner">
        <span>MallAgent · MVP 电商演示</span>
        <span>Vue 3 + Vite · Spring Boot</span>
      </div>
    </footer>
  </div>
</template>
