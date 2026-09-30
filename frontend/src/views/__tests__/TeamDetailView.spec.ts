import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import TeamDetailView from '../TeamDetailView.vue'
import { useAuthStore } from '@/stores/auth'
import type { Team, TeamMember } from '@/types/team'
import type { UserMe } from '@/types/user'

vi.mock('@/api/teams', () => ({
  fetchTeamById: vi.fn<(id: string) => Promise<Team>>(),
  fetchTeamMembers: vi.fn<(teamId: string) => Promise<TeamMember[]>>(),
}))

vi.mock('@/api/applications', () => ({
  createApplication: vi.fn<
    (teamId: string, request: { message: string | null }) => Promise<unknown>
  >(),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ params: { id: '10' } }),
  }
})

import { fetchTeamById, fetchTeamMembers } from '@/api/teams'
import { createApplication } from '@/api/applications'

const sampleTeam: Team = {
  id: 10,
  tournamentId: 1,
  tournamentName: 'STREET FIGHTER 6 CUP',
  ownerId: 1,
  ownerName: 'Owner User',
  name: 'Team Ryu',
  rankRequirement: 'MASTER',
  characterRequirements: [1, 2],
  recruitmentMessage: '誰でも歓迎です',
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

const defaultMembers: TeamMember[] = [
  { userId: sampleTeam.ownerId, userName: sampleTeam.ownerName, joinedAt: '2026-09-01T00:00:00' },
]

function createUserMe(id: number): UserMe {
  return {
    id,
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
}

describe('TeamDetailView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(fetchTeamById).mockReset()
    vi.mocked(createApplication).mockReset()
    vi.mocked(fetchTeamMembers).mockReset()
    // 既存テストが個別にmembersを設定していないケースのdefault(owner 1人所属)
    vi.mocked(fetchTeamMembers).mockResolvedValue(defaultMembers)
  })

  it('API取得成功時にチーム詳細が表示される', async () => {
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Team Ryu')
    expect(wrapper.text()).toContain('STREET FIGHTER 6 CUP')
    expect(wrapper.text()).toContain('Owner User')
    expect(wrapper.text()).toContain('MASTER')
    expect(wrapper.text()).toContain('1, 2')
    expect(wrapper.text()).toContain('誰でも歓迎です')
    expect(fetchTeamById).toHaveBeenCalledWith('10')
  })

  it('nullable項目がnullの場合は指定なしと表示される', async () => {
    vi.mocked(fetchTeamById).mockResolvedValue({
      ...sampleTeam,
      rankRequirement: null,
      characterRequirements: null,
      recruitmentMessage: null,
    })

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    const text = wrapper.text()
    expect(text.match(/指定なし/g)?.length).toBe(3)
  })

  it('404時に専用メッセージが表示され他のエラーとは区別される', async () => {
    vi.mocked(fetchTeamById).mockRejectedValue({
      isAxiosError: true,
      response: { status: 404 },
    })

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('指定したチームが見つかりません')
    expect(wrapper.text()).not.toContain('チーム情報の取得に失敗しました')
  })

  it('404以外のエラー時は汎用エラーメッセージが表示される', async () => {
    vi.mocked(fetchTeamById).mockRejectedValue(new Error('network error'))

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('チーム情報の取得に失敗しました')
    expect(wrapper.text()).not.toContain('指定したチームが見つかりません')
  })

  it('ownerの場合「チームを編集する」リンクが表示される', async () => {
    const authStore = useAuthStore()
    authStore.user = createUserMe(sampleTeam.ownerId)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    const links = wrapper.findAllComponents(RouterLinkStub)
    const editLink = links.find((link) => link.text().includes('チームを編集する'))

    expect(editLink).toBeDefined()
    expect(editLink?.props('to')).toBe(`/teams/${sampleTeam.id}/edit`)
  })

  it('non-ownerの場合「チームを編集する」リンクが表示されない', async () => {
    const authStore = useAuthStore()
    authStore.user = createUserMe(sampleTeam.ownerId + 999)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('チームを編集する')
  })

  it('non-ownerログイン時に参加申請フォームが表示される', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe(sampleTeam.ownerId + 999)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(true)
    expect(wrapper.text()).toContain('このチームに参加申請する')
  })

  it('owner時は参加申請フォームを表示せず、参加申請一覧へのリンクを表示する', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe(sampleTeam.ownerId)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(false)

    const links = wrapper.findAllComponents(RouterLinkStub)
    const applicationsLink = links.find((link) => link.text().includes('参加申請を確認する'))
    expect(applicationsLink).toBeDefined()
    expect(applicationsLink?.props('to')).toBe(`/teams/${sampleTeam.id}/applications`)
  })

  it('参加申請成功時に「参加申請しました」と表示されフォームが非表示になる', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe(sampleTeam.ownerId + 999)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(createApplication).mockResolvedValue({
      id: 1,
      teamId: sampleTeam.id,
      userId: 999,
      userName: 'Test User',
      message: null,
      status: 'PENDING',
      createdAt: '2026-09-01T00:00:00',
      updatedAt: '2026-09-01T00:00:00',
    })

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createApplication).toHaveBeenCalledWith(String(sampleTeam.id), { message: null })
    expect(wrapper.text()).toContain('参加申請しました')
    expect(wrapper.find('form').exists()).toBe(false)

    const links = wrapper.findAllComponents(RouterLinkStub)
    const myApplicationsLink = links.find((link) => link.text().includes('自分の参加申請を見る'))
    expect(myApplicationsLink).toBeDefined()
    expect(myApplicationsLink?.props('to')).toBe('/applications/my')
  })

  it('未ログイン時にログイン案内とLOGINボタンが表示される', async () => {
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('参加申請するにはログインしてください')
    expect(wrapper.find('form').exists()).toBe(false)

    const links = wrapper.findAllComponents(RouterLinkStub)
    const loginLink = links.find((link) => link.text().includes('LOGIN'))
    expect(loginLink).toBeDefined()
    expect(loginLink?.props('to')).toBe('/login')
  })

  it('Membersセクションにmembers.lengthとOWNER/MEMBER判定が反映される', async () => {
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(fetchTeamMembers).mockResolvedValue([
      { userId: sampleTeam.ownerId, userName: sampleTeam.ownerName, joinedAt: '2026-09-01T00:00:00' },
      { userId: 999, userName: 'Player B', joinedAt: '2026-09-02T00:00:00' },
    ])

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(fetchTeamMembers).toHaveBeenCalledWith('10')
    expect(wrapper.text()).toContain('MEMBERS (2)')

    const items = wrapper.findAll('.team-detail-view__member')
    const ownerItem = items.find((item) => item.text().includes(sampleTeam.ownerName))
    const memberItem = items.find((item) => item.text().includes('Player B'))

    expect(ownerItem?.text()).toContain('OWNER')
    expect(memberItem?.text()).toContain('MEMBER')
    expect(memberItem?.text()).not.toContain('OWNER')
  })

  it('member userNameが/users/{userId}へのリンクになる', async () => {
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(fetchTeamMembers).mockResolvedValue([
      { userId: sampleTeam.ownerId, userName: sampleTeam.ownerName, joinedAt: '2026-09-01T00:00:00' },
      { userId: 999, userName: 'Player B', joinedAt: '2026-09-02T00:00:00' },
    ])

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    const links = wrapper.findAllComponents(RouterLinkStub)
    const ownerLink = links.find((link) => link.text() === sampleTeam.ownerName)
    const memberLink = links.find((link) => link.text() === 'Player B')

    expect(ownerLink?.props('to')).toBe(`/users/${sampleTeam.ownerId}`)
    expect(memberLink?.props('to')).toBe('/users/999')
  })

  it('membersが0件でもレイアウトが崩れずfallback文言が表示される', async () => {
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(fetchTeamMembers).mockResolvedValue([])

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('MEMBERS (0)')
    expect(wrapper.text()).toContain('メンバー情報がありません')
  })

  it('参加申請409時に専用メッセージが表示される', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe(sampleTeam.ownerId + 999)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(createApplication).mockRejectedValue({
      isAxiosError: true,
      response: { status: 409 },
    })

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('この大会では既にチームに所属しているか、申請中です')
  })
})
