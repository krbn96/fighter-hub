import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import TeamForm from '../TeamForm.vue'
import type { Character } from '@/types/character'
import type { TeamFormValues } from '@/types/team'

const sampleCharacters: Character[] = [
  { id: 1, name: 'RYU' },
  { id: 2, name: 'KEN' },
]

const initialValues: TeamFormValues = {
  name: 'Team Ryu',
  rankRequirement: 'MASTER',
  characterRequirements: [1],
  recruitmentMessage: '誰でも歓迎です',
}

describe('TeamForm', () => {
  it('initialValuesが各フィールドの初期値として表示される', () => {
    const wrapper = mount(TeamForm, {
      props: {
        initialValues,
        characters: sampleCharacters,
        submitting: false,
        submitLabel: 'CREATE TEAM',
        submittingLabel: 'CREATING...',
      },
    })

    expect((wrapper.find('#team-form-name').element as HTMLInputElement).value).toBe('Team Ryu')
    expect((wrapper.find('#team-form-rank').element as HTMLSelectElement).value).toBe('MASTER')
    expect(
      (wrapper.find('#team-form-message').element as HTMLTextAreaElement).value,
    ).toBe('誰でも歓迎です')

    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    expect((checkboxes[0]!.element as HTMLInputElement).checked).toBe(true)
    expect((checkboxes[1]!.element as HTMLInputElement).checked).toBe(false)
  })

  it('initialValuesを省略した場合は空のCreate用初期値になる', () => {
    const wrapper = mount(TeamForm, {
      props: {
        characters: sampleCharacters,
        submitting: false,
        submitLabel: 'CREATE TEAM',
        submittingLabel: 'CREATING...',
      },
    })

    expect((wrapper.find('#team-form-name').element as HTMLInputElement).value).toBe('')
    expect((wrapper.find('#team-form-rank').element as HTMLSelectElement).value).toBe('')
    wrapper.findAll('input[type="checkbox"]').forEach((checkbox) => {
      expect((checkbox.element as HTMLInputElement).checked).toBe(false)
    })
  })

  it('TEAM NAMEはrequired、maxlength=255を持つ', () => {
    const wrapper = mount(TeamForm, {
      props: {
        characters: sampleCharacters,
        submitting: false,
        submitLabel: 'CREATE TEAM',
        submittingLabel: 'CREATING...',
      },
    })

    const nameInput = wrapper.find('#team-form-name')
    expect(nameInput.attributes('required')).toBeDefined()
    expect(nameInput.attributes('maxlength')).toBe('255')
  })

  it('submitでフォーム内容がTeamFormValuesとしてemitされる', async () => {
    const wrapper = mount(TeamForm, {
      props: {
        characters: sampleCharacters,
        submitting: false,
        submitLabel: 'CREATE TEAM',
        submittingLabel: 'CREATING...',
      },
    })

    await wrapper.find('#team-form-name').setValue('New Team')
    await wrapper.find('#team-form-rank').setValue('DIAMOND')
    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    await checkboxes[0]!.setValue(true)
    await checkboxes[1]!.setValue(true)
    await wrapper.find('#team-form-message').setValue('よろしく')
    await wrapper.find('form').trigger('submit')

    const emitted = wrapper.emitted('submit')?.[0]?.[0] as TeamFormValues
    expect(emitted).toEqual({
      name: 'New Team',
      rankRequirement: 'DIAMOND',
      characterRequirements: [1, 2],
      recruitmentMessage: 'よろしく',
    })
  })

  it('Characterを解除すると、残り0件ならcharacterRequirementsはnullになる', async () => {
    const wrapper = mount(TeamForm, {
      props: {
        initialValues,
        characters: sampleCharacters,
        submitting: false,
        submitLabel: 'CREATE TEAM',
        submittingLabel: 'CREATING...',
      },
    })

    await wrapper.findAll('input[type="checkbox"]')[0]!.setValue(false)
    await wrapper.find('form').trigger('submit')

    const emitted = wrapper.emitted('submit')?.[0]?.[0] as TeamFormValues
    expect(emitted.characterRequirements).toBeNull()
  })

  it('CANCELボタンでcancelがemitされ、submitはemitされない', async () => {
    const wrapper = mount(TeamForm, {
      props: {
        characters: sampleCharacters,
        submitting: false,
        submitLabel: 'CREATE TEAM',
        submittingLabel: 'CREATING...',
      },
    })

    await wrapper.find('button[type="button"]').trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('submitting中はsubmitボタンがdisabledになりsubmittingLabelを表示する', () => {
    const wrapper = mount(TeamForm, {
      props: {
        characters: sampleCharacters,
        submitting: true,
        submitLabel: 'CREATE TEAM',
        submittingLabel: 'CREATING...',
      },
    })

    expect(wrapper.text()).toContain('CREATING...')
    wrapper.findAll('button').forEach((button) => {
      expect((button.element as HTMLButtonElement).disabled).toBe(true)
    })
  })

  it('Character一覧が0件の場合はcheckbox領域の代わりに補助表示になり、フォーム送信は可能', async () => {
    const wrapper = mount(TeamForm, {
      props: {
        characters: [],
        submitting: false,
        submitLabel: 'CREATE TEAM',
        submittingLabel: 'CREATING...',
      },
    })

    expect(wrapper.text()).toContain('キャラクター条件を指定できません')
    expect(wrapper.findAll('input[type="checkbox"]')).toHaveLength(0)

    await wrapper.find('#team-form-name').setValue('No Character Team')
    await wrapper.find('form').trigger('submit')

    const emitted = wrapper.emitted('submit')?.[0]?.[0] as TeamFormValues
    expect(emitted.characterRequirements).toBeNull()
  })

  it('errorが渡された場合role=alertでエラーメッセージを表示する', () => {
    const wrapper = mount(TeamForm, {
      props: {
        characters: sampleCharacters,
        submitting: false,
        submitLabel: 'CREATE TEAM',
        submittingLabel: 'CREATING...',
        error: 'この大会では既にチームに所属しています',
      },
    })

    const alert = wrapper.find('[role="alert"]')
    expect(alert.text()).toBe('この大会では既にチームに所属しています')
  })
})
