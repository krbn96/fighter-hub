import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

import AdminTournamentCreateView from '../AdminTournamentCreateView.vue'
import type { Tournament, TournamentCreateRequest } from '@/types/tournament'

vi.mock('@/api/tournaments', () => ({
  createTournament: vi.fn<(request: TournamentCreateRequest) => Promise<Tournament>>(),
}))

const push = vi.fn<(path: string) => void>()

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({ push }),
  }
})

import { createTournament } from '@/api/tournaments'

const createdTournament: Tournament = {
  id: 1,
  name: 'FIGHTER HUB CUP',
  teamSize: 3,
  startAt: '2026-12-20T13:00:00',
  recruitmentDeadline: '2026-12-15T23:59:00',
  maxPlayers: 64,
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

describe('AdminTournamentCreateView', () => {
  beforeEach(() => {
    vi.mocked(createTournament).mockReset()
    push.mockReset()
  })

  it('正常入力で送信すると、createTournamentが正しいrequest bodyで呼ばれ/admin/tournamentsへ遷移する', async () => {
    vi.mocked(createTournament).mockResolvedValue(createdTournament)

    const wrapper = mount(AdminTournamentCreateView)

    await wrapper.find('#tournament-form-name').setValue('FIGHTER HUB CUP')
    await wrapper.find('#tournament-form-team-size').setValue('3')
    await wrapper.find('#tournament-form-max-players').setValue('64')
    await wrapper.find('#tournament-form-start-at').setValue('2026-12-20T13:00')
    await wrapper.find('#tournament-form-deadline').setValue('2026-12-15T23:59')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createTournament).toHaveBeenCalledWith({
      name: 'FIGHTER HUB CUP',
      teamSize: 3,
      maxPlayers: 64,
      startAt: '2026-12-20T13:00:00',
      recruitmentDeadline: '2026-12-15T23:59:00',
    })
    expect(push).toHaveBeenCalledWith('/admin/tournaments')
  })

  it('400エラーの場合は入力確認を促すメッセージを表示しpushしない', async () => {
    vi.mocked(createTournament).mockRejectedValue({
      isAxiosError: true,
      response: { status: 400 },
    })

    const wrapper = mount(AdminTournamentCreateView)

    await wrapper.find('#tournament-form-name').setValue('FIGHTER HUB CUP')
    await wrapper.find('#tournament-form-team-size').setValue('3')
    await wrapper.find('#tournament-form-max-players').setValue('64')
    await wrapper.find('#tournament-form-start-at').setValue('2026-12-20T13:00')
    await wrapper.find('#tournament-form-deadline').setValue('2026-12-15T23:59')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('入力内容を確認してください')
    expect(push).not.toHaveBeenCalled()
  })

  it('403エラーの場合はADMIN権限が必要なことを表示する', async () => {
    vi.mocked(createTournament).mockRejectedValue({
      isAxiosError: true,
      response: { status: 403 },
    })

    const wrapper = mount(AdminTournamentCreateView)

    await wrapper.find('#tournament-form-name').setValue('FIGHTER HUB CUP')
    await wrapper.find('#tournament-form-team-size').setValue('3')
    await wrapper.find('#tournament-form-max-players').setValue('64')
    await wrapper.find('#tournament-form-start-at').setValue('2026-12-20T13:00')
    await wrapper.find('#tournament-form-deadline').setValue('2026-12-15T23:59')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('ADMIN権限が必要です')
  })

  it('cancelボタン押下で/admin/tournamentsへ遷移する', async () => {
    const wrapper = mount(AdminTournamentCreateView)

    const cancelButton = wrapper.findAll('button').find((button) => button.text() === 'CANCEL')
    await cancelButton?.trigger('click')

    expect(push).toHaveBeenCalledWith('/admin/tournaments')
  })
})
