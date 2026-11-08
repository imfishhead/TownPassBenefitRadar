<script setup lang="ts">
/**
 * 下載附政策 QR Code 的長輩圖。
 * WebView 對 <a download> 的支援不一致，所以同時提供三條路：
 *   1. 長按圖片「儲存到照片」（iOS／Android WebView 最穩定）
 *   2. 系統分享（Web Share API 支援檔案時才顯示）
 *   3. 下載檔案
 */
import { computed } from 'vue'
import BottomSheet from './BottomSheet.vue'
import Icon from './Icon.vue'
import { closeDownload, downloadState, startDownload } from '../composables/artworkActions'
import { toast } from '../composables/toast'

const s = downloadState
const open = computed(() => !!s.value.artwork)
const own = computed(() => !!s.value.artwork?.ownedByMe)
const title = computed(() => (own.value ? '存到手機相簿' : '下載長輩圖'))

const canShare = computed(() => {
  const b = s.value.blob
  if (!b || typeof navigator.share !== 'function' || typeof navigator.canShare !== 'function') return false
  try {
    return navigator.canShare({ files: [new File([b], s.value.filename, { type: 'image/png' })] })
  } catch {
    return false
  }
})

async function share() {
  const b = s.value.blob
  if (!b) return
  try {
    await navigator.share({ files: [new File([b], s.value.filename, { type: 'image/png' })], title: '古摸泥：政策長輩圖產生器' })
  } catch (e) {
    if ((e as DOMException)?.name !== 'AbortError') toast('分享沒有成功，可改用長按圖片儲存', 'error')
  }
}

function saveFile() {
  if (!s.value.url) return
  const a = document.createElement('a')
  a.href = s.value.url
  a.download = s.value.filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  toast('已送出下載，若沒有反應請長按圖片儲存', 'success')
}

function retry() {
  if (s.value.artwork) startDownload(s.value.artwork)
}
</script>

<template>
  <BottomSheet :open="open" :title="title" @close="closeDownload">
    <div v-if="s.loading" class="state" aria-busy="true">
      <div class="preview skeleton" />
      <p class="muted" role="status">正在加上政策 <span lang="en">QR Code</span>…</p>
    </div>
    <div v-else-if="s.error" class="state" role="alert">
      <p>{{ s.error }}</p>
      <button class="btn primary block large" type="button" @click="retry">
        <Icon name="refresh" /> 再試一次
      </button>
    </div>
    <div v-else-if="s.url" class="state">
      <img class="preview" :src="s.url" alt="附政策 QR Code 的長輩圖，可長按儲存" />
      <p v-if="canShare" class="hint">
        <Icon name="share" :size="20" />
        <span>按下方按鈕，在選單中選<strong>「儲存影像」</strong>就會存進相簿</span>
      </p>
      <p v-else class="hint">
        <Icon name="image" :size="20" />
        <span><strong>長按圖片</strong>，選「儲存到照片」或「下載圖片」</span>
      </p>
      <div class="actions">
        <button v-if="canShare" class="btn primary block large" type="button" @click="share">
          <Icon name="share" /> {{ own ? '存到相簿或分享' : '分享給親友' }}
        </button>
        <button class="btn block large" :class="{ primary: !canShare }" type="button" @click="saveFile">
          <Icon name="download" /> 下載檔案
        </button>
      </div>
      <p class="muted small qr-note">右下角 QR Code 掃描後會開啟政策說明網頁</p>
    </div>
  </BottomSheet>
</template>

<style scoped>
.state {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 4px;
}
.preview {
  width: 100%;
  aspect-ratio: 1;
  border-radius: var(--radius-m);
  object-fit: cover;
  -webkit-touch-callout: default;
  user-select: auto;
}
.hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: var(--radius-m);
  background: var(--c-brand-soft);
  color: var(--c-action);
}
.actions {
  display: grid;
  gap: 10px;
}
.qr-note {
  text-align: center;
}
</style>
