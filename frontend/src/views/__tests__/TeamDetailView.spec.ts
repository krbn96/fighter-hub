import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'

import TeamDetailView from '../TeamDetailView.vue'
import type { Team } from '@/types/team'

vi.mock('@/api/teams', () => ({
  fetchTeamById: vi.fn<(id: string) => Promise<Team>>(),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ params: { id: '10' } }),
  }
})

import { fetchTeamById } from '@/api/teams'

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

describe('TeamDetailView', () => {
  beforeEach(() => {
    vi.mocked(fetchTeamById).mockReset()
  })

  it('API取得成功時にチーム詳細が表示される', async () => {
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Team Ryu')
    expect(wrapper.text()).toContain('STREET FIGHTER 6 CUP')
    expect(wrapper.text()).toContain('Owner User')
    expect(wrapper.text()).toContain('MASTER')
    expect(wrapper.text()).toContain('1, 2')
    expect(wrapper.text()).toContain('誰でも歓迎です')
    expect(fetchTeamById).toHaveBeenCalledWith('10')
  })

  it('nullable項目がnullの場合は指定なしと表示される', async () => {
    vi.mocked(fetchTeamById).mockResolvedValue({
      ...sampleTeam,
      rankRequirement: null,
      characterRequirements: null,
      recruitmentMessage: null,
    })

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    const text = wrapper.text()
    expect(text.match(/指定なし/g)?.length).toBe(3)
  })

  it('404時に専用メッセージが表示され他のエラーとは区別される', async () => {
    vi.mocked(fetchTeamById).mockRejectedValue({
      isAxiosError: true,
      response: { status: 404 },
    })

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('指定したチームが見つかりません')
    expect(wrapper.text()).not.toContain('チーム情報の取得に失敗しました')
  })

  it('404以外のエラー時は汎用エラーメッセージが表示される', async () => {
    vi.mocked(fetchTeamById).mockRejectedValue(new Error('network error'))

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('チーム情報の取得に失敗しました')
    expect(wrapper.text()).not.toContain('指定したチームが見つかりません')
  })
})
