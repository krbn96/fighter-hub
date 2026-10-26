import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    // requiresAdmin: trueのルートは、未認証なら/loginへ、ログイン済みだがADMINでなければ
    // /forbiddenへ遷移させる(requiresAuthを別途付与する必要はない。beforeEach参照)。
    requiresAdmin?: boolean
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
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
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
    {
      path: '/users/:id',
      name: 'user-profile',
      component: () => import('../views/UserProfileView.vue'),
    },
    {
      path: '/forbidden',
      name: 'forbidden',
      component: () => import('../views/ForbiddenView.vue'),
    },
    {
      path: '/admin/tournaments',
      name: 'admin-tournaments',
      component: () => import('../views/admin/AdminTournamentListView.vue'),
      meta: { requiresAdmin: true },
    },
    {
      // 静的パスの/admin/tournaments/newは、動的セグメントの/admin/tournaments/:id/editより
      // 先に定義し、:idとして誤認されないようにする(/teams/my等と同じ方針)。
      path: '/admin/tournaments/new',
      name: 'admin-tournament-create',
      component: () => import('../views/admin/AdminTournamentCreateView.vue'),
      meta: { requiresAdmin: true },
    },
    {
      path: '/admin/tournaments/:id/edit',
      name: 'admin-tournament-edit',
      component: () => import('../views/admin/AdminTournamentEditView.vue'),
      meta: { requiresAdmin: true },
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore()

  // main.tsがapp.mount()より前にauthStore.restore()の完了を待つため、beforeEachが
  // 呼ばれる時点(初回アクセス・リロード後の最初のナビゲーションを含む)では認証復元は
  // 既に完了している。そのためこのガード自体で復元の完了を待つ処理は不要。
  if (to.meta.requiresAdmin) {
    if (!authStore.isAuthenticated) {
      return '/login'
    }
    if (authStore.user?.role !== 'ADMIN') {
      // /forbidden自体はrequiresAuth/requiresAdminを持たないため、無限リダイレクトにはならない。
      return '/forbidden'
    }
    return true
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return '/login'
  }

  // ログイン済みユーザーが/login・/registerへ直接アクセスした場合は/mypageへ戻す。
  // /mypageはrequiresAuth: trueだがログイン済みのため上の条件には掛からず、
  // 再度/login・/registerへ戻されることはない(redirect loopにならない)。
  if ((to.name === 'login' || to.name === 'register') && authStore.isAuthenticated) {
    return '/mypage'
  }
})

export default router
