import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import LoginView from '../LoginView.vue'
import { useAuthStore } from '@/stores/auth'
import type { LoginRequest, LoginResponse } from '@/types/auth'
import type { UserMe } from '@/types/user'

vi.mock('@/api/auth', () => ({
  login: vi.fn<(request: LoginRequest) => Promise<LoginResponse>>(),
}))
vi.mock('@/api/user', () => ({
  fetchMe: vi.fn<() => Promise<UserMe>>(),
}))

import { login as loginRequest } from '@/api/auth'
import { fetchMe } from '@/api/user'

const push = vi.fn<(path: string) => void>()
let currentQuery: Record<string, string> = {}

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({ push }),
    useRoute: () => ({ query: currentQuery }),
  }
})

const dummyUser: UserMe = {
  id: 1,
  name: 'Test User',
  characters: [],
  playTimeStart: null,
  playTimeEnd: null,
  message: null,
  email: 'test@example.com',
  xId: null,
  discordId: null,
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

function mountLoginView() {
  return mount(LoginView, {
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
}

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(loginRequest).mockReset()
    vi.mocked(fetchMe).mockReset()
    push.mockReset()
    currentQuery = {}
  })

  it('ログイン成功時にauthStore.loginが呼ばれ、成功後/mypageへ遷移する', async () => {
    vi.mocked(loginRequest).mockResolvedValue({ accessToken: 'token-123' })
    vi.mocked(fetchMe).mockResolvedValue(dummyUser)

    const wrapper = mountLoginView()
    const authStore = useAuthStore()
    const loginSpy = vi.spyOn(authStore, 'login')

    await wrapper.find('#email').setValue('test@example.com')
    await wrapper.find('#password').setValue('password123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(loginSpy).toHaveBeenCalledWith('test@example.com', 'password123')
    expect(push).toHaveBeenCalledWith('/mypage')
  })

  it('送信中はボタンがdisabledになりLOGGING IN...を表示する', async () => {
    let resolveLogin: (() => void) | undefined
    vi.mocked(loginRequest).mockReturnValue(
      new Promise((resolve) => {
        resolveLogin = () => resolve({ accessToken: 'token-123' })
      }),
    )
    vi.mocked(fetchMe).mockResolvedValue(dummyUser)

    const wrapper = mountLoginView()

    await wrapper.find('#email').setValue('test@example.com')
    await wrapper.find('#password').setValue('password123')
    await wrapper.find('form').trigger('submit')

    const button = wrapper.find('button[type="submit"]')
    expect(button.text()).toBe('LOGGING IN...')
    expect((button.element as HTMLButtonElement).disabled).toBe(true)

    resolveLogin?.()
    await flushPromises()
  })

  it('ログイン失敗時に既存のエラーメッセージが表示され、/mypageへ遷移しない', async () => {
    vi.mocked(loginRequest).mockRejectedValue(new Error('Invalid email or password.'))

    const wrapper = mountLoginView()

    await wrapper.find('#email').setValue('test@example.com')
    await wrapper.find('#password').setValue('wrong-password')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('メールアドレスまたはパスワードが正しくありません')
    expect(push).not.toHaveBeenCalled()
  })

  it('/login?registered=trueの場合のみ登録成功メッセージを表示する', () => {
    currentQuery = { registered: 'true' }

    const wrapper = mountLoginView()

    expect(wrapper.text()).toContain('アカウントを作成しました。ログインしてください。')
  })

  it('通常の/login(registeredクエリなし)では登録成功メッセージを表示しない', () => {
    currentQuery = {}

    const wrapper = mountLoginView()

    expect(wrapper.text()).not.toContain('アカウントを作成しました。ログインしてください。')
  })

  it('新規登録画面(/register)へのリンクを表示する', () => {
    const wrapper = mountLoginView()

    const link = wrapper.findComponent(RouterLinkStub)
    expect(link.props('to')).toBe('/register')
  })
})
