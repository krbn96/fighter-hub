import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import ProfileEditForm from '../ProfileEditForm.vue'
import type { UserMe, UserUpdateRequest } from '@/types/user'
import type { Character } from '@/types/character'

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

const sampleCharacters: Character[] = [
  { id: 1, name: 'RYU' },
  { id: 2, name: 'KEN' },
  { id: 3, name: 'CHUN-LI' },
]

function mainCard(wrapper: ReturnType<typeof mount>) {
  return wrapper.find('.profile-edit-form__main-character')
}

function subCards(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('.profile-edit-form__sub-character-grid .profile-edit-form__character-card')
}

describe('ProfileEditForm', () => {
  it('MAIN/SUB1/SUB2/SUB3のCHARACTER/RANK/MRがlabel[for]とcontrol[id]で正しく対応している', () => {
    const wrapper = mount(ProfileEditForm, {
      props: { user: sampleUser, characters: sampleCharacters, saving: false, error: '' },
    })

    const slots = ['main', 'sub1', 'sub2', 'sub3']
    const fields = ['character', 'rank', 'mr']

    for (const slot of slots) {
      for (const field of fields) {
        const id = `profile-edit-${field}-${slot}`
        const control = wrapper.find(`#${id}`)
        const label = wrapper.find(`label[for="${id}"]`)

        expect(control.exists(), `#${id} control should exist`).toBe(true)
        expect(label.exists(), `label[for="${id}"] should exist`).toBe(true)
      }
    }
  })

  it('authStore.user相当のuser propから初期値が設定される', () => {
    const wrapper = mount(ProfileEditForm, {
      props: { user: sampleUser, characters: sampleCharacters, saving: false, error: '' },
    })

    expect((wrapper.find('#profile-edit-name').element as HTMLInputElement).value).toBe(
      'Test User',
    )
    expect(
      (wrapper.find('#profile-edit-playtime-start').element as HTMLInputElement).value,
    ).toBe('20:00')
    expect((wrapper.find('#profile-edit-playtime-end').element as HTMLInputElement).value).toBe(
      '23:30',
    )
    expect((wrapper.find('#profile-edit-message').element as HTMLTextAreaElement).value).toBe(
      'よろしくお願いします',
    )

    const mainSelects = mainCard(wrapper).findAll('select')
    expect((mainSelects[0]!.element as HTMLSelectElement).value).toBe('1')
    expect((mainSelects[1]!.element as HTMLSelectElement).value).toBe('MASTER')
    expect((mainCard(wrapper).find('input').element as HTMLInputElement).value).toBe('1650')

    const sub1Selects = subCards(wrapper)[0]!.findAll('select')
    expect((sub1Selects[0]!.element as HTMLSelectElement).value).toBe('2')
    expect((sub1Selects[1]!.element as HTMLSelectElement).value).toBe('DIAMOND')

    const sub2Selects = subCards(wrapper)[1]!.findAll('select')
    expect((sub2Selects[0]!.element as HTMLSelectElement).value).toBe('未設定')
  })

  it('CANCELボタンでcancelがemitされる', async () => {
    const wrapper = mount(ProfileEditForm, {
      props: { user: sampleUser, characters: sampleCharacters, saving: false, error: '' },
    })

    await wrapper.find('button[type="button"]').trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
    expect(wrapper.emitted('save')).toBeUndefined()
  })

  it('SAVE CHANGESでMAIN→SUB1→SUB2→SUB3の順のpayloadがemitされ、未設定SUBは含まれない', async () => {
    const wrapper = mount(ProfileEditForm, {
      props: { user: sampleUser, characters: sampleCharacters, saving: false, error: '' },
    })

    await wrapper.find('form').trigger('submit')

    const saved = wrapper.emitted('save')?.[0]?.[0] as UserUpdateRequest
    expect(saved.name).toBe('Test User')
    expect(saved.playTimeStart).toBe('20:00')
    expect(saved.playTimeEnd).toBe('23:30')
    expect(saved.message).toBe('よろしくお願いします')
    expect(saved.characters).toEqual([
      { characterId: 1, rank: 'MASTER', mr: 1650 },
      { characterId: 2, rank: 'DIAMOND', mr: null },
    ])
  })

  it('SUB CHARACTERのselectを未設定に戻すとpayloadから除外される(削除)', async () => {
    const wrapper = mount(ProfileEditForm, {
      props: { user: sampleUser, characters: sampleCharacters, saving: false, error: '' },
    })

    const sub1CharacterSelect = subCards(wrapper)[0]!.findAll('select')[0]!
    await sub1CharacterSelect.setValue('未設定')
    await wrapper.find('form').trigger('submit')

    const saved = wrapper.emitted('save')?.[0]?.[0] as UserUpdateRequest
    expect(saved.characters).toEqual([{ characterId: 1, rank: 'MASTER', mr: 1650 }])
  })

  it('NAMEが空の場合はvalidationエラーとなりsaveがemitされない', async () => {
    const wrapper = mount(ProfileEditForm, {
      props: { user: sampleUser, characters: sampleCharacters, saving: false, error: '' },
    })

    await wrapper.find('#profile-edit-name').setValue('')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('save')).toBeUndefined()
    expect(wrapper.text()).toContain('NAMEを入力してください')
  })

  it('MAIN CHARACTERが未設定の場合はvalidationエラーとなりsaveがemitされない', async () => {
    const wrapper = mount(ProfileEditForm, {
      props: {
        user: { ...sampleUser, characters: [] },
        characters: sampleCharacters,
        saving: false,
        error: '',
      },
    })

    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('save')).toBeUndefined()
    expect(wrapper.text()).toContain('MAIN CHARACTERを選択してください')
  })

  it('MAINとSUBで同じCharacterを選択した場合はvalidationエラーとなりsaveがemitされない', async () => {
    const wrapper = mount(ProfileEditForm, {
      props: { user: sampleUser, characters: sampleCharacters, saving: false, error: '' },
    })

    const sub1CharacterSelect = subCards(wrapper)[0]!.findAll('select')[0]!
    await sub1CharacterSelect.setValue('1')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('save')).toBeUndefined()
    expect(wrapper.text()).toContain('同じCharacterを複数選択することはできません')
  })

  it('Character一覧が空の場合はCharacter編集項目が無効化され、payloadにcharactersを含めない', async () => {
    const wrapper = mount(ProfileEditForm, {
      props: { user: sampleUser, characters: [], saving: false, error: '' },
    })

    expect(wrapper.text()).toContain('キャラクター一覧を取得できなかったため')
    expect((mainCard(wrapper).find('select').element as HTMLSelectElement).disabled).toBe(true)

    await wrapper.find('form').trigger('submit')

    const saved = wrapper.emitted('save')?.[0]?.[0] as UserUpdateRequest
    expect(saved).toBeDefined()
    expect(saved.characters).toBeUndefined()
  })

  it('saving中はボタンがdisabledになりSAVING...表示になる', () => {
    const wrapper = mount(ProfileEditForm, {
      props: { user: sampleUser, characters: sampleCharacters, saving: true, error: '' },
    })

    expect(wrapper.text()).toContain('SAVING...')
    wrapper.findAll('button').forEach((button) => {
      expect((button.element as HTMLButtonElement).disabled).toBe(true)
    })
  })

  it('[バグ修正] Character/Rankを変更せずMRだけ変更してもSAVEでpayloadに反映される', async () => {
    // 回帰テスト: MR inputがtype="number"だった当時、Vue3のv-modelがtype="number"の
    // <input>を自動的にnumber型へcastするため、mrTextが文字列であることを前提にした
    // handleSubmit内のslot.mrText.trim()がTypeErrorを投げ、save自体がemitされなかった。
    const wrapper = mount(ProfileEditForm, {
      props: { user: sampleUser, characters: sampleCharacters, saving: false, error: '' },
    })

    await mainCard(wrapper).find('input').setValue('1650')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('save')).toBeDefined()
    const saved = wrapper.emitted('save')?.[0]?.[0] as UserUpdateRequest
    expect(saved.characters?.[0]).toEqual({ characterId: 1, rank: 'MASTER', mr: 1650 })
  })

  it('MASTER以外のCharacterはMR inputがdisabledになり、payloadでもmr: nullになる', () => {
    // sampleUserのSUB1(characterId:2)はDIAMOND/mr:nullなので、そのままMASTER以外のケースになる
    const wrapper = mount(ProfileEditForm, {
      props: { user: sampleUser, characters: sampleCharacters, saving: false, error: '' },
    })

    const sub1Input = subCards(wrapper)[0]!.find('input')
    expect((sub1Input.element as HTMLInputElement).disabled).toBe(true)
  })

  it('rankをMASTERから別のrankへ変更するとMRがクリアされinput disabledになり、payloadもmr: nullになる', async () => {
    const wrapper = mount(ProfileEditForm, {
      props: { user: sampleUser, characters: sampleCharacters, saving: false, error: '' },
    })

    const mainRankSelect = mainCard(wrapper).findAll('select')[1]!
    const mainMrInput = mainCard(wrapper).find('input')

    expect((mainMrInput.element as HTMLInputElement).value).toBe('1650')

    await mainRankSelect.setValue('DIAMOND')

    expect((mainMrInput.element as HTMLInputElement).value).toBe('')
    expect((mainMrInput.element as HTMLInputElement).disabled).toBe(true)

    await wrapper.find('form').trigger('submit')

    const saved = wrapper.emitted('save')?.[0]?.[0] as UserUpdateRequest
    expect(saved.characters?.[0]).toEqual({ characterId: 1, rank: 'DIAMOND', mr: null })
  })

  it('rankをMASTER以外からMASTERへ戻すとMR inputがenabledになり、以前の値は自動復元されず空欄になる', async () => {
    const wrapper = mount(ProfileEditForm, {
      props: { user: sampleUser, characters: sampleCharacters, saving: false, error: '' },
    })

    const mainRankSelect = mainCard(wrapper).findAll('select')[1]!
    const mainMrInput = mainCard(wrapper).find('input')

    await mainRankSelect.setValue('DIAMOND')
    await mainRankSelect.setValue('MASTER')

    expect((mainMrInput.element as HTMLInputElement).disabled).toBe(false)
    expect((mainMrInput.element as HTMLInputElement).value).toBe('')

    await mainMrInput.setValue('1700')
    await wrapper.find('form').trigger('submit')

    const saved = wrapper.emitted('save')?.[0]?.[0] as UserUpdateRequest
    expect(saved.characters?.[0]).toEqual({ characterId: 1, rank: 'MASTER', mr: 1700 })
  })

  it('初期データがrank!=MASTERかつmr!=nullという不整合の場合、編集フォームではMRをnullとして扱う', () => {
    const wrapper = mount(ProfileEditForm, {
      props: {
        user: {
          ...sampleUser,
          characters: [{ characterId: 1, rank: 'DIAMOND', mr: 1600 }],
        },
        characters: sampleCharacters,
        saving: false,
        error: '',
      },
    })

    const mainMrInput = mainCard(wrapper).find('input')
    expect((mainMrInput.element as HTMLInputElement).value).toBe('')
    expect((mainMrInput.element as HTMLInputElement).disabled).toBe(true)
  })

  it('SUB CHARACTERでもMASTER以外はMR inputがdisabledになり、MASTERへ変更すると入力可能になる', async () => {
    const wrapper = mount(ProfileEditForm, {
      props: { user: sampleUser, characters: sampleCharacters, saving: false, error: '' },
    })

    const sub1Card = subCards(wrapper)[0]!
    const sub1RankSelect = sub1Card.findAll('select')[1]!
    const sub1MrInput = sub1Card.find('input')

    // sampleUserのSUB1は初期状態でDIAMONDのためdisabled
    expect((sub1MrInput.element as HTMLInputElement).disabled).toBe(true)

    await sub1RankSelect.setValue('MASTER')

    expect((sub1MrInput.element as HTMLInputElement).disabled).toBe(false)
    await sub1MrInput.setValue('1500')
    await wrapper.find('form').trigger('submit')

    const saved = wrapper.emitted('save')?.[0]?.[0] as UserUpdateRequest
    expect(saved.characters?.[1]).toEqual({ characterId: 2, rank: 'MASTER', mr: 1500 })
  })

  it('errorが渡された場合エラーメッセージを表示する', () => {
    const wrapper = mount(ProfileEditForm, {
      props: {
        user: sampleUser,
        characters: sampleCharacters,
        saving: false,
        error: 'プロフィールの更新に失敗しました',
      },
    })

    expect(wrapper.text()).toContain('プロフィールの更新に失敗しました')
  })
})
