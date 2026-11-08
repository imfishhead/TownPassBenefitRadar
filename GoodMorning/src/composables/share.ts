/**
 * 分享「古摸泥」給親友（獲得徽章的慶祝畫面使用）。
 * 手機支援系統分享就叫出分享選單（LINE、訊息都在裡面）；不支援就複製連結（回傳 'copied'）；複製也失敗就回傳 'manual'，由畫面顯示連結讓人長按複製。
 */

/**
 * 要分享出去的連結：優先用 VITE_SHARE_URL（正式上線後填台北通裡這個服務的連結），
 * 沒設定就用目前網站自己的首頁（不含 #/ 路由）。開發時是本機網址，別人打不開。
 */
export function shareUrl(): string {
  const fromEnv = import.meta.env.VITE_SHARE_URL?.trim()
  if (fromEnv) return fromEnv
  if (typeof document === 'undefined') return ''
  return new URL('./', document.baseURI).href
}

export function shareMessage(badge?: { level: number; name: string } | null): string {
  return badge
    ? `我在台北通的「古摸泥」拿到 Lv.${badge.level} ${badge.name} 徽章！你也來做一張長輩圖：`
    : '我在台北通的「古摸泥」做了長輩圖，你也來做一張：'
}

export type ShareResult = 'shared' | 'copied' | 'manual' | 'cancelled'

async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    /* 繼續試舊做法 */
  }
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.setAttribute('readonly', '')
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    ta.setSelectionRange(0, text.length)
    const ok = document.execCommand('copy')
    ta.remove()
    return ok
  } catch {
    return false
  }
}

export async function shareApp(badge?: { level: number; name: string } | null): Promise<ShareResult> {
  const text = shareMessage(badge)
  const url = shareUrl()
  if (typeof navigator.share === 'function') {
    try {
      await navigator.share({ title: '古摸泥：政策長輩圖產生器', text, url })
      return 'shared'
    } catch (e) {
      // 使用者自己關掉分享選單：不算失敗，不要再跳提示
      if ((e as DOMException)?.name === 'AbortError') return 'cancelled'
    }
  }
  // 複製成功的提示由呼叫端顯示（慶祝畫面在原地顯示，不用 toast 蓋住徽章）
  return (await copyText(`${text}${url}`)) ? 'copied' : 'manual'
}
