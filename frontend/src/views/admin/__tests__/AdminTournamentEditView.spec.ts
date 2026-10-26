import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

import AdminTournamentEditView from '../AdminTournamentEditView.vue'
import type { Tournament, TournamentUpdateRequest } from '@/types/tournament'

vi.mock('@/api/tournaments', () => ({
  fetchTournamentById: vi.fn<(id: string) => Promise<Tournament>>(),
  updateTournament: vi.fn<(id: string, request: TournamentUpdateRequest) => Promise<Tournament>>(),
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

import { fetchTournamentById, updateTournament } from '@/api/tournaments'

const sampleTournament: Tournament = {
  id: 1,
  name: 'FIGHTER HUB CUP',
  teamSize: 3,
  startAt: '2026-12-20T13:00:00',
  recruitmentDeadline: '2026-12-15T23:59:00',
  maxPlayers: 64,
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

async function mountReady() {
  const wrapper = mount(AdminTournamentEditView)
  await flushPromises()
  return wrapper
}

describe('AdminTournamentEditView', () => {
  beforeEach(() => {
    vi.mocked(fetchTournamentById).mockReset()
    vi.mocked(fetchTournamentById).mockResolvedValue(sampleTournament)
    vi.mocked(updateTournament).mockReset()
    push.mockReset()
  })

  it('取得した大会情報が秒無しのdatetime-local形式でフォームに初期表示される', async () => {
    const wrapper = await mountReady()

    expect((wrapper.find('#tournament-form-name').element as HTMLInputElement).value).toBe(
      'FIGHTER HUB CUP',
    )
    expect(
      (wrapper.find('#tournament-form-start-at').element as HTMLInputElement).value,
    ).toBe('2026-12-20T13:00')
    expect(
      (wrapper.find('#tournament-form-deadline').element as HTMLInputElement).value,
    ).toBe('2026-12-15T23:59')
  })

  it('更新成功時にupdateTournamentが正しいrequest bodyで呼ばれ/admin/tournamentsへ遷移する', async () => {
    vi.mocked(updateTournament).mockResolvedValue(sampleTournament)
    const wrapper = await mountReady()

    await wrapper.find('#tournament-form-name').setValue('Updated Cup')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(updateTournament).toHaveBeenCalledWith('1', {
      name: 'Updated Cup',
      teamSize: 3,
      maxPlayers: 64,
      startAt: '2026-12-20T13:00:00',
      recruitmentDeadline: '2026-12-15T23:59:00',
    })
    expect(push).toHaveBeenCalledWith('/admin/tournaments')
  })

  it('404時に指定した大会が見つからない旨を表示する', async () => {
    vi.mocked(fetchTournamentById).mockRejectedValue({
      isAxiosError: true,
      response: { status: 404 },
    })

    const wrapper = await mountReady()

    expect(wrapper.text()).toContain('指定した大会が見つかりません')
  })

  it('更新時の403エラーでADMIN権限が必要なことを表示する', async () => {
    vi.mocked(updateTournament).mockRejectedValue({
      isAxiosError: true,
      response: { status: 403 },
    })

    const wrapper = await mountReady()
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('ADMIN権限が必要です')
  })
})
