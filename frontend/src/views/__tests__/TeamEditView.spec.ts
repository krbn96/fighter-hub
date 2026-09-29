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

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn<() => Promise<{ data: Character[] }>>(),
  },
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
import { apiClient } from '@/api/client'

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
    discordId: null,
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
    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.get).mockResolvedValue({ data: sampleCharacters })
  })

  it('fetchTeamByIdで取得した既存値がフォーム初期値へ反映される(rankRequirementのselectを含む)', async () => {
    const authStore = useAuthStore()
    authStore.user = createUserMe(sampleTeam.ownerId)
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)

    const wrapper = mount(TeamEditView)
    await flushPromises()

    const nameInput = wrapper.find<HTMLInputElement>('#name')
    const rankSelect = wrapper.find<HTMLSelectElement>('#rankRequirement')

    expect(nameInput.element.value).toBe('Team Ryu')
    expect(rankSelect.element.value).toBe('MASTER')
  })

  it('non-ownerの場合フォームを表示せず「このチームを編集する権限がありません」が表示される', async () => {
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
})
