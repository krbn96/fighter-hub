import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'

import TournamentTeamsView from '../TournamentTeamsView.vue'
import type { Team } from '@/types/team'

vi.mock('@/api/teams', () => ({
  fetchTeamsByTournament: vi.fn<(tournamentId: string) => Promise<Team[]>>(),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ params: { id: '1' } }),
  }
})

import { fetchTeamsByTournament } from '@/api/teams'

const sampleTeam: Team = {
  id: 10,
  tournamentId: 1,
  tournamentName: 'STREET FIGHTER 6 CUP',
  ownerId: 1,
  ownerName: 'Owner User',
  name: 'Team Ryu',
  rankRequirement: 'MASTER',
  characterRequirements: [1, 2],
  recruitmentMessage: '誰でも歓迎です',
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

describe('TournamentTeamsView', () => {
  beforeEach(() => {
    vi.mocked(fetchTeamsByTournament).mockReset()
  })

  it('API取得成功時にチーム名が表示される', async () => {
    vi.mocked(fetchTeamsByTournament).mockResolvedValue([sampleTeam])

    const wrapper = mount(TournamentTeamsView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Team Ryu')
    expect(fetchTeamsByTournament).toHaveBeenCalledWith('1')
  })

  it('0件時に募集中のチームがありませんと表示される', async () => {
    vi.mocked(fetchTeamsByTournament).mockResolvedValue([])

    const wrapper = mount(TournamentTeamsView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('募集中のチームがありません')
  })

  it('大会不存在の404時に専用メッセージが表示される', async () => {
    vi.mocked(fetchTeamsByTournament).mockRejectedValue({
      isAxiosError: true,
      response: { status: 404 },
    })

    const wrapper = mount(TournamentTeamsView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('指定した大会が見つかりません')
  })
})
