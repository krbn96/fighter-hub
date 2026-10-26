import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import TournamentForm from '../TournamentForm.vue'
import type { TournamentFormValues } from '@/types/tournament'

const initialValues: TournamentFormValues = {
  name: 'FIGHTER HUB CUP',
  teamSize: 3,
  maxPlayers: 64,
  startAt: '2026-12-20T13:00',
  recruitmentDeadline: '2026-12-15T23:59',
}

function mountForm(values: TournamentFormValues = initialValues) {
  return mount(TournamentForm, {
    props: {
      initialValues: values,
      submitting: false,
      submitLabel: 'CREATE TOURNAMENT',
      submittingLabel: 'CREATING...',
    },
  })
}

describe('TournamentForm', () => {
  it('initialValuesが各フィールドの初期値として表示される(datetime-localは秒無し)', () => {
    const wrapper = mountForm()

    expect((wrapper.find('#tournament-form-name').element as HTMLInputElement).value).toBe(
      'FIGHTER HUB CUP',
    )
    expect(
      (wrapper.find('#tournament-form-team-size').element as HTMLInputElement).value,
    ).toBe('3')
    expect(
      (wrapper.find('#tournament-form-max-players').element as HTMLInputElement).value,
    ).toBe('64')
    expect(
      (wrapper.find('#tournament-form-start-at').element as HTMLInputElement).value,
    ).toBe('2026-12-20T13:00')
    expect(
      (wrapper.find('#tournament-form-deadline').element as HTMLInputElement).value,
    ).toBe('2026-12-15T23:59')
  })

  it('正常な入力で送信すると、秒を補ったISO形式でsubmitイベントが発火する', async () => {
    const wrapper = mountForm()

    await wrapper.find('form').trigger('submit')

    const emitted = wrapper.emitted('submit')
    expect(emitted).toHaveLength(1)
    expect(emitted?.[0]?.[0]).toEqual({
      name: 'FIGHTER HUB CUP',
      teamSize: 3,
      maxPlayers: 64,
      startAt: '2026-12-20T13:00:00',
      recruitmentDeadline: '2026-12-15T23:59:00',
    })
  })

  it('大会名が空の場合はエラーを表示しsubmitイベントを発火しない', async () => {
    const wrapper = mountForm()

    await wrapper.find('#tournament-form-name').setValue('')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.text()).toContain('大会名を入力してください')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('teamSizeが0の場合はエラーを表示しsubmitイベントを発火しない', async () => {
    const wrapper = mountForm()

    await wrapper.find('#tournament-form-team-size').setValue('0')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.text()).toContain('チームサイズは1以上の整数で入力してください')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('maxPlayersが未入力の場合はエラーを表示しsubmitイベントを発火しない', async () => {
    const wrapper = mountForm()

    await wrapper.find('#tournament-form-max-players').setValue('')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.text()).toContain('最大参加人数は1以上の整数で入力してください')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('startAtが未入力の場合はエラーを表示しsubmitイベントを発火しない', async () => {
    const wrapper = mountForm()

    await wrapper.find('#tournament-form-start-at').setValue('')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.text()).toContain('開始日時を入力してください')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('recruitmentDeadlineがstartAt以降(同時刻含む)の場合はエラーを表示しsubmitイベントを発火しない', async () => {
    const wrapper = mountForm({
      ...initialValues,
      recruitmentDeadline: '2026-12-20T13:00',
    })

    await wrapper.find('form').trigger('submit')

    expect(wrapper.text()).toContain('募集締切日時は開始日時より前に設定してください')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('cancelボタン押下でcancelイベントが発火する', async () => {
    const wrapper = mountForm()

    const cancelButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'CANCEL')
    await cancelButton?.trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('サーバーエラー(errorプロパティ)が表示される', () => {
    const wrapper = mount(TournamentForm, {
      props: {
        initialValues,
        submitting: false,
        submitLabel: 'CREATE TOURNAMENT',
        submittingLabel: 'CREATING...',
        error: '入力内容を確認してください',
      },
    })

    expect(wrapper.text()).toContain('入力内容を確認してください')
  })
})
