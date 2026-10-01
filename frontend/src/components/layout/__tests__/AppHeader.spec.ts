import { describe, expect, it } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import AppHeader from '../AppHeader.vue'

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
})
