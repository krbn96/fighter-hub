import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import router from '../index'
import { useAuthStore } from '@/stores/auth'

describe('router guard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
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
})
