import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

import TeamCreateView from '../TeamCreateView.vue'
import type { Character } from '@/types/character'
import type { TeamCreateRequest, TeamCreateResponse } from '@/types/team'

vi.mock('@/api/teams', () => ({
  createTeam: vi.fn<(request: TeamCreateRequest) => Promise<TeamCreateResponse>>(),
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
    useRoute: () => ({ params: { id: '1' } }),
    useRouter: () => ({ push }),
  }
})

import { createTeam } from '@/api/teams'
import { apiClient } from '@/api/client'

const sampleCharacters: Character[] = [
  { id: 1, name: 'Ryu' },
  { id: 2, name: 'Ken' },
]

describe('TeamCreateView', () => {
  beforeEach(() => {
    vi.mocked(createTeam).mockReset()
    push.mockReset()
    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.get).mockResolvedValue({ data: sampleCharacters })
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

    await wrapper.find('#name').setValue('Team Ryu')
    // 画面上は日本語(マスター)で選択するが、selectのvalueはAPIへ送る英字コード
    await wrapper.find('#rankRequirement').setValue('MASTER')
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

  it('409時に「この大会では既にチームに所属しています」が表示される', async () => {
    vi.mocked(createTeam).mockRejectedValue({
      isAxiosError: true,
      response: { status: 409 },
    })

    const wrapper = mount(TeamCreateView)
    await flushPromises()

    await wrapper.find('#name').setValue('Team Ryu')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('この大会では既にチームに所属しています')
  })
})
