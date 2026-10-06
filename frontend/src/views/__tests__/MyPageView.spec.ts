import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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

vi.mock('@/api/discord', () => ({
  startDiscordAuthorization: vi.fn<() => Promise<{ authorizationUrl: string }>>(),
  unlinkDiscordAccount: vi.fn<() => Promise<void>>(),
}))

const replace = vi.fn<(location: { query: Record<string, string> }) => void>()
let currentQuery: Record<string, string> = {}

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ query: currentQuery }),
    useRouter: () => ({ replace }),
  }
})

import { fetchCharacters } from '@/api/characters'
import { updateMe } from '@/api/users'
import { startDiscordAuthorization, unlinkDiscordAccount } from '@/api/discord'

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
    vi.mocked(startDiscordAuthorization).mockReset()
    vi.mocked(unlinkDiscordAccount).mockReset()
    replace.mockReset()
    currentQuery = {}
  })

  // BaseModalは<Teleport to="body">で実DOMのdocument.bodyへ描画されるため、
  // VTUの通常のマウント先(detachedなコンテナ)とは異なりテスト間で残り続けてしまう。
  // 次テストのmodalDialog()が前のテストの残骸を拾わないよう、毎テスト後に掃除する。
  afterEach(() => {
    document.body.innerHTML = ''
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

  // ==== Discord連携UI(PLAYER PROFILE内に統合、独立カードは無し) ====

  function discordActionButton(wrapper: ReturnType<typeof mount>) {
    return wrapper
      .findAll('button')
      .find((button) => button.text() === 'Discordと連携' || button.text() === '連携解除')
  }

  function modalDialog(): HTMLElement | null {
    return document.querySelector('[role="dialog"]')
  }

  it('discordUsername=nullの場合はPLAYER PROFILE内にDiscordと連携ボタンが表示される', async () => {
    const authStore = useAuthStore()
    authStore.user = { ...sampleUser, discordUsername: null }

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Discordと連携')
    expect(wrapper.text()).not.toContain('連携解除')
  })

  it('未連携の場合、Discordアイコンはmuted状態(connectedクラスなし)になる', async () => {
    const authStore = useAuthStore()
    authStore.user = { ...sampleUser, discordUsername: null }

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.find('.discord-icon--connected').exists()).toBe(false)
  })

  it('discordUsernameありの場合は連携済みUI(ユーザー名・連携解除ボタン)が表示され、再連携ボタンは存在しない', async () => {
    const authStore = useAuthStore()
    authStore.user = { ...sampleUser, discordUsername: 'discorduser' }

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('discorduser')
    expect(wrapper.text()).toContain('連携解除')
    expect(wrapper.text()).not.toContain('再連携')
    expect(wrapper.text()).not.toContain('Discordと連携')
  })

  it('Discord情報はMY PAGE内で重複表示されない(discordUsernameの出現は1回のみ)', async () => {
    const authStore = useAuthStore()
    authStore.user = { ...sampleUser, discordUsername: 'discorduser' }

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    const occurrences = wrapper.text().split('discorduser').length - 1
    expect(occurrences).toBe(1)
    // 独立したDiscordカード自体(旧DiscordLinkSection)は廃止済み。
    expect(wrapper.find('.discord-link-section').exists()).toBe(false)
  })

  // ==== Discord連携確認モーダル ====

  it('Discordと連携ボタン押下で確認モーダルが表示され、authorize APIはまだ呼ばれない', async () => {
    const authStore = useAuthStore()
    authStore.user = { ...sampleUser, discordUsername: null }

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(modalDialog()).toBeNull()

    await discordActionButton(wrapper)?.trigger('click')
    await flushPromises()

    expect(modalDialog()).not.toBeNull()
    expect(modalDialog()?.textContent).toContain('Discordアカウントを連携')
    expect(modalDialog()?.textContent).toContain(
      'Discordアカウントを連携すると、Discordユーザー名がプロフィールに公開されます。',
    )
    expect(modalDialog()?.textContent).toContain('別のFIGHTER HUBアカウントに連携済みの場合')
    expect(startDiscordAuthorization).not.toHaveBeenCalled()
  })

  it('モーダルのキャンセル押下でモーダルが閉じ、authorize APIは呼ばれない', async () => {
    const authStore = useAuthStore()
    authStore.user = { ...sampleUser, discordUsername: null }

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    await discordActionButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(modalDialog()).not.toBeNull()

    const cancelButton = Array.from(modalDialog()?.querySelectorAll('button') ?? []).find(
      (button) => button.textContent?.trim() === 'キャンセル',
    )
    cancelButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    expect(modalDialog()).toBeNull()
    expect(startDiscordAuthorization).not.toHaveBeenCalled()
  })

  it('モーダル内のDiscordと連携押下でauthorize APIが実行され、成功時にauthorizationUrlへ遷移する', async () => {
    // jsdomのwindow.location.assignは直接vi.spyOnできない(プロパティがconfigurable:falseのため)。
    // window.location自体を一時的に差し替えて検証し、テスト後に元へ戻す。
    const assignMock = vi.fn<(url: string) => void>()
    const originalLocation = window.location
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...originalLocation, assign: assignMock },
    })

    vi.mocked(startDiscordAuthorization).mockResolvedValue({
      authorizationUrl: 'https://discord.com/oauth2/authorize?client_id=test',
    })

    const authStore = useAuthStore()
    authStore.user = { ...sampleUser, discordUsername: null }

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    await discordActionButton(wrapper)?.trigger('click')
    await flushPromises()

    const confirmButton = Array.from(modalDialog()?.querySelectorAll('button') ?? []).find(
      (button) => button.textContent?.includes('Discordと連携'),
    )
    confirmButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    expect(startDiscordAuthorization).toHaveBeenCalledTimes(1)
    expect(assignMock).toHaveBeenCalledWith('https://discord.com/oauth2/authorize?client_id=test')

    Object.defineProperty(window, 'location', { configurable: true, value: originalLocation })
  })

  it('モーダル内のDiscordと連携押下でauthorize APIが失敗した場合、モーダルを閉じ適切なエラーを表示する', async () => {
    vi.mocked(startDiscordAuthorization).mockRejectedValue(new Error('network error'))

    const authStore = useAuthStore()
    authStore.user = { ...sampleUser, discordUsername: null }

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    await discordActionButton(wrapper)?.trigger('click')
    await flushPromises()

    const confirmButton = Array.from(modalDialog()?.querySelectorAll('button') ?? []).find(
      (button) => button.textContent?.includes('Discordと連携'),
    )
    confirmButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    expect(modalDialog()).toBeNull()
    expect(wrapper.text()).toContain('Discordとの連携を開始できませんでした')
  })

  it('Escapeキーでモーダルが閉じる', async () => {
    const authStore = useAuthStore()
    authStore.user = { ...sampleUser, discordUsername: null }

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    await discordActionButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(modalDialog()).not.toBeNull()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()

    expect(modalDialog()).toBeNull()
    expect(startDiscordAuthorization).not.toHaveBeenCalled()
  })

  it('backdropクリックでモーダルが閉じる', async () => {
    const authStore = useAuthStore()
    authStore.user = { ...sampleUser, discordUsername: null }

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    await discordActionButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(modalDialog()).not.toBeNull()

    document
      .querySelector('.base-modal__backdrop')
      ?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    expect(modalDialog()).toBeNull()
    expect(startDiscordAuthorization).not.toHaveBeenCalled()
  })

  // ==== Discord連携解除 ====

  it('連携解除ボタン押下→unlink成功時にauthStore.userが更新され未連携表示へ切り替わり成功メッセージが表示される', async () => {
    vi.mocked(unlinkDiscordAccount).mockResolvedValue(undefined)

    const authStore = useAuthStore()
    authStore.user = { ...sampleUser, discordUsername: 'discorduser' }

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    await discordActionButton(wrapper)?.trigger('click')
    await flushPromises()

    expect(unlinkDiscordAccount).toHaveBeenCalledTimes(1)
    expect(authStore.user?.discordUsername).toBeNull()
    expect(wrapper.text()).toContain('Discord連携を解除しました')
    expect(wrapper.text()).toContain('Discordと連携')
    expect(wrapper.text()).not.toContain('discorduser')

    // メッセージはPLAYER PROFILEカード外の上部ではなく、Discord行(操作ボタンの右側)に表示される。
    expect(wrapper.find('.player-profile__discord-message').text()).toBe('Discord連携を解除しました')
    expect(wrapper.find('.my-page-view__discord-message').exists()).toBe(false)
  })

  it('連携解除ボタン押下→unlink失敗時はエラーを表示し連携状態(ユーザー名)を維持する', async () => {
    vi.mocked(unlinkDiscordAccount).mockRejectedValue(new Error('network error'))

    const authStore = useAuthStore()
    authStore.user = { ...sampleUser, discordUsername: 'discorduser' }

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    await discordActionButton(wrapper)?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Discord連携の解除に失敗しました')
    expect(authStore.user?.discordUsername).toBe('discorduser')
    expect(wrapper.text()).toContain('discorduser')
    expect(wrapper.text()).toContain('連携解除')
  })

  // ==== OAuth callback結果表示 ====

  it('oauth=discord&result=successの場合、成功メッセージを表示しqueryをreplaceで除去する', async () => {
    currentQuery = { oauth: 'discord', result: 'success' }

    const authStore = useAuthStore()
    authStore.user = sampleUser

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Discordアカウントを連携しました。')
    expect(replace).toHaveBeenCalledWith({
      query: { oauth: undefined, result: undefined, reason: undefined },
    })

    // PLAYER PROFILEカード外の上部ではなく、Discord行(連携解除ボタンの右側)に表示される。
    expect(wrapper.find('.player-profile__discord-message').text()).toBe(
      'Discordアカウントを連携しました。',
    )
    expect(wrapper.find('.my-page-view__discord-message').exists()).toBe(false)
  })

  it.each([
    ['cancelled', 'Discord連携がキャンセルされました。'],
    ['invalid_state', 'Discord連携情報を確認できませんでした。もう一度お試しください。'],
    ['expired_state', 'Discord連携の有効期限が切れました。もう一度お試しください。'],
    ['provider_error', 'Discordとの通信に失敗しました。時間をおいてもう一度お試しください。'],
    ['server_error', 'Discord連携中にエラーが発生しました。もう一度お試しください。'],
  ])('oauth=discord&result=error&reason=%sの場合、対応する安全な固定メッセージを表示する', async (reason, expectedMessage) => {
    currentQuery = { oauth: 'discord', result: 'error', reason }

    const authStore = useAuthStore()
    authStore.user = sampleUser

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain(expectedMessage)
  })

  it('reasonが未知の文字列の場合でも安全なfallbackメッセージを表示し、生の文字列をDOMへ出さない', async () => {
    currentQuery = { oauth: 'discord', result: 'error', reason: '<script>alert(1)</script>' }

    const authStore = useAuthStore()
    authStore.user = sampleUser

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Discord連携中にエラーが発生しました。もう一度お試しください。')
    expect(wrapper.html()).not.toContain('<script>alert(1)</script>')
  })

  it('oauthクエリが無い場合はcallback結果メッセージを表示しない', async () => {
    currentQuery = {}

    const authStore = useAuthStore()
    authStore.user = sampleUser

    const wrapper = mount(MyPageView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('Discordアカウントを連携しました。')
    expect(replace).not.toHaveBeenCalled()
  })
})
