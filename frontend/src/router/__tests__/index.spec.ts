import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import router from '../index'
import { useAuthStore } from '@/stores/auth'
import type { UserMe } from '@/types/user'

function createUserMe(role: 'USER' | 'ADMIN'): UserMe {
  return {
    id: 1,
    name: 'Test User',
    characters: [],
    playTimeStart: null,
    playTimeEnd: null,
    message: null,
    email: 'test@example.com',
    xId: null,
    discordUsername: null,
    createdAt: '2026-09-01T00:00:00',
    updatedAt: '2026-09-01T00:00:00',
    role,
  }
}

describe('router guard', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    // 各テストを中立なrouteから開始する。直前のテストが到達したrouteと同じpathへ
    // 続けてpushすると、Vue Routerが冗長な遷移として扱いguardを再評価しない
    // ことがあるため、必ず一度別routeを経由してから各テストの対象routeへ遷移する。
    await router.push('/')
  })

  it('未ログインでrequiresAuthなページへアクセスすると/loginへリダイレクトされる', async () => {
    await router.push('/mypage')

    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('ログイン済みならrequiresAuthなページへアクセスできる', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'

    await router.push('/mypage')

    expect(router.currentRoute.value.path).toBe('/mypage')
  })

  it('requiresAuthではないページは未ログインでもアクセスできる', async () => {
    await router.push('/about')

    expect(router.currentRoute.value.path).toBe('/about')
  })

  it('未ログインで/loginへアクセスすると/loginがそのまま表示される', async () => {
    await router.push('/login')

    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('ログイン済みで/loginへアクセスすると/mypageへリダイレクトされる', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'

    await router.push('/login')

    expect(router.currentRoute.value.path).toBe('/mypage')
  })

  // ==== requiresAdmin(ADMIN専用大会管理画面)====

  it('未ログインでrequiresAdminなページへアクセスすると/loginへリダイレクトされる', async () => {
    await router.push('/admin/tournaments')

    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('ログイン済みだがUSERロールの場合_requiresAdminなページへアクセスすると/forbiddenへリダイレクトされる', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe('USER')

    await router.push('/admin/tournaments')

    expect(router.currentRoute.value.path).toBe('/forbidden')
  })

  it('ADMINロールの場合_requiresAdminなページへアクセスできる', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe('ADMIN')

    await router.push('/admin/tournaments')

    expect(router.currentRoute.value.path).toBe('/admin/tournaments')
  })

  it('ADMINロールの場合_admin大会作成ページへアクセスできる', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe('ADMIN')

    await router.push('/admin/tournaments/new')

    expect(router.currentRoute.value.path).toBe('/admin/tournaments/new')
  })

  it('ADMINロールの場合_admin大会編集ページへアクセスできる', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe('ADMIN')

    await router.push('/admin/tournaments/1/edit')

    expect(router.currentRoute.value.path).toBe('/admin/tournaments/1/edit')
  })

  it('/forbiddenはrequiresAuth/requiresAdminを持たず未ログインでもそのまま表示される(無限リダイレクトしない)', async () => {
    await router.push('/forbidden')

    expect(router.currentRoute.value.path).toBe('/forbidden')
  })

  it('未ログインで/registerへアクセスすると/registerがそのまま表示される', async () => {
    await router.push('/register')

    expect(router.currentRoute.value.path).toBe('/register')
  })

  it('ログイン済みで/registerへアクセスすると/mypageへリダイレクトされる', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'

    await router.push('/register')

    expect(router.currentRoute.value.path).toBe('/mypage')
  })
})
