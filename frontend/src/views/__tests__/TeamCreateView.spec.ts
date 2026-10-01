import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'

import TeamCreateView from '../TeamCreateView.vue'
import type { Character } from '@/types/character'
import type { Tournament } from '@/types/tournament'
import type { TeamCreateRequest, TeamCreateResponse } from '@/types/team'

vi.mock('@/api/teams', () => ({
  createTeam: vi.fn<(request: TeamCreateRequest) => Promise<TeamCreateResponse>>(),
}))

vi.mock('@/api/characters', () => ({
  fetchCharacters: vi.fn<() => Promise<Character[]>>(),
}))

vi.mock('@/api/tournaments', () => ({
  fetchTournamentById: vi.fn<(id: string) => Promise<Tournament>>(),
}))

const push = vi.fn<(path: string) => void>()

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ params: { id: '1' } }),
    useRouter: () => ({ push }),
  }
})

import { createTeam } from '@/api/teams'
import { fetchCharacters } from '@/api/characters'
import { fetchTournamentById } from '@/api/tournaments'

const sampleCharacters: Character[] = [
  { id: 1, name: 'Ryu' },
  { id: 2, name: 'Ken' },
]

const sampleTournament: Tournament = {
  id: 1,
  name: 'STREET FIGHTER 6 CUP',
  teamSize: 3,
  startAt: '2099-12-20T13:00:00',
  recruitmentDeadline: '2099-12-15T23:59:00',
  maxPlayers: 64,
  status: 'OPEN',
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

describe('TeamCreateView', () => {
  beforeEach(() => {
    vi.mocked(createTeam).mockReset()
    push.mockReset()
    vi.mocked(fetchCharacters).mockReset()
    vi.mocked(fetchCharacters).mockResolvedValue(sampleCharacters)
    vi.mocked(fetchTournamentById).mockReset()
    vi.mocked(fetchTournamentById).mockResolvedValue(sampleTournament)
  })

  it('fetchCharacters()でCharacter一覧を取得する', async () => {
    const wrapper = mount(TeamCreateView)
    await flushPromises()

    expect(fetchCharacters).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('Ryu')
    expect(wrapper.text()).toContain('Ken')
  })

  it('フォーム入力→createTeam→成功時に/teams/{id}へ遷移し、rankRequirementは英字コードで送信される', async () => {
    const created: TeamCreateResponse = {
      id: 100,
      tournamentId: 1,
      ownerId: 1,
      name: 'Team Ryu',
      rankRequirement: 'MASTER',
      characterRequirements: null,
      recruitmentMessage: null,
      createdAt: '2026-09-01T00:00:00',
      updatedAt: '2026-09-01T00:00:00',
    }
    vi.mocked(createTeam).mockResolvedValue(created)

    const wrapper = mount(TeamCreateView)
    await flushPromises()

    await wrapper.find('#team-form-name').setValue('Team Ryu')
    // 画面上は日本語(マスター)で選択するが、selectのvalueはAPIへ送る英字コード
    await wrapper.find('#team-form-rank').setValue('MASTER')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createTeam).toHaveBeenCalledWith({
      tournamentId: 1,
      name: 'Team Ryu',
      rankRequirement: 'MASTER',
      characterRequirements: null,
      recruitmentMessage: null,
    })
    expect(push).toHaveBeenCalledWith('/teams/100')
  })

  it('Character選択がcreateTeamのpayloadへ反映される', async () => {
    const created: TeamCreateResponse = {
      id: 100,
      tournamentId: 1,
      ownerId: 1,
      name: 'Team Ryu',
      rankRequirement: null,
      characterRequirements: [1, 2],
      recruitmentMessage: null,
      createdAt: '2026-09-01T00:00:00',
      updatedAt: '2026-09-01T00:00:00',
    }
    vi.mocked(createTeam).mockResolvedValue(created)

    const wrapper = mount(TeamCreateView)
    await flushPromises()

    await wrapper.find('#team-form-name').setValue('Team Ryu')
    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    await checkboxes[0]!.setValue(true)
    await checkboxes[1]!.setValue(true)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createTeam).toHaveBeenCalledWith(
      expect.objectContaining({ characterRequirements: [1, 2] }),
    )
  })

  it('Character未選択の場合、characterRequirementsはnullで送信される', async () => {
    const created: TeamCreateResponse = {
      id: 100,
      tournamentId: 1,
      ownerId: 1,
      name: 'Team Ryu',
      rankRequirement: null,
      characterRequirements: null,
      recruitmentMessage: null,
      createdAt: '2026-09-01T00:00:00',
      updatedAt: '2026-09-01T00:00:00',
    }
    vi.mocked(createTeam).mockResolvedValue(created)

    const wrapper = mount(TeamCreateView)
    await flushPromises()

    await wrapper.find('#team-form-name').setValue('Team Ryu')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createTeam).toHaveBeenCalledWith(
      expect.objectContaining({ characterRequirements: null }),
    )
  })

  it('409時に汎用メッセージ(所属済み/募集終了の可能性)が表示される', async () => {
    vi.mocked(createTeam).mockRejectedValue({
      isAxiosError: true,
      response: { status: 409 },
    })

    const wrapper = mount(TeamCreateView)
    await flushPromises()

    await wrapper.find('#team-form-name').setValue('Team Ryu')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain(
      'この大会では既にチームに所属しているか、募集が終了している可能性があります',
    )
  })

  it('CANCELボタンで大会詳細(/tournaments/{tournamentId})へ戻る', async () => {
    const wrapper = mount(TeamCreateView)
    await flushPromises()

    await wrapper.find('button[type="button"]').trigger('click')

    expect(push).toHaveBeenCalledWith('/tournaments/1')
    expect(createTeam).not.toHaveBeenCalled()
  })

  it('募集締切前はTeamFormが表示される', async () => {
    const wrapper = mount(TeamCreateView)
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(true)
  })

  it('募集締切後はTeamFormを表示せず案内メッセージを表示する', async () => {
    vi.mocked(fetchTournamentById).mockResolvedValue({
      ...sampleTournament,
      recruitmentDeadline: '2020-01-01T00:00:00',
    })

    const wrapper = mount(TeamCreateView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('この大会のチーム募集は終了しました')
  })

  it('Tournament取得に失敗してもフォームは表示される(最終判定はbackendの409に委ねる)', async () => {
    vi.mocked(fetchTournamentById).mockRejectedValue(new Error('network error'))

    const wrapper = mount(TeamCreateView)
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(true)
  })
})
