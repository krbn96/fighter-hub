import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'

import RegisterView from '../RegisterView.vue'
import type { Character } from '@/types/character'
import type { UserCreateRequest, UserCreateResponse } from '@/types/user'

vi.mock('@/api/user', () => ({
  createUser: vi.fn<(request: UserCreateRequest) => Promise<UserCreateResponse>>(),
}))

vi.mock('@/api/characters', () => ({
  fetchCharacters: vi.fn<() => Promise<Character[]>>(),
}))

const push = vi.fn<(path: string) => void>()

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({ push }),
  }
})

import { createUser } from '@/api/user'
import { fetchCharacters } from '@/api/characters'

const sampleCharacters: Character[] = [
  { id: 1, name: 'Ryu' },
  { id: 2, name: 'Ken' },
  { id: 3, name: 'Chun-Li' },
]

async function mountReady() {
  const wrapper = mount(RegisterView, {
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
  await flushPromises()
  return wrapper
}

function mainCard(wrapper: ReturnType<typeof mount>) {
  return wrapper.find('.register-view__main-character')
}

function subCards(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('.register-view__sub-character-grid .register-view__character-card')
}

async function fillAccountFields(
  wrapper: ReturnType<typeof mount>,
  overrides: Partial<Record<'name' | 'email' | 'password' | 'confirmPassword', string>> = {},
) {
  await wrapper.find('#register-name').setValue(overrides.name ?? 'Test User')
  await wrapper.find('#register-email').setValue(overrides.email ?? 'test@example.com')
  await wrapper.find('#register-password').setValue(overrides.password ?? 'password123')
  await wrapper
    .find('#register-confirm-password')
    .setValue(overrides.confirmPassword ?? 'password123')
}

async function selectMainCharacter(wrapper: ReturnType<typeof mount>, characterId = '1') {
  await wrapper.find('#register-character-main').setValue(characterId)
}

describe('RegisterView', () => {
  beforeEach(() => {
    vi.mocked(createUser).mockReset()
    vi.mocked(fetchCharacters).mockReset()
    vi.mocked(fetchCharacters).mockResolvedValue(sampleCharacters)
    push.mockReset()
  })

  it('Character一覧を取得し、正常に表示される', async () => {
    const wrapper = await mountReady()

    expect(fetchCharacters).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('CREATE ACCOUNT')
    expect(wrapper.text()).toContain('Ryu')
    expect(wrapper.text()).toContain('Ken')
  })

  it('MAIN CHARACTERを選択できる', async () => {
    const wrapper = await mountReady()

    await selectMainCharacter(wrapper, '2')

    const mainSelect = mainCard(wrapper).find('select')
    expect((mainSelect.element as HTMLSelectElement).value).toBe('2')
  })

  it('SUB CHARACTERを0〜3件選択できる', async () => {
    const wrapper = await mountReady()

    const subSelects = subCards(wrapper).map((card) => card.find('select'))
    await subSelects[0]!.setValue('2')
    await subSelects[1]!.setValue('3')

    expect((subSelects[0]!.element as HTMLSelectElement).value).toBe('2')
    expect((subSelects[1]!.element as HTMLSelectElement).value).toBe('3')
    expect((subSelects[2]!.element as HTMLSelectElement).value).toBe('未設定')
  })

  it('正常入力→createUserが正しいrequest bodyで呼ばれ、/login?registered=trueへ遷移する(自動ログインは行わない)', async () => {
    vi.mocked(createUser).mockResolvedValue({ id: 1 })

    const wrapper = await mountReady()
    await fillAccountFields(wrapper)
    await selectMainCharacter(wrapper, '1')
    await mainCard(wrapper).findAll('select')[1]!.setValue('GOLD')

    await wrapper.find('#register-playtime-start').setValue('20:00')
    await wrapper.find('#register-playtime-end').setValue('23:00')
    await wrapper.find('#register-message').setValue('よろしくお願いします')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createUser).toHaveBeenCalledWith({
      email: 'test@example.com',
      password: 'password123',
      name: 'Test User',
      characters: [{ characterId: 1, rank: 'GOLD', mr: null }],
      playTimeStart: '20:00',
      playTimeEnd: '23:00',
      message: 'よろしくお願いします',
    })
    expect(push).toHaveBeenCalledWith('/login?registered=true')
  })

  it('MAIN+SUBのcharacters配列が正しい順番になり、未選択SUB CHARACTERはrequestに含まれない', async () => {
    vi.mocked(createUser).mockResolvedValue({ id: 1 })

    const wrapper = await mountReady()
    await fillAccountFields(wrapper)
    await selectMainCharacter(wrapper, '1')

    const subSelects = subCards(wrapper).map((card) => card.find('select'))
    await subSelects[0]!.setValue('2')
    // sub2は未設定のまま、sub3のみ選択する
    await subSelects[2]!.setValue('3')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const request = vi.mocked(createUser).mock.calls[0]![0]
    expect(request.characters).toEqual([
      { characterId: 1, rank: 'ROOKIE', mr: null },
      { characterId: 2, rank: 'ROOKIE', mr: null },
      { characterId: 3, rank: 'ROOKIE', mr: null },
    ])
  })

  it('PLAY TIME/MESSAGEが任意項目として未入力でも登録でき、未入力の場合はnullが送信される', async () => {
    vi.mocked(createUser).mockResolvedValue({ id: 1 })

    const wrapper = await mountReady()
    await fillAccountFields(wrapper)
    await selectMainCharacter(wrapper, '1')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const request = vi.mocked(createUser).mock.calls[0]![0]
    expect(request.playTimeStart).toBeNull()
    expect(request.playTimeEnd).toBeNull()
    expect(request.message).toBeNull()
    expect(push).toHaveBeenCalledWith('/login?registered=true')
  })

  it('password と confirmPassword が不一致の場合、createUserを呼ばずエラーを表示する', async () => {
    const wrapper = await mountReady()
    await fillAccountFields(wrapper, { confirmPassword: 'different123' })
    await selectMainCharacter(wrapper, '1')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createUser).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('PASSWORDとCONFIRM PASSWORDが一致しません')
  })

  it('PASSWORDが8文字未満の場合、createUserを呼ばずエラーを表示する', async () => {
    const wrapper = await mountReady()
    await fillAccountFields(wrapper, { password: 'short1', confirmPassword: 'short1' })
    await selectMainCharacter(wrapper, '1')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createUser).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('PASSWORDは8文字以上100文字以内で入力してください')
  })

  it('MAIN CHARACTER未選択の場合、createUserを呼ばずエラーを表示する', async () => {
    const wrapper = await mountReady()
    await fillAccountFields(wrapper)

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createUser).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('MAIN CHARACTERを選択してください')
  })

  it('MAINとSUBで同じCharacterを選択した場合、createUserを呼ばずエラーを表示する', async () => {
    const wrapper = await mountReady()
    await fillAccountFields(wrapper)
    await selectMainCharacter(wrapper, '1')

    const sub1Select = subCards(wrapper)[0]!.find('select')
    await sub1Select.setValue('1')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createUser).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('同じCharacterを複数選択することはできません')
  })

  it('MASTER以外のCharacterはMR inputがdisabledになり、送信時もmr: nullになる', async () => {
    vi.mocked(createUser).mockResolvedValue({ id: 1 })

    const wrapper = await mountReady()
    await fillAccountFields(wrapper)
    await selectMainCharacter(wrapper, '1')

    const mainMrInput = mainCard(wrapper).find('input')
    expect((mainMrInput.element as HTMLInputElement).disabled).toBe(true)

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const request = vi.mocked(createUser).mock.calls[0]![0]
    expect(request.characters[0]).toEqual({ characterId: 1, rank: 'ROOKIE', mr: null })
  })

  it('MASTER選択時はMR inputが入力可能になり、入力値が送信される', async () => {
    vi.mocked(createUser).mockResolvedValue({ id: 1 })

    const wrapper = await mountReady()
    await fillAccountFields(wrapper)
    await selectMainCharacter(wrapper, '1')

    const mainRankSelect = mainCard(wrapper).findAll('select')[1]!
    const mainMrInput = mainCard(wrapper).find('input')
    await mainRankSelect.setValue('MASTER')
    expect((mainMrInput.element as HTMLInputElement).disabled).toBe(false)
    await mainMrInput.setValue('1650')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const request = vi.mocked(createUser).mock.calls[0]![0]
    expect(request.characters[0]).toEqual({ characterId: 1, rank: 'MASTER', mr: 1650 })
  })

  it('rankをMASTERから別のrankへ変更するとMRがクリアされinput disabledになる', async () => {
    const wrapper = await mountReady()
    await selectMainCharacter(wrapper, '1')

    const mainRankSelect = mainCard(wrapper).findAll('select')[1]!
    const mainMrInput = mainCard(wrapper).find('input')

    await mainRankSelect.setValue('MASTER')
    await mainMrInput.setValue('1650')
    await mainRankSelect.setValue('DIAMOND')

    expect((mainMrInput.element as HTMLInputElement).value).toBe('')
    expect((mainMrInput.element as HTMLInputElement).disabled).toBe(true)
  })

  it('409エラー時はメールアドレス重複メッセージを表示する', async () => {
    vi.mocked(createUser).mockRejectedValue({
      isAxiosError: true,
      response: { status: 409 },
    })

    const wrapper = await mountReady()
    await fillAccountFields(wrapper)
    await selectMainCharacter(wrapper, '1')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('このメールアドレスは既に登録されています')
    expect(push).not.toHaveBeenCalled()
  })

  it('400エラー時は入力内容確認メッセージを表示する', async () => {
    vi.mocked(createUser).mockRejectedValue({
      isAxiosError: true,
      response: { status: 400 },
    })

    const wrapper = await mountReady()
    await fillAccountFields(wrapper)
    await selectMainCharacter(wrapper, '1')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('入力内容を確認してください')
    expect(push).not.toHaveBeenCalled()
  })

  it('その他のエラー時は登録失敗メッセージを表示する', async () => {
    vi.mocked(createUser).mockRejectedValue(new Error('network error'))

    const wrapper = await mountReady()
    await fillAccountFields(wrapper)
    await selectMainCharacter(wrapper, '1')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('登録に失敗しました')
    expect(push).not.toHaveBeenCalled()
  })

  it('Character一覧取得に失敗した場合、エラーを表示しフォームを表示しない(登録できない)', async () => {
    vi.mocked(fetchCharacters).mockRejectedValue(new Error('network error'))

    const wrapper = await mountReady()

    expect(wrapper.text()).toContain('キャラクター一覧の取得に失敗しました')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('送信中はCREATE ACCOUNTボタンがdisabledになりCREATING ACCOUNT...を表示する', async () => {
    let resolveCreate: (() => void) | undefined
    vi.mocked(createUser).mockReturnValue(
      new Promise((resolve) => {
        resolveCreate = () => resolve({ id: 1 })
      }),
    )

    const wrapper = await mountReady()
    await fillAccountFields(wrapper)
    await selectMainCharacter(wrapper, '1')

    await wrapper.find('form').trigger('submit')

    const button = wrapper.find('button[type="submit"]')
    expect(button.text()).toBe('CREATING ACCOUNT...')
    expect((button.element as HTMLButtonElement).disabled).toBe(true)

    resolveCreate?.()
    await flushPromises()
  })

  it('ログイン画面(/login)へのリンクを表示する', async () => {
    const wrapper = await mountReady()

    const link = wrapper.findComponent(RouterLinkStub)
    expect(link.props('to')).toBe('/login')
  })
})
