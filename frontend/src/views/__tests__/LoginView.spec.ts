import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
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

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({ push }),
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

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(loginRequest).mockReset()
    vi.mocked(fetchMe).mockReset()
    push.mockReset()
  })

  it('ログイン成功時にauthStore.loginが呼ばれ、成功後/mypageへ遷移する', async () => {
    vi.mocked(loginRequest).mockResolvedValue({ accessToken: 'token-123' })
    vi.mocked(fetchMe).mockResolvedValue(dummyUser)

    const wrapper = mount(LoginView)
    const authStore = useAuthStore()
    const loginSpy = vi.spyOn(authStore, 'login')

    await wrapper.find('#email').setValue('test@example.com')
    await wrapper.find('#password').setValue('password123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(loginSpy).toHaveBeenCalledWith('test@example.com', 'password123')
    expect(push).toHaveBeenCalledWith('/mypage')
  })

  it('ログイン失敗時に既存のエラーメッセージが表示され、/mypageへ遷移しない', async () => {
    vi.mocked(loginRequest).mockRejectedValue(new Error('Invalid email or password.'))

    const wrapper = mount(LoginView)

    await wrapper.find('#email').setValue('test@example.com')
    await wrapper.find('#password').setValue('wrong-password')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('メールアドレスまたはパスワードが正しくありません')
    expect(push).not.toHaveBeenCalled()
  })
})
