import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'

import TournamentDetailView from '../TournamentDetailView.vue'
import type { Tournament } from '@/types/tournament'

vi.mock('@/api/tournaments', () => ({
  fetchTournamentById: vi.fn<(id: string) => Promise<Tournament>>(),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ params: { id: '1' } }),
  }
})

import { fetchTournamentById } from '@/api/tournaments'

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

describe('TournamentDetailView', () => {
  beforeEach(() => {
    vi.mocked(fetchTournamentById).mockReset()
  })

  it('API取得成功時に大会詳細が表示される', async () => {
    vi.mocked(fetchTournamentById).mockResolvedValue(sampleTournament)

    const wrapper = mount(TournamentDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('STREET FIGHTER 6 CUP')
    expect(wrapper.text()).toContain('2026/10/10 13:00')
    expect(fetchTournamentById).toHaveBeenCalledWith('1')
  })

  it('404時に専用メッセージが表示され他のエラーとは区別される', async () => {
    vi.mocked(fetchTournamentById).mockRejectedValue({
      isAxiosError: true,
      response: { status: 404 },
    })

    const wrapper = mount(TournamentDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('指定した大会が見つかりません')
    expect(wrapper.text()).not.toContain('大会情報の取得に失敗しました')
  })

  it('404以外のエラー時は汎用エラーメッセージが表示される', async () => {
    vi.mocked(fetchTournamentById).mockRejectedValue(new Error('network error'))

    const wrapper = mount(TournamentDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('大会情報の取得に失敗しました')
    expect(wrapper.text()).not.toContain('指定した大会が見つかりません')
  })

  it('「チームを探す・参加する」が/tournaments/{id}/teamsへ、「チームを作る・募集する」が/tournaments/{id}/teams/createへのリンクになっている', async () => {
    vi.mocked(fetchTournamentById).mockResolvedValue(sampleTournament)

    const wrapper = mount(TournamentDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    const links = wrapper.findAllComponents(RouterLinkStub)
    const searchTeamsLink = links.find((link) => link.text().includes('チームを探す・参加する'))
    const createTeamLink = links.find((link) => link.text().includes('チームを作る・募集する'))

    expect(searchTeamsLink).toBeDefined()
    expect(searchTeamsLink?.props('to')).toBe('/tournaments/1/teams')

    expect(createTeamLink).toBeDefined()
    expect(createTeamLink?.props('to')).toBe('/tournaments/1/teams/create')
  })
})
