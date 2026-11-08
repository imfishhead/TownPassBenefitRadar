import { expect, test } from '@playwright/test'

test('作品牆可切換排行榜與最新作品', async ({ page }) => {
  await page.goto('./#/')

  await expect(page.getByRole('banner')).toBeVisible()
  await expect(page.getByRole('heading', { level: 1, name: '古摸泥：政策長輩圖產生器' })).toBeVisible()
  await expect(page.getByRole('button', { name: '關閉' })).toBeVisible()
  const ranking = page.getByRole('tab', { name: '排行榜' })
  const latest = page.getByRole('tab', { name: '最新' })
  const tabsBox = await ranking.locator('..').boundingBox()
  expect(tabsBox?.y).toBeLessThanOrEqual(70)
  await expect(ranking).toHaveAttribute('aria-selected', 'true')
  const rankingPanel = page.getByRole('tabpanel', { name: '排行榜' })
  await expect(rankingPanel).toBeVisible()
  await expect(rankingPanel.getByRole('list', { name: '排行榜前十名' }).getByRole('listitem')).toHaveCount(5)
  await expect(rankingPanel.getByText(/^第 [123] 名$/)).toHaveCount(3)
  await expect(rankingPanel.getByText('第 4 名', { exact: true })).toHaveCount(0)

  await latest.click()
  await expect(latest).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('tabpanel', { name: '最新' })).toBeVisible()

  const mainNavigation = page.getByRole('navigation', { name: '主要功能' })
  await expect(mainNavigation.getByRole('link')).toHaveCount(3)
  await expect(mainNavigation.getByRole('link', { name: '排行榜' })).toHaveCount(0)
})
