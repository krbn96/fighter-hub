import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import PlayerProfile from '../PlayerProfile.vue'
import type { UserMe } from '@/types/user'

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
})
