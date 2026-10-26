import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'

import AdminTournamentListView from '../AdminTournamentListView.vue'
import type { Tournament } from '@/types/tournament'

vi.mock('@/api/tournaments', () => ({
  fetchTournaments: vi.fn<() => Promise<Tournament[]>>(),
  deleteTournament: vi.fn<(id: string) => Promise<void>>(),
}))

import { deleteTournament, fetchTournaments } from '@/api/tournaments'

const sampleTournaments: Tournament[] = [
  {
    id: 1,
    name: 'FIGHTER HUB CUP',
    teamSize: 3,
    startAt: '2026-12-20T13:00:00',
    recruitmentDeadline: '2026-12-15T23:59:00',
    maxPlayers: 64,
    createdAt: '2026-09-01T00:00:00',
    updatedAt: '2026-09-01T00:00:00',
  },
]

// BaseModalは<Teleport to="body">で実DOMのdocument.bodyへ描画されるため、
// VTUの通常のマウント先とは異なりテスト間で残り続けてしまう(MyPageView.spec.tsと同じ対策)。
function modalDialog(): HTMLElement | null {
  return document.querySelector('[role="dialog"]')
}

async function mountReady() {
  const wrapper = mount(AdminTournamentListView, {
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
  await flushPromises()
  return wrapper
}

describe('AdminTournamentListView', () => {
  beforeEach(() => {
    vi.mocked(fetchTournaments).mockReset()
    vi.mocked(fetchTournaments).mockResolvedValue(sampleTournaments)
    vi.mocked(deleteTournament).mockReset()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('大会一覧が表示されCREATE/EDITリンクの行き先が正しい', async () => {
    const wrapper = await mountReady()

    expect(wrapper.text()).toContain('FIGHTER HUB CUP')

    const links = wrapper.findAllComponents(RouterLinkStub)
    const createLink = links.find((link) => link.text() === 'CREATE TOURNAMENT')
    const editLink = links.find((link) => link.text() === 'EDIT')

    expect(createLink?.props('to')).toBe('/admin/tournaments/new')
    expect(editLink?.props('to')).toBe('/admin/tournaments/1/edit')
  })

  it('大会が0件の場合は空の旨を表示する', async () => {
    vi.mocked(fetchTournaments).mockResolvedValue([])

    const wrapper = await mountReady()

    expect(wrapper.text()).toContain('大会がありません')
  })

  it('DELETEボタン押下で確認モーダルが表示され、配下チームが操作できなくなる旨が明記される', async () => {
    const wrapper = await mountReady()

    const deleteButton = wrapper.findAll('button').find((b) => b.text() === 'DELETE')
    await deleteButton?.trigger('click')

    expect(modalDialog()).not.toBeNull()
    expect(modalDialog()?.textContent).toContain('大会削除後、配下のチームは表示・操作できなくなります')
    expect(deleteTournament).not.toHaveBeenCalled()
  })

  it('確認モーダルでキャンセルすると削除されずモーダルが閉じる', async () => {
    const wrapper = await mountReady()

    const deleteButton = wrapper.findAll('button').find((b) => b.text() === 'DELETE')
    await deleteButton?.trigger('click')
    expect(modalDialog()).not.toBeNull()

    const cancelButton = Array.from(modalDialog()?.querySelectorAll('button') ?? []).find(
      (button) => button.textContent?.includes('キャンセル'),
    )
    cancelButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    expect(modalDialog()).toBeNull()
    expect(deleteTournament).not.toHaveBeenCalled()
  })

  it('確認モーダルで削除するを押下するとdeleteTournamentが呼ばれ一覧が更新される', async () => {
    vi.mocked(deleteTournament).mockResolvedValue(undefined)
    const wrapper = await mountReady()

    const deleteButton = wrapper.findAll('button').find((b) => b.text() === 'DELETE')
    await deleteButton?.trigger('click')

    vi.mocked(fetchTournaments).mockResolvedValue([])

    const confirmButton = Array.from(modalDialog()?.querySelectorAll('button') ?? []).find(
      (button) => button.textContent?.includes('削除する'),
    )
    confirmButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    expect(deleteTournament).toHaveBeenCalledWith('1')
    expect(modalDialog()).toBeNull()
    expect(fetchTournaments).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('大会がありません')
  })

  it('削除が403で失敗した場合はモーダル内にADMIN権限が必要な旨を表示する', async () => {
    vi.mocked(deleteTournament).mockRejectedValue({
      isAxiosError: true,
      response: { status: 403 },
    })
    const wrapper = await mountReady()

    const deleteButton = wrapper.findAll('button').find((b) => b.text() === 'DELETE')
    await deleteButton?.trigger('click')

    const confirmButton = Array.from(modalDialog()?.querySelectorAll('button') ?? []).find(
      (button) => button.textContent?.includes('削除する'),
    )
    confirmButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    expect(modalDialog()?.textContent).toContain('ADMIN権限が必要です')
  })
})
