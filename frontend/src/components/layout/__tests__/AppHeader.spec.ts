import { describe, expect, it } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import AppHeader from '../AppHeader.vue'
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

function findAdminLink(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAllComponents(RouterLinkStub).find((link) => link.text() === 'Admin')
}

describe('AppHeader', () => {
  it('未ログイン時、hamburger menuを開いた状態でLoginリンクをクリックするとmenuが閉じる', async () => {
    setActivePinia(createPinia())

    const wrapper = mount(AppHeader, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    const toggleButton = wrapper.find('.app-header__toggle')
    await toggleButton.trigger('click')
    expect(toggleButton.attributes('aria-expanded')).toBe('true')

    const loginLink = wrapper
      .findAllComponents(RouterLinkStub)
      .find((link) => link.text() === 'Login')
    expect(loginLink).toBeDefined()

    await loginLink?.trigger('click')

    expect(toggleButton.attributes('aria-expanded')).toBe('false')
  })

  it('未ログイン時はAdminリンクを表示しない', () => {
    setActivePinia(createPinia())

    const wrapper = mount(AppHeader, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    expect(findAdminLink(wrapper)).toBeUndefined()
  })

  it('USERロールでログイン時はAdminリンクを表示しない', () => {
    setActivePinia(createPinia())
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe('USER')

    const wrapper = mount(AppHeader, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    expect(findAdminLink(wrapper)).toBeUndefined()
  })

  it('ADMINロールでログイン時は/admin/tournamentsへのAdminリンクを表示する', () => {
    setActivePinia(createPinia())
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe('ADMIN')

    const wrapper = mount(AppHeader, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    const adminLink = findAdminLink(wrapper)
    expect(adminLink).toBeDefined()
    expect(adminLink?.props('to')).toBe('/admin/tournaments')
  })

  it('ADMINリンクはLOGOUTと同じ四角いアウトラインボタンの専用クラスを持つ(汎用リンクのクラスではない)', () => {
    setActivePinia(createPinia())
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe('ADMIN')

    const wrapper = mount(AppHeader, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    const adminLink = findAdminLink(wrapper)
    expect(adminLink?.classes()).toContain('app-header__admin-link')
    expect(adminLink?.classes()).not.toContain('app-header__link')
  })

  it('PCヘッダーでADMINはLOGOUTの右側(直後)に配置される', () => {
    setActivePinia(createPinia())
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe('ADMIN')

    const wrapper = mount(AppHeader, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    const authArea = wrapper.find('.app-header__auth')
    const children = Array.from(authArea.element.children)
    const logoutIndex = children.findIndex((el) => el.classList.contains('app-header__logout'))
    const adminIndex = children.findIndex((el) => el.classList.contains('app-header__admin-link'))

    expect(logoutIndex).toBeGreaterThanOrEqual(0)
    expect(adminIndex).toBe(logoutIndex + 1)
  })
})
