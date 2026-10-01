import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'

import TournamentListView from '../TournamentListView.vue'
import type { Tournament } from '@/types/tournament'

vi.mock('@/api/tournaments', () => ({
  fetchTournaments: vi.fn<() => Promise<Tournament[]>>(),
}))

import { fetchTournaments } from '@/api/tournaments'

const sampleTournament: Tournament = {
  id: 1,
  name: 'STREET FIGHTER 6 CUP',
  teamSize: 3,
  startAt: '2026-10-10T13:00:00',
  recruitmentDeadline: '2026-10-05T23:59:00',
  maxPlayers: 64,
  status: 'OPEN',
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

describe('TournamentListView', () => {
  beforeEach(() => {
    vi.mocked(fetchTournaments).mockReset()
  })

  it('API取得成功時に大会名が表示される', async () => {
    vi.mocked(fetchTournaments).mockResolvedValue([sampleTournament])

    const wrapper = mount(TournamentListView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('STREET FIGHTER 6 CUP')
    expect(wrapper.text()).toContain('2026/10/10 13:00')
  })

  it('API取得失敗時にエラーメッセージが表示される', async () => {
    vi.mocked(fetchTournaments).mockRejectedValue(new Error('network error'))

    const wrapper = mount(TournamentListView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('大会一覧の取得に失敗しました')
  })
})
