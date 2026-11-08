<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TopBar from './components/TopBar.vue'
import TabBar from './components/TabBar.vue'
import ToastHost from './components/ToastHost.vue'
import DownloadSheet from './components/DownloadSheet.vue'
import { titleOverride } from './composables/pageTitle'
import { apiMode } from './api'

const route = useRoute()
const title = computed(() => titleOverride.value ?? route.meta.title ?? '古摸泥：政策長輩圖產生器')
const main = ref<HTMLElement | null>(null)
// 內容區自己捲動，換頁時回到頂端；並把焦點移到新頁面的標題（NCC 1.4／台北通 FC），
// 讀報軟體會唸出新頁面是什麼，鍵盤使用者也從頁面最上面開始。第一次進來不動焦點。
watch(
  () => route.fullPath,
  async () => {
    main.value?.scrollTo?.({ top: 0 })
    await nextTick()
    document.querySelector<HTMLElement>('h1')?.focus({ preventScroll: true })
  },
)
</script>

<template>
  <TopBar :title="title" />
  <p v-if="apiMode === 'mock'" class="demo-notice" role="note">
    示範資料：人物為示範人物，116 年度參數皆為示意
  </p>
  <main ref="main">
    <RouterView />
  </main>
  <TabBar />
  <DownloadSheet />
  <ToastHost />
</template>

<style scoped>
.demo-notice {
  flex: none;
  padding: 6px 16px;
  background: var(--tp-warning-100, #fff4d6);
  color: var(--c-text);
  font-size: var(--tp-font-body);
  line-height: var(--tp-lh-body);
  text-align: center;
}
</style>
