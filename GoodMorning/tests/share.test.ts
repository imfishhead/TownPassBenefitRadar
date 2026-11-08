import { describe, expect, it } from 'vitest'
import { shareMessage } from '../src/composables/share'
import { CELEBRATION_SECONDS, soundOn } from '../src/composables/sound'

describe('分享文字', () => {
  it('拿到徽章時寫明等級與名稱，結尾是冒號（後面直接接連結）', () => {
    const t = shareMessage({ level: 2, name: '雙喜分享' })
    expect(t).toContain('Lv.2 雙喜分享')
    expect(t).toContain('古摸泥')
    expect(t.endsWith('：')).toBe(true)
  })
  it('沒有徽章時也是完整的一句邀請', () => {
    const t = shareMessage(null)
    expect(t).toContain('古摸泥')
    expect(t.endsWith('：')).toBe(true)
  })
})

describe('慶祝音效', () => {
  it('預設開啟（產品決定；偏離台北通 AV 的部分見 README），使用者可以在畫面上關掉並記住', () => {
    expect(soundOn.value).toBe(true)
  })
  it('合成音效整段不超過 4 秒（稱讚錄音另外算）', () => {
    expect(CELEBRATION_SECONDS).toBeGreaterThan(2)
    expect(CELEBRATION_SECONDS).toBeLessThanOrEqual(4)
  })
})
