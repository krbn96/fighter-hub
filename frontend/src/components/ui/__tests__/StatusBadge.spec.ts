import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import StatusBadge from '../StatusBadge.vue'

describe('StatusBadge', () => {
  it('RECRUITINGはprimary toneで表示される', () => {
    const wrapper = mount(StatusBadge, { props: { status: 'RECRUITING' } })
    expect(wrapper.text()).toBe('RECRUITING')
    expect(wrapper.classes()).toContain('status-badge--primary')
  })

  it('CLOSEDはneutral toneで表示される', () => {
    const wrapper = mount(StatusBadge, { props: { status: 'CLOSED' } })
    expect(wrapper.text()).toBe('CLOSED')
    expect(wrapper.classes()).toContain('status-badge--neutral')
  })
})
