import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import MyPageView from '../MyPageView.vue'
import { useAuthStore } from '@/stores/auth'
import type { UserMe, UserUpdateRequest } from '@/types/user'
import type { Character } from '@/types/character'

vi.mock('@/api/characters', () => ({
  fetchCharacters: vi.fn<() => Promise<Character[]>>(),
}))

vi.mock('@/api/users', () => ({
  updateMe: vi.fn<(request: UserUpdateRequest) => Promise<UserMe>>(),
}))

import { fetchCharacters } from '@/api/characters'
import { updateMe } from '@/api/users'

const sampleUser: UserMe = {
  id: 1,
  name: 'Test User',
  characters: [{ characterId: 1, rank: 'MASTER', mr: 1650 }],
  playTimeStart: '20:00:00',
  playTimeEnd: '23:00:00',
  message: 'よろしく',
  email: 'test@example.com',
  xId: 'somexid',
  discordUsername: 'some#1234',
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

describe('MyPageView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(fetchCharacters).mockReset()
    vi.mocked(fetchCharacters).mockResolvedValue([{ id: 1, name: 'RYU' }])
    vi.mocked(updateMe).mockReset()
  })

  it('emailが表示される', async () => {
    const authStore = useAuthStore()
    authStore.user = sampleUser

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('test@example.com')
  })

  it('Character IDではなくCharacter名が表示される', async () => {
    const authStore = useAuthStore()
    authStore.user = sampleUser

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(fetchCharacters).toHaveBeenCalled()
    expect(wrapper.text()).toContain('RYU')
    expect(wrapper.text()).not.toContain('characterId')
  })

  it('MY TEAMS / MY APPLICATIONSへのリンクが表示される', async () => {
    const authStore = useAuthStore()
    authStore.user = sampleUser

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    const links = wrapper.findAllComponents(RouterLinkStub)
    const myTeamsLink = links.find((link) => link.text().includes('MY TEAMS'))
    const myApplicationsLink = links.find((link) => link.text().includes('MY APPLICATIONS'))

    expect(myTeamsLink?.props('to')).toBe('/teams/my')
    expect(myApplicationsLink?.props('to')).toBe('/applications/my')
  })

  it('Character取得が失敗してもemail等の表示は継続される', async () => {
    vi.mocked(fetchCharacters).mockRejectedValue(new Error('network error'))

    const authStore = useAuthStore()
    authStore.user = sampleUser

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('test@example.com')
    expect(wrapper.text()).toContain('不明なキャラクター')
  })

  it('EDIT PROFILEボタンをクリックするとroute遷移せず編集モードへ切り替わる', async () => {
    const authStore = useAuthStore()
    authStore.user = sampleUser

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(false)

    const editButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('EDIT PROFILE'))
    expect(editButton?.attributes('type')).toBe('button')

    await editButton?.trigger('click')

    expect(wrapper.find('form').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('EDIT PROFILE')
  })

  it('CANCELでAPIを呼ばず編集内容を破棄して通常表示へ戻る', async () => {
    const authStore = useAuthStore()
    authStore.user = sampleUser

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('EDIT PROFILE'))
      ?.trigger('click')

    await wrapper.find('#profile-edit-name').setValue('Changed Name')
    await wrapper.find('button[type="button"]').trigger('click')
    await flushPromises()

    expect(updateMe).not.toHaveBeenCalled()
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('Test User')
    expect(wrapper.text()).not.toContain('Changed Name')
  })

  it('SAVE CHANGES成功後にauthStore.userと表示が最新化され通常表示へ戻る', async () => {
    const authStore = useAuthStore()
    authStore.user = sampleUser

    const updatedUser: UserMe = { ...sampleUser, name: 'Updated User' }
    vi.mocked(updateMe).mockResolvedValue(updatedUser)

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('EDIT PROFILE'))
      ?.trigger('click')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(updateMe).toHaveBeenCalledTimes(1)
    expect(authStore.user?.name).toBe('Updated User')
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('Updated User')
  })

  it('SAVE CHANGES失敗時は編集モードを維持し入力内容とエラーを表示する', async () => {
    const authStore = useAuthStore()
    authStore.user = sampleUser
    vi.mocked(updateMe).mockRejectedValue(new Error('network error'))

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('EDIT PROFILE'))
      ?.trigger('click')

    await wrapper.find('#profile-edit-name').setValue('Changed Name')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(true)
    expect((wrapper.find('#profile-edit-name').element as HTMLInputElement).value).toBe(
      'Changed Name',
    )
    expect(wrapper.text()).toContain('プロフィールの更新に失敗しました')
    expect(authStore.user?.name).toBe('Test User')
  })

  it('[バグ修正] MASTER/MR1600でCharacter/Rankを変更せずMRだけ1650へ変更するとSAVE後の通常表示もMR1650になる', async () => {
    const baseUser: UserMe = {
      ...sampleUser,
      characters: [{ characterId: 1, rank: 'MASTER', mr: 1600 }],
    }
    const authStore = useAuthStore()
    authStore.user = baseUser

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('MR 1600')

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('EDIT PROFILE'))
      ?.trigger('click')

    vi.mocked(updateMe).mockResolvedValue({
      ...baseUser,
      characters: [{ characterId: 1, rank: 'MASTER', mr: 1650 }],
    })

    await wrapper.find('.profile-edit-form__main-character input').setValue('1650')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(updateMe).toHaveBeenCalledTimes(1)
    const payload = vi.mocked(updateMe).mock.calls[0]?.[0]
    expect(payload?.characters?.[0]).toEqual({ characterId: 1, rank: 'MASTER', mr: 1650 })

    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('MR 1650')
    expect(wrapper.text()).not.toContain('MR 1600')
  })

  it('Character一覧取得失敗時でも編集モードに入ってもクラッシュしない', async () => {
    vi.mocked(fetchCharacters).mockRejectedValue(new Error('network error'))

    const authStore = useAuthStore()
    authStore.user = sampleUser

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('EDIT PROFILE'))
      ?.trigger('click')

    expect(wrapper.find('form').exists()).toBe(true)
    expect(wrapper.text()).toContain('キャラクター一覧を取得できなかったため')
  })
})
