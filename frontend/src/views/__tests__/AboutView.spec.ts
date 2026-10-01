import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import AboutView from '../AboutView.vue'

describe('AboutView', () => {
  it('メイン見出しが表示される', () => {
    const wrapper = mount(AboutView)

    expect(wrapper.text()).toContain('ABOUT FIGHTER HUB')
  })

  it('主要機能セクションが表示される', () => {
    const wrapper = mount(AboutView)

    const text = wrapper.text()
    expect(text).toContain('WHAT YOU CAN DO')
    expect(text).toContain('Find Tournaments')
    expect(text).toContain('Create / Find Teams')
    expect(text).toContain('Recruitment Applications')
    expect(text).toContain('Player Profiles')
  })

  it('技術構成セクションが表示される', () => {
    const wrapper = mount(AboutView)

    const text = wrapper.text()
    expect(text).toContain('TECH STACK')
    expect(text).toContain('Vue 3')
    expect(text).toContain('TypeScript')
    expect(text).toContain('Spring Boot')
    expect(text).toContain('PostgreSQL')
  })

  it('未実装のAWS/Dockerは記載しない', () => {
    const wrapper = mount(AboutView)

    const text = wrapper.text()
    expect(text).not.toContain('AWS')
    expect(text).not.toContain('Docker')
  })
})
