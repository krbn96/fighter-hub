import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import TeamEditView from '../TeamEditView.vue'
import { useAuthStore } from '@/stores/auth'
import type { Character } from '@/types/character'
import type { Team, TeamUpdateRequest } from '@/types/team'
import type { UserMe } from '@/types/user'

vi.mock('@/api/teams', () => ({
  fetchTeamById: vi.fn<(id: string) => Promise<Team>>(),
  updateTeam: vi.fn<(id: string, request: TeamUpdateRequest) => Promise<Team>>(),
}))

vi.mock('@/api/characters', () => ({
  fetchCharacters: vi.fn<() => Promise<Character[]>>(),
}))

const push = vi.fn<(path: string) => void>()

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ params: { id: '10' } }),
    useRouter: () => ({ push }),
  }
})

import { fetchTeamById, updateTeam } from '@/api/teams'
import { fetchCharacters } from '@/api/characters'

const sampleCharacters: Character[] = [
  { id: 1, name: 'Ryu' },
  { id: 2, name: 'Ken' },
]

const sampleTeam: Team = {
  id: 10,
  tournamentId: 1,
  tournamentName: 'STREET FIGHTER 6 CUP',
  ownerId: 1,
  ownerName: 'Owner User',
  name: 'Team Ryu',
  rankRequirement: 'MASTER',
  characterRequirements: [1],
  recruitmentMessage: '誰でも歓迎です',
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

function createUserMe(id: number): UserMe {
  return {
    id,
    name: 'Test User',
    characters: [],
    playTimeStart: null,
    playTimeEnd: null,
    message: null,
    email: 'test@example.com',
    xId: null,
    discordUsername: null,
    createdAt: '2026-09-01T00:00:00',
    updatedAt: '2026-09-01T00:00:00',
  }
}

describe('TeamEditView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(fetchTeamById).mockReset()
    vi.mocked(updateTeam).mockReset()
    push.mockReset()
    vi.mocked(fetchCharacters).mockReset()
    vi.mocked(fetchCharacters).mockResolvedValue(sampleCharacters)
  })

  it('fetchTeamById + fetchCharactersを並行取得し、既存値がフォーム初期値へ反映される(rankRequirementのselectを含む)', async () => {
    const authStore = useAuthStore()
    authStore.user = createUserMe(sampleTeam.ownerId)
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)

    const wrapper = mount(TeamEditView)
    await flushPromises()

    expect(fetchTeamById).toHaveBeenCalledWith('10')
    expect(fetchCharacters).toHaveBeenCalledTimes(1)

    const nameInput = wrapper.find<HTMLInputElement>('#team-form-name')
    const rankSelect = wrapper.find<HTMLSelectElement>('#team-form-rank')

    expect(nameInput.element.value).toBe('Team Ryu')
    expect(rankSelect.element.value).toBe('MASTER')

    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    expect((checkboxes[0]!.element as HTMLInputElement).checked).toBe(true)
    expect((checkboxes[1]!.element as HTMLInputElement).checked).toBe(false)
  })

  it('非ownerの場合フォームを表示せず「このチームを編集する権限がありません」が表示される', async () => {
    const authStore = useAuthStore()
    authStore.user = createUserMe(sampleTeam.ownerId + 999)
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)

    const wrapper = mount(TeamEditView)
    await flushPromises()

    expect(wrapper.text()).toContain('このチームを編集する権限がありません')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('更新成功時に/teams/{id}へ遷移する', async () => {
    const authStore = useAuthStore()
    authStore.user = createUserMe(sampleTeam.ownerId)
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(updateTeam).mockResolvedValue(sampleTeam)

    const wrapper = mount(TeamEditView)
    await flushPromises()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(updateTeam).toHaveBeenCalledWith('10', {
      name: 'Team Ryu',
      rankRequirement: 'MASTER',
      characterRequirements: [1],
      recruitmentMessage: '誰でも歓迎です',
    })
    expect(push).toHaveBeenCalledWith('/teams/10')
  })

  it('Character変更がupdateTeamのpayloadへ反映される', async () => {
    const authStore = useAuthStore()
    authStore.user = createUserMe(sampleTeam.ownerId)
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(updateTeam).mockResolvedValue(sampleTeam)

    const wrapper = mount(TeamEditView)
    await flushPromises()

    // sampleTeamはcharacterRequirements:[1]で初期化されているため、2番目(Ken)も追加選択する
    await wrapper.findAll('input[type="checkbox"]')[1]!.setValue(true)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(updateTeam).toHaveBeenCalledWith(
      '10',
      expect.objectContaining({ characterRequirements: [1, 2] }),
    )
  })

  it('404時に「指定したチームが見つかりません」が表示される', async () => {
    vi.mocked(fetchTeamById).mockRejectedValue({
      isAxiosError: true,
      response: { status: 404 },
    })

    const wrapper = mount(TeamEditView)
    await flushPromises()

    expect(wrapper.text()).toContain('指定したチームが見つかりません')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('CANCELボタンでチーム詳細(/teams/{teamId})へ戻る', async () => {
    const authStore = useAuthStore()
    authStore.user = createUserMe(sampleTeam.ownerId)
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)

    const wrapper = mount(TeamEditView)
    await flushPromises()

    await wrapper.find('button[type="button"]').trigger('click')

    expect(push).toHaveBeenCalledWith('/teams/10')
    expect(updateTeam).not.toHaveBeenCalled()
  })
})
