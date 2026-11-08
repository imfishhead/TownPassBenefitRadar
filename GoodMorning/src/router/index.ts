import { createRouter, createWebHashHistory } from 'vue-router'
import FeedView from '../views/FeedView.vue'
import { titleOverride } from '../composables/pageTitle'

declare module 'vue-router' {
  interface RouteMeta {
    title: string
    tab?: 'feed' | 'create' | 'me'
    back?: boolean
  }
}

export const router = createRouter({
  // hash 模式：靜態主機與 TownPass WebView 都不需伺服器 rewrite，返回鍵可用
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'feed', component: FeedView, meta: { title: '古摸泥：政策長輩圖產生器', tab: 'feed' } },
    {
      path: '/create',
      name: 'create',
      component: () => import('../views/EditorView.vue'),
      meta: { title: '製作長輩圖', tab: 'create' },
    },
    {
      path: '/leaderboard',
      redirect: '/',
    },
    { path: '/me', name: 'me', component: () => import('../views/MeView.vue'), meta: { title: '我的作品', tab: 'me' } },
    {
      path: '/artworks/:id(\\d+)',
      name: 'artwork',
      component: () => import('../views/ArtworkView.vue'),
      meta: { title: '作品', back: true },
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: (_to, _from, saved) => saved ?? { top: 0 },
})

router.afterEach((to) => {
  // TownPass 以頁面標題作為 AppBar 標題
  titleOverride.value = null
  document.title = to.meta.title
})
