import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/about',
      name: 'about',
      // route level code-splitting
      // this generates a separate chunk (About.[hash].js) for this route
      // which is lazy-loaded when the route is visited.
      component: () => import('../views/AboutView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
    },
    {
      path: '/mypage',
      name: 'mypage',
      component: () => import('../views/MyPageView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/tournaments',
      name: 'tournaments',
      component: () => import('../views/TournamentListView.vue'),
    },
    {
      path: '/tournaments/:id',
      name: 'tournament-detail',
      component: () => import('../views/TournamentDetailView.vue'),
    },
    {
      path: '/tournaments/:id/teams',
      name: 'tournament-teams',
      component: () => import('../views/TournamentTeamsView.vue'),
    },
    {
      path: '/tournaments/:id/teams/create',
      name: 'team-create',
      component: () => import('../views/TeamCreateView.vue'),
      meta: { requiresAuth: true },
    },
    // 静的パスの/teams/myは、動的セグメントの/teams/:idより先に定義し、
    // :idとして誤認されないことを分かりやすくする。
    {
      path: '/teams/my',
      name: 'my-teams',
      component: () => import('../views/MyTeamView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/teams/:id',
      name: 'team-detail',
      component: () => import('../views/TeamDetailView.vue'),
    },
    {
      path: '/teams/:id/edit',
      name: 'team-edit',
      component: () => import('../views/TeamEditView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/teams/:id/applications',
      name: 'team-applications',
      component: () => import('../views/TeamApplicationsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/applications/my',
      name: 'my-applications',
      component: () => import('../views/MyApplicationView.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return '/login'
  }
})

export default router
