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
  recruitmentDeadline: '2099-12-15T23:59:00',
  maxPlayers: 64,
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

  it('「FIND A TEAM」が/tournaments/{id}/teamsへ、「CREATE TEAM」が/tournaments/{id}/teams/createへのリンクになっている', async () => {
    vi.mocked(fetchTournamentById).mockResolvedValue(sampleTournament)

    const wrapper = mount(TournamentDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    const links = wrapper.findAllComponents(RouterLinkStub)
    const searchTeamsLink = links.find((link) => link.text().includes('FIND A TEAM'))
    const createTeamLink = links.find((link) => link.text().includes('CREATE TEAM'))

    expect(searchTeamsLink).toBeDefined()
    expect(searchTeamsLink?.props('to')).toBe('/tournaments/1/teams')

    expect(createTeamLink).toBeDefined()
    expect(createTeamLink?.props('to')).toBe('/tournaments/1/teams/create')
  })

  it('募集締切前はRECRUITING表示・募集締切/開始日時が表示され、CREATE TEAMはリンクとして有効', async () => {
    vi.mocked(fetchTournamentById).mockResolvedValue(sampleTournament)

    const wrapper = mount(TournamentDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('RECRUITING')
    expect(wrapper.text()).toContain('2099/12/15 23:59')
    expect(wrapper.text()).toContain('2026/10/10 13:00')

    const createTeamLink = wrapper
      .findAllComponents(RouterLinkStub)
      .find((link) => link.text().includes('CREATE TEAM'))
    expect(createTeamLink).toBeDefined()
  })

  it('募集締切後はCLOSED表示になり、CREATE TEAMはdisabledになり理由が表示され、FIND A TEAMは引き続き利用できる', async () => {
    vi.mocked(fetchTournamentById).mockResolvedValue({
      ...sampleTournament,
      recruitmentDeadline: '2020-01-01T00:00:00',
    })

    const wrapper = mount(TournamentDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('CLOSED')
    expect(wrapper.text()).toContain('チーム募集は終了しました')

    const createTeamLink = wrapper
      .findAllComponents(RouterLinkStub)
      .find((link) => link.text().includes('CREATE TEAM'))
    expect(createTeamLink).toBeUndefined()

    const createTeamButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('CREATE TEAM'))
    expect(createTeamButton?.attributes('disabled')).toBeDefined()

    const findTeamLink = wrapper
      .findAllComponents(RouterLinkStub)
      .find((link) => link.text().includes('FIND A TEAM'))
    expect(findTeamLink).toBeDefined()
    expect(findTeamLink?.props('to')).toBe('/tournaments/1/teams')
  })
})
