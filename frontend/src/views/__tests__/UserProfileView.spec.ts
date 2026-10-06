import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

import UserProfileView from '../UserProfileView.vue'
import type { UserPublic } from '@/types/user'
import type { Character } from '@/types/character'

vi.mock('@/api/users', () => ({
  fetchUserById: vi.fn<(userId: number) => Promise<UserPublic>>(),
}))

vi.mock('@/api/characters', () => ({
  fetchCharacters: vi.fn<() => Promise<Character[]>>(),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ params: { id: '5' } }),
  }
})

import { fetchUserById } from '@/api/users'
import { fetchCharacters } from '@/api/characters'

const samplePublicUser: UserPublic = {
  id: 5,
  name: 'Public User',
  characters: [{ characterId: 1, rank: 'MASTER', mr: 1650 }],
  playTimeStart: '20:00:00',
  playTimeEnd: '23:00:00',
  message: 'よろしく',
  xId: 'somexid',
  discordUsername: 'some#1234',
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

const sampleCharacters: Character[] = [{ id: 1, name: 'RYU' }]

describe('UserProfileView', () => {
  beforeEach(() => {
    vi.mocked(fetchUserById).mockReset()
    vi.mocked(fetchCharacters).mockReset()
  })

  it('公開ユーザー情報を取得しCharacter名へ変換して表示する', async () => {
    vi.mocked(fetchUserById).mockResolvedValue(samplePublicUser)
    vi.mocked(fetchCharacters).mockResolvedValue(sampleCharacters)

    const wrapper = mount(UserProfileView)
    await flushPromises()

    expect(fetchUserById).toHaveBeenCalledWith(5)
    const text = wrapper.text()
    expect(text).toContain('Public User')
    expect(text).toContain('RYU')
    expect(text).toContain('MASTER')
    expect(text).toContain('MR 1650')
  })

  it('emailやMy Teams/My Applications導線、xId/discordUsernameを表示しない', async () => {
    vi.mocked(fetchUserById).mockResolvedValue(samplePublicUser)
    vi.mocked(fetchCharacters).mockResolvedValue(sampleCharacters)

    const wrapper = mount(UserProfileView)
    await flushPromises()

    const text = wrapper.text()
    expect(text).not.toContain('ACCOUNT')
    expect(text).not.toContain('MY TEAMS')
    expect(text).not.toContain('MY APPLICATIONS')
    expect(text).not.toContain('somexid')
    expect(text).not.toContain('some#1234')
    expect(wrapper.html()).not.toContain('email')
  })

  it('404時に専用のnot found表示になる', async () => {
    vi.mocked(fetchUserById).mockRejectedValue({
      isAxiosError: true,
      response: { status: 404 },
    })
    vi.mocked(fetchCharacters).mockResolvedValue(sampleCharacters)

    const wrapper = mount(UserProfileView)
    await flushPromises()

    expect(wrapper.text()).toContain('指定したユーザーが見つかりません')
  })

  it('404以外のエラー時はretry可能な汎用エラーが表示される', async () => {
    vi.mocked(fetchUserById).mockRejectedValue(new Error('network error'))
    vi.mocked(fetchCharacters).mockResolvedValue(sampleCharacters)

    const wrapper = mount(UserProfileView)
    await flushPromises()

    expect(wrapper.text()).toContain('ユーザー情報の取得に失敗しました')
  })

  it('Character取得が失敗してもProfile自体は表示される', async () => {
    vi.mocked(fetchUserById).mockResolvedValue(samplePublicUser)
    vi.mocked(fetchCharacters).mockRejectedValue(new Error('network error'))

    const wrapper = mount(UserProfileView)
    await flushPromises()

    expect(wrapper.text()).toContain('Public User')
    expect(wrapper.text()).toContain('不明なキャラクター')
  })
})
