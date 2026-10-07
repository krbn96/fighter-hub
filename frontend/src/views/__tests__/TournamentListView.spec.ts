import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'

import TournamentListView from '../TournamentListView.vue'
import type { FetchTournamentsParams } from '@/api/tournaments'
import type { Tournament } from '@/types/tournament'

vi.mock('@/api/tournaments', () => ({
  fetchTournaments: vi.fn<(params?: FetchTournamentsParams) => Promise<Tournament[]>>(),
}))

import { fetchTournaments } from '@/api/tournaments'

const sampleTournament: Tournament = {
  id: 1,
  name: 'STREET FIGHTER 6 CUP',
  teamSize: 3,
  startAt: '2026-10-10T13:00:00',
  recruitmentDeadline: '2026-10-05T23:59:00',
  maxPlayers: 64,
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

function mountView() {
  return mount(TournamentListView, {
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
}

describe('TournamentListView', () => {
  beforeEach(() => {
    vi.mocked(fetchTournaments).mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('API取得成功時に大会名が表示される', async () => {
    vi.mocked(fetchTournaments).mockResolvedValue([sampleTournament])

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('STREET FIGHTER 6 CUP')
    expect(wrapper.text()).toContain('2026/10/10 13:00')
  })

  it('初期表示時はnameを空文字・recruitingをundefinedで取得する(従来どおり全件取得)', async () => {
    vi.mocked(fetchTournaments).mockResolvedValue([sampleTournament])

    mountView()
    await flushPromises()

    expect(fetchTournaments).toHaveBeenCalledWith({ name: '', recruiting: undefined })
  })

  it('API取得失敗時にエラーメッセージが表示される', async () => {
    vi.mocked(fetchTournaments).mockRejectedValue(new Error('network error'))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('大会一覧の取得に失敗しました')
  })

  it('フィルタなしで0件の場合は「大会がありません」を表示する', async () => {
    vi.mocked(fetchTournaments).mockResolvedValue([])

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('大会がありません')
    expect(wrapper.text()).not.toContain('条件に一致する大会がありません')
  })

  it('大会名を入力すると入力停止から一定時間後にnameを付けて再取得する', async () => {
    vi.useFakeTimers()
    vi.mocked(fetchTournaments).mockResolvedValue([sampleTournament])

    const wrapper = mountView()
    await flushPromises()
    vi.mocked(fetchTournaments).mockClear()

    await wrapper.find('.tournament-list-view__search-name').setValue('street')

    vi.advanceTimersByTime(299)
    await flushPromises()
    expect(fetchTournaments).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1)
    await flushPromises()
    expect(fetchTournaments).toHaveBeenCalledWith({ name: 'street', recruiting: undefined })
  })

  it('募集状態を「募集中」に変更すると即時recruiting=trueで再取得する', async () => {
    vi.mocked(fetchTournaments).mockResolvedValue([sampleTournament])

    const wrapper = mountView()
    await flushPromises()
    vi.mocked(fetchTournaments).mockClear()

    await wrapper.find('.tournament-list-view__search-status').setValue('RECRUITING')
    await flushPromises()

    expect(fetchTournaments).toHaveBeenCalledWith({ name: '', recruiting: true })
  })

  it('募集状態を「募集終了」に変更すると即時recruiting=falseで再取得する', async () => {
    vi.mocked(fetchTournaments).mockResolvedValue([sampleTournament])

    const wrapper = mountView()
    await flushPromises()
    vi.mocked(fetchTournaments).mockClear()

    await wrapper.find('.tournament-list-view__search-status').setValue('CLOSED')
    await flushPromises()

    expect(fetchTournaments).toHaveBeenCalledWith({ name: '', recruiting: false })
  })

  it('大会名と募集状態を組み合わせて検索できる', async () => {
    vi.useFakeTimers()
    vi.mocked(fetchTournaments).mockResolvedValue([sampleTournament])

    const wrapper = mountView()
    await flushPromises()
    vi.mocked(fetchTournaments).mockClear()

    await wrapper.find('.tournament-list-view__search-name').setValue('street')
    vi.advanceTimersByTime(300)
    await flushPromises()

    await wrapper.find('.tournament-list-view__search-status').setValue('RECRUITING')
    await flushPromises()

    expect(fetchTournaments).toHaveBeenLastCalledWith({ name: 'street', recruiting: true })
  })

  it('検索条件に一致する大会が0件の場合は「条件に一致する大会がありません」を表示する', async () => {
    vi.mocked(fetchTournaments).mockResolvedValueOnce([sampleTournament])

    const wrapper = mountView()
    await flushPromises()

    vi.mocked(fetchTournaments).mockResolvedValueOnce([])
    await wrapper.find('.tournament-list-view__search-status').setValue('CLOSED')
    await flushPromises()

    expect(wrapper.text()).toContain('条件に一致する大会がありません')
  })

  it('検索操作によってページ全体をリロードしない(同一コンポーネントインスタンスが再利用される)', async () => {
    vi.mocked(fetchTournaments).mockResolvedValue([sampleTournament])

    const wrapper = mountView()
    await flushPromises()
    const instanceBefore = wrapper.vm

    await wrapper.find('.tournament-list-view__search-status').setValue('RECRUITING')
    await flushPromises()

    expect(wrapper.vm).toBe(instanceBefore)
  })
})
