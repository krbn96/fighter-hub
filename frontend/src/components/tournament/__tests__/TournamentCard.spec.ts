import { describe, expect, it } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'

import TournamentCard from '../TournamentCard.vue'
import type { Tournament } from '@/types/tournament'

const sampleTournament: Tournament = {
  id: 1,
  name: 'STREET FIGHTER 6 CUP',
  teamSize: 3,
  startAt: '2026-10-10T13:00:00',
  maxPlayers: 64,
  status: 'OPEN',
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

describe('TournamentCard', () => {
  it('大会名・status・開始日時・チームサイズを表示し、大会詳細へのリンクになる', () => {
    const wrapper = mount(TournamentCard, {
      props: { tournament: sampleTournament },
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    expect(wrapper.text()).toContain('STREET FIGHTER 6 CUP')
    expect(wrapper.text()).toContain('OPEN')
    expect(wrapper.text()).toContain('2026/10/10 13:00')
    expect(wrapper.text()).toContain('3 ON 3')

    const link = wrapper.findComponent(RouterLinkStub)
    expect(link.props('to')).toBe('/tournaments/1')
  })
})
