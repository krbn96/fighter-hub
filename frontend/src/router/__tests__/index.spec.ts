import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import router from '../index'
import { useAuthStore } from '@/stores/auth'

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
