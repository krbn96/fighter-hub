import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'

import TournamentTeamsView from '../TournamentTeamsView.vue'
import type { FetchTeamsByTournamentParams } from '@/api/teams'
import type { Team } from '@/types/team'
import type { Character } from '@/types/character'

vi.mock('@/api/teams', () => ({
  fetchTeamsByTournament: vi.fn<
    (tournamentId: string, params?: FetchTeamsByTournamentParams) => Promise<Team[]>
  >(),
}))

vi.mock('@/api/characters', () => ({
  fetchCharacters: vi.fn<() => Promise<Character[]>>(),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ params: { id: '1' } }),
  }
})

import { fetchTeamsByTournament } from '@/api/teams'
import { fetchCharacters } from '@/api/characters'

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

const sampleCharacters: Character[] = [
  { id: 1, name: 'RYU' },
  { id: 2, name: 'KEN' },
]

function mountView() {
  return mount(TournamentTeamsView, {
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
}

describe('TournamentTeamsView', () => {
  beforeEach(() => {
    vi.mocked(fetchTeamsByTournament).mockReset()
    vi.mocked(fetchCharacters).mockReset()
    vi.mocked(fetchCharacters).mockResolvedValue(sampleCharacters)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('API取得成功時にチーム名が表示される', async () => {
    vi.mocked(fetchTeamsByTournament).mockResolvedValue([sampleTeam])

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Team Ryu')
  })

  it('初期表示はavailable=trueで取得し他の条件は未指定で取得する', async () => {
    vi.mocked(fetchTeamsByTournament).mockResolvedValue([sampleTeam])

    mountView()
    await flushPromises()

    expect(fetchTeamsByTournament).toHaveBeenCalledWith('1', {
      name: '',
      characterId: undefined,
      rank: undefined,
      available: true,
    })
  })

  it('0件時にこの大会にはまだチームがありませんと表示される', async () => {
    vi.mocked(fetchTeamsByTournament).mockResolvedValue([])

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('この大会にはまだチームがありません')
  })

  it('大会不存在の404時に専用メッセージが表示される', async () => {
    vi.mocked(fetchTeamsByTournament).mockRejectedValue({
      isAxiosError: true,
      response: { status: 404 },
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('指定した大会が見つかりません')
  })

  it('404以外のエラー時は汎用エラーメッセージが表示される', async () => {
    vi.mocked(fetchTeamsByTournament).mockRejectedValue(new Error('network error'))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('チーム一覧の取得に失敗しました')
  })

  it('チーム名を入力すると入力停止から一定時間後にnameを付けて再取得する', async () => {
    vi.useFakeTimers()
    vi.mocked(fetchTeamsByTournament).mockResolvedValue([sampleTeam])

    const wrapper = mountView()
    await flushPromises()
    vi.mocked(fetchTeamsByTournament).mockClear()

    await wrapper.find('.tournament-teams-view__search-name').setValue('ryu')

    vi.advanceTimersByTime(299)
    await flushPromises()
    expect(fetchTeamsByTournament).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1)
    await flushPromises()
    expect(fetchTeamsByTournament).toHaveBeenCalledWith('1', {
      name: 'ryu',
      characterId: undefined,
      rank: undefined,
      available: true,
    })
  })

  it('募集キャラクターを選択すると即時characterIdを付けて再取得する', async () => {
    vi.mocked(fetchTeamsByTournament).mockResolvedValue([sampleTeam])

    const wrapper = mountView()
    await flushPromises()
    vi.mocked(fetchTeamsByTournament).mockClear()

    await wrapper.find('.tournament-teams-view__search-character').setValue('2')
    await flushPromises()

    expect(fetchTeamsByTournament).toHaveBeenCalledWith('1', {
      name: '',
      characterId: 2,
      rank: undefined,
      available: true,
    })
  })

  it('プレイヤーランクを選択すると即時rankを付けて再取得する', async () => {
    vi.mocked(fetchTeamsByTournament).mockResolvedValue([sampleTeam])

    const wrapper = mountView()
    await flushPromises()
    vi.mocked(fetchTeamsByTournament).mockClear()

    await wrapper.find('.tournament-teams-view__search-rank').setValue('DIAMOND')
    await flushPromises()

    expect(fetchTeamsByTournament).toHaveBeenCalledWith('1', {
      name: '',
      characterId: undefined,
      rank: 'DIAMOND',
      available: true,
    })
  })

  it('空き状況を「すべて」に変更すると即時availableパラメータを送らずに再取得する', async () => {
    vi.mocked(fetchTeamsByTournament).mockResolvedValue([sampleTeam])

    const wrapper = mountView()
    await flushPromises()
    vi.mocked(fetchTeamsByTournament).mockClear()

    await wrapper.find('.tournament-teams-view__search-available').setValue('ALL')
    await flushPromises()

    expect(fetchTeamsByTournament).toHaveBeenCalledWith('1', {
      name: '',
      characterId: undefined,
      rank: undefined,
      available: undefined,
    })
  })

  it('チーム名と募集キャラクターとプレイヤーランクを組み合わせて検索できる', async () => {
    vi.useFakeTimers()
    vi.mocked(fetchTeamsByTournament).mockResolvedValue([sampleTeam])

    const wrapper = mountView()
    await flushPromises()
    vi.mocked(fetchTeamsByTournament).mockClear()

    await wrapper.find('.tournament-teams-view__search-name').setValue('ryu')
    vi.advanceTimersByTime(300)
    await flushPromises()

    await wrapper.find('.tournament-teams-view__search-character').setValue('1')
    await flushPromises()

    await wrapper.find('.tournament-teams-view__search-rank').setValue('DIAMOND')
    await flushPromises()

    expect(fetchTeamsByTournament).toHaveBeenLastCalledWith('1', {
      name: 'ryu',
      characterId: 1,
      rank: 'DIAMOND',
      available: true,
    })
  })

  it('検索条件に一致するチームが0件の場合は条件に一致するチームが見つかりませんと表示される', async () => {
    vi.mocked(fetchTeamsByTournament).mockResolvedValueOnce([sampleTeam])

    const wrapper = mountView()
    await flushPromises()

    vi.mocked(fetchTeamsByTournament).mockResolvedValueOnce([])
    await wrapper.find('.tournament-teams-view__search-rank').setValue('MASTER')
    await flushPromises()

    expect(wrapper.text()).toContain('条件に一致するチームが見つかりません')
  })

  it('キャラクター一覧の取得に失敗してもチーム検索自体は表示される', async () => {
    vi.mocked(fetchCharacters).mockRejectedValue(new Error('network error'))
    vi.mocked(fetchTeamsByTournament).mockResolvedValue([sampleTeam])

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Team Ryu')
    expect(wrapper.find('.tournament-teams-view__search-character').exists()).toBe(true)
  })
})
