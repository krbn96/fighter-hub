import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

import PlayerProfile from '../PlayerProfile.vue'
import type { UserMe } from '@/types/user'

// jsdomのnavigator.clipboardはデフォルトで存在しない(または直接vi.spyOnできない)ため、
// window.locationと同様にnavigator.clipboard自体を一時的に差し替えてテストする。
let writeTextMock: ReturnType<typeof vi.fn<(text: string) => Promise<void>>>
let originalClipboard: Clipboard | undefined

function stubClipboard() {
  writeTextMock = vi.fn<(text: string) => Promise<void>>().mockResolvedValue(undefined)
  originalClipboard = navigator.clipboard
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: { writeText: writeTextMock },
  })
}

function restoreClipboard() {
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: originalClipboard,
  })
}

const sampleUser: UserMe = {
  id: 1,
  name: 'Test User',
  characters: [
    { characterId: 1, rank: 'MASTER', mr: 1650 },
    { characterId: 2, rank: 'DIAMOND', mr: null },
  ],
  playTimeStart: '20:00:00',
  playTimeEnd: '23:30:00',
  message: 'よろしくお願いします',
  email: 'test@example.com',
  xId: null,
  discordUsername: null,
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

const characterNames = new Map<number, string>([
  [1, 'RYU'],
  [2, 'CHUN-LI'],
])

describe('PlayerProfile', () => {
  beforeEach(() => {
    stubClipboard()
  })

  afterEach(() => {
    restoreClipboard()
    vi.useRealTimers()
  })

  it('user name・CHARACTERS見出し・PLAY TIME/MESSAGEラベルを表示する', () => {
    const wrapper = mount(PlayerProfile, {
      props: { user: sampleUser, characterNames },
    })

    const text = wrapper.text()
    expect(text).toContain('Test User')
    expect(text).toContain('CHARACTERS')
    expect(text).not.toContain('FIGHTERS')
    expect(text).toContain('PLAY TIME')
    expect(text).toContain('MESSAGE')
  })

  it('先頭のcharacterがMAIN CHARACTERとして表示される', () => {
    const wrapper = mount(PlayerProfile, {
      props: { user: sampleUser, characterNames },
    })

    const text = wrapper.text()
    expect(text).toContain('MAIN CHARACTER')

    const mainCard = wrapper.find('.player-profile__main-character-card')
    expect(mainCard.text()).toContain('RYU')
    expect(mainCard.text()).toContain('MASTER')
    expect(mainCard.text()).toContain('MR 1650')
    expect(mainCard.find('.player-profile__main-character-visual').exists()).toBe(true)
  })

  it('2件目以降のcharacterがSUB CHARACTERSとして表示される', () => {
    const wrapper = mount(PlayerProfile, {
      props: { user: sampleUser, characterNames },
    })

    expect(wrapper.text()).toContain('SUB CHARACTERS')

    const subCards = wrapper.findAll('.player-profile__sub-character-card')
    expect(subCards).toHaveLength(1)
    expect(subCards[0]?.text()).toContain('CHUN-LI')
    expect(subCards[0]?.text()).toContain('DIAMOND')
    expect(subCards[0]?.find('.player-profile__sub-character-visual').exists()).toBe(true)
  })

  it('mrがnullのcharacterはMR行を表示しない', () => {
    const wrapper = mount(PlayerProfile, {
      props: { user: sampleUser, characterNames },
    })

    const subCards = wrapper.findAll('.player-profile__sub-character-card')
    expect(subCards[0]?.text()).not.toContain('MR')
  })

  it('characterが1件のみの場合はSUB CHARACTERS見出しを表示しない', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, characters: [{ characterId: 1, rank: 'MASTER', mr: 1650 }] },
        characterNames,
      },
    })

    expect(wrapper.text()).toContain('MAIN CHARACTER')
    expect(wrapper.text()).not.toContain('SUB CHARACTERS')
    expect(wrapper.findAll('.player-profile__sub-character-card')).toHaveLength(0)
  })

  it('SUB CHARACTERSが2件の場合、fake cardを追加せず2件のみ表示する', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: {
          ...sampleUser,
          characters: [
            { characterId: 1, rank: 'MASTER', mr: 1650 },
            { characterId: 2, rank: 'DIAMOND', mr: null },
            { characterId: 3, rank: 'GOLD', mr: 1400 },
          ],
        },
        characterNames: new Map([
          [1, 'RYU'],
          [2, 'CHUN-LI'],
          [3, 'KEN'],
        ]),
      },
    })

    const subCards = wrapper.findAll('.player-profile__sub-character-card')
    expect(subCards).toHaveLength(2)
  })

  it('characterが0件の場合はEmpty表示になる', () => {
    const wrapper = mount(PlayerProfile, {
      props: { user: { ...sampleUser, characters: [] }, characterNames },
    })

    expect(wrapper.text()).toContain('使用キャラクターが登録されていません')
    expect(wrapper.text()).not.toContain('MAIN CHARACTER')
    expect(wrapper.text()).not.toContain('SUB CHARACTERS')
  })

  it('rank!==MASTERでmrが設定された不整合データの場合はMRを表示しない', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, characters: [{ characterId: 1, rank: 'DIAMOND', mr: 1600 }] },
        characterNames,
      },
    })

    expect(wrapper.text()).not.toContain('MR 1600')
    expect(wrapper.text()).not.toContain('MR')
  })

  it('characterNamesに存在しないcharacterIdはfallback表示になる', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, characters: [{ characterId: 999, rank: 'GOLD', mr: 1200 }] },
        characterNames,
      },
    })

    expect(wrapper.text()).toContain('不明なキャラクター')
  })

  it('messageがnullの場合はfallback文言を表示する', () => {
    const wrapper = mount(PlayerProfile, {
      props: { user: { ...sampleUser, message: null }, characterNames },
    })

    expect(wrapper.text()).toContain('メッセージはまだありません')
  })

  it('playTimeStart/Endが両方揃っている場合は範囲表示になる', () => {
    const wrapper = mount(PlayerProfile, {
      props: { user: sampleUser, characterNames },
    })

    expect(wrapper.text()).toContain('20:00 - 23:30')
  })

  it('playTimeが両方nullの場合も表示が崩れない', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, playTimeStart: null, playTimeEnd: null },
        characterNames,
      },
    })

    expect(wrapper.text()).toContain('プレイ時間は未設定です')
  })

  // ==== Discord表示(公開プロフィール相当、showDiscordActions省略=false) ====

  it('[公開] discordUsernameがある場合はicon+usernameのみ表示し、操作ボタンは表示しない', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'discorduser' },
        characterNames,
      },
    })

    expect(wrapper.find('.player-profile__discord').exists()).toBe(true)
    expect(wrapper.text()).toContain('discorduser')
    expect(wrapper.text()).not.toContain('連携解除')
    expect(wrapper.text()).not.toContain('Discordと連携')
  })

  it('[公開] discordUsernameがnullの場合はDiscord欄自体を表示しない', () => {
    const wrapper = mount(PlayerProfile, {
      props: { user: { ...sampleUser, discordUsername: null }, characterNames },
    })

    expect(wrapper.find('.player-profile__discord').exists()).toBe(false)
  })

  // ==== Discord表示(MY PAGE相当、showDiscordActions=true) ====

  it('[MY PAGE] discordUsernameがある場合はicon+username+連携解除ボタンを表示し、再連携ボタンは無い', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'discorduser' },
        characterNames,
        showDiscordActions: true,
      },
    })

    expect(wrapper.text()).toContain('discorduser')
    expect(wrapper.text()).toContain('連携解除')
    expect(wrapper.text()).not.toContain('再連携')
    expect(wrapper.text()).not.toContain('Discordと連携')
  })

  it('[MY PAGE] discordUsernameがnullでもDiscord行を表示し、muted iconとDiscordと連携ボタンを表示する', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: null },
        characterNames,
        showDiscordActions: true,
      },
    })

    expect(wrapper.find('.player-profile__discord').exists()).toBe(true)
    expect(wrapper.text()).toContain('Discordと連携')
    expect(wrapper.find('.discord-icon--connected').exists()).toBe(false)
  })

  it('[MY PAGE] discordUsernameがある場合、Discordアイコンはconnected状態になる', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'discorduser' },
        characterNames,
        showDiscordActions: true,
      },
    })

    expect(wrapper.find('.discord-icon--connected').exists()).toBe(true)
  })

  it('[MY PAGE] 連携解除ボタン押下でdiscordUnlinkイベントがemitされる', async () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'discorduser' },
        characterNames,
        showDiscordActions: true,
      },
    })

    await wrapper.find('.player-profile__discord-button').trigger('click')

    expect(wrapper.emitted('discordUnlink')).toHaveLength(1)
  })

  it('[MY PAGE] Discordと連携ボタン押下でdiscordLinkイベントがemitされる', async () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: null },
        characterNames,
        showDiscordActions: true,
      },
    })

    await wrapper.find('.player-profile__discord-button').trigger('click')

    expect(wrapper.emitted('discordLink')).toHaveLength(1)
  })

  it('[MY PAGE] discordUnlinking中は連携解除ボタンがdisabledになり解除中...を表示する', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'discorduser' },
        characterNames,
        showDiscordActions: true,
        discordUnlinking: true,
      },
    })

    const button = wrapper.find('.player-profile__discord-button')
    expect(button.text()).toBe('解除中...')
    expect((button.element as HTMLButtonElement).disabled).toBe(true)
  })

  it('Discord情報はPLAYER PROFILE内の1か所にのみ表示される(重複表示しない)', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'discorduser' },
        characterNames,
        showDiscordActions: true,
      },
    })

    expect(wrapper.findAll('.player-profile__discord')).toHaveLength(1)
  })

  // ==== Discord操作メッセージ(操作ボタンの右側に表示) ====

  it('[MY PAGE] 連携済み+discordMessage(success)の場合、連携解除ボタンの右側にメッセージを表示する', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'discorduser' },
        characterNames,
        showDiscordActions: true,
        discordMessage: 'Discord連携を解除しました',
        discordMessageType: 'success',
      },
    })

    const discordRow = wrapper.find('.player-profile__discord')
    const message = discordRow.find('.player-profile__discord-message')
    expect(message.exists()).toBe(true)
    expect(message.text()).toBe('Discord連携を解除しました')
    expect(message.classes()).toContain('player-profile__discord-message--success')
    // 連携解除ボタンと同じDiscord行の中に表示されること(ボタンの右側)。
    expect(discordRow.find('.player-profile__discord-button').exists()).toBe(true)
  })

  it('[MY PAGE] 未連携+discordMessage(error)の場合、Discordと連携ボタンの右側にエラーメッセージを表示する', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: null },
        characterNames,
        showDiscordActions: true,
        discordMessage: 'Discordとの連携を開始できませんでした',
        discordMessageType: 'error',
      },
    })

    const discordRow = wrapper.find('.player-profile__discord')
    const message = discordRow.find('.player-profile__discord-message')
    expect(message.exists()).toBe(true)
    expect(message.text()).toBe('Discordとの連携を開始できませんでした')
    expect(message.classes()).toContain('player-profile__discord-message--error')
    expect(message.attributes('role')).toBe('alert')
  })

  it('discordMessageが空文字の場合はメッセージ要素を表示しない', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'discorduser' },
        characterNames,
        showDiscordActions: true,
        discordMessage: '',
      },
    })

    expect(wrapper.find('.player-profile__discord-message').exists()).toBe(false)
  })

  it('[公開] showDiscordActions=falseの場合、discordMessageを渡してもメッセージは表示されない', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'discorduser' },
        characterNames,
        discordMessage: 'Discord連携を解除しました',
        discordMessageType: 'success',
      },
    })

    expect(wrapper.find('.player-profile__discord-message').exists()).toBe(false)
  })

  // ==== Discord usernameクリックコピー ====

  it('[MY PAGE] Discord usernameクリックでnavigator.clipboard.writeText(username)が呼ばれる', async () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'kuro_bina' },
        characterNames,
        showDiscordActions: true,
      },
    })

    await wrapper.find('.player-profile__discord-username').trigger('click')

    expect(writeTextMock).toHaveBeenCalledWith('kuro_bina')
  })

  it('コピー成功時に「コピーしました」が表示される', async () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'kuro_bina' },
        characterNames,
        showDiscordActions: true,
      },
    })

    expect(wrapper.find('.player-profile__discord-copy-feedback').exists()).toBe(false)

    await wrapper.find('.player-profile__discord-username').trigger('click')
    await wrapper.vm.$nextTick()

    const feedback = wrapper.find('.player-profile__discord-copy-feedback')
    expect(feedback.exists()).toBe(true)
    expect(feedback.text()).toBe('コピーしました')
  })

  it('コピー成功から約2000ms後に「コピーしました」が自動的に消える', async () => {
    vi.useFakeTimers()
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'kuro_bina' },
        characterNames,
        showDiscordActions: true,
      },
    })

    await wrapper.find('.player-profile__discord-username').trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.player-profile__discord-copy-feedback').exists()).toBe(true)

    vi.advanceTimersByTime(1999)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.player-profile__discord-copy-feedback').exists()).toBe(true)

    vi.advanceTimersByTime(1)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.player-profile__discord-copy-feedback').exists()).toBe(false)
  })

  it('表示中に再クリックすると、最後に成功したコピーから約2秒間表示されるようtimerがリセットされる', async () => {
    vi.useFakeTimers()
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'kuro_bina' },
        characterNames,
        showDiscordActions: true,
      },
    })

    await wrapper.find('.player-profile__discord-username').trigger('click')
    await wrapper.vm.$nextTick()

    vi.advanceTimersByTime(1500)
    await wrapper.find('.player-profile__discord-username').trigger('click')
    await wrapper.vm.$nextTick()

    // 1回目のクリックから2000ms経過した時点でも、2回目のクリックから2000ms未満なら
    // まだ表示されているはず(timerがリセットされているため)。
    vi.advanceTimersByTime(1500)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.player-profile__discord-copy-feedback').exists()).toBe(true)

    vi.advanceTimersByTime(500)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.player-profile__discord-copy-feedback').exists()).toBe(false)
  })

  it('クリップボードへのコピーが失敗した場合は「コピーしました」を表示しない', async () => {
    writeTextMock.mockRejectedValueOnce(new Error('clipboard denied'))

    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'kuro_bina' },
        characterNames,
        showDiscordActions: true,
      },
    })

    await wrapper.find('.player-profile__discord-username').trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.player-profile__discord-copy-feedback').exists()).toBe(false)
  })

  it('[MY PAGE] usernameコピー機能を追加しても既存の連携解除ボタン・メッセージ表示は維持される', async () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'kuro_bina' },
        characterNames,
        showDiscordActions: true,
        discordMessage: 'Discord連携を解除しました',
        discordMessageType: 'success',
      },
    })

    await wrapper.find('.player-profile__discord-username').trigger('click')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.player-profile__discord-button').exists()).toBe(true)
    expect(wrapper.find('.player-profile__discord-button').text()).toBe('連携解除')
    expect(wrapper.find('.player-profile__discord-message').text()).toBe(
      'Discord連携を解除しました',
    )
  })

  it('[公開プロフィール] 連携済みの場合もusernameをクリックコピーできる', async () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: 'kuro_bina' },
        characterNames,
      },
    })

    await wrapper.find('.player-profile__discord-username').trigger('click')
    await wrapper.vm.$nextTick()

    expect(writeTextMock).toHaveBeenCalledWith('kuro_bina')
    expect(wrapper.find('.player-profile__discord-copy-feedback').text()).toBe('コピーしました')
    // 公開プロフィールには連携/解除ボタンは出ない。
    expect(wrapper.find('.player-profile__discord-button').exists()).toBe(false)
  })

  it('[公開プロフィール] 未連携の場合はDiscord表示自体が無い(コピー対象も無い)', () => {
    const wrapper = mount(PlayerProfile, {
      props: {
        user: { ...sampleUser, discordUsername: null },
        characterNames,
      },
    })

    expect(wrapper.find('.player-profile__discord').exists()).toBe(false)
    expect(wrapper.find('.player-profile__discord-username').exists()).toBe(false)
  })
})
