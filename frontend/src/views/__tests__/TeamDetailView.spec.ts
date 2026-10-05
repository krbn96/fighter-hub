import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import TeamDetailView from '../TeamDetailView.vue'
import { useAuthStore } from '@/stores/auth'
import type { Team, TeamMember } from '@/types/team'
import type { Character } from '@/types/character'
import type { Tournament } from '@/types/tournament'
import type { RecruitmentApplication } from '@/types/recruitmentApplication'
import type { UserMe } from '@/types/user'

vi.mock('@/api/teams', () => ({
  fetchTeamById: vi.fn<(id: string) => Promise<Team>>(),
  fetchTeamMembers: vi.fn<(teamId: string) => Promise<TeamMember[]>>(),
  fetchMyTeams: vi.fn<() => Promise<Team[]>>(),
}))

vi.mock('@/api/characters', () => ({
  fetchCharacters: vi.fn<() => Promise<Character[]>>(),
}))

vi.mock('@/api/tournaments', () => ({
  fetchTournamentById: vi.fn<(id: string) => Promise<Tournament>>(),
}))

vi.mock('@/api/applications', () => ({
  createApplication: vi.fn<
    (teamId: string, request: { message: string | null }) => Promise<unknown>
  >(),
  fetchMyApplications: vi.fn<() => Promise<RecruitmentApplication[]>>(),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ params: { id: '10' } }),
  }
})

import { fetchMyTeams, fetchTeamById, fetchTeamMembers } from '@/api/teams'
import { fetchCharacters } from '@/api/characters'
import { fetchTournamentById } from '@/api/tournaments'
import { createApplication, fetchMyApplications } from '@/api/applications'

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

const sampleCharacters: Character[] = [
  { id: 1, name: 'RYU' },
  { id: 2, name: 'KEN' },
]

const sampleTournament: Tournament = {
  id: sampleTeam.tournamentId,
  name: sampleTeam.tournamentName,
  teamSize: 3,
  startAt: '2099-12-20T13:00:00',
  recruitmentDeadline: '2099-12-15T23:59:00',
  maxPlayers: 64,
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

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
    vi.mocked(fetchCharacters).mockReset()
    vi.mocked(fetchCharacters).mockResolvedValue(sampleCharacters)
    vi.mocked(fetchMyTeams).mockReset()
    vi.mocked(fetchMyTeams).mockResolvedValue([])
    vi.mocked(fetchMyApplications).mockReset()
    vi.mocked(fetchMyApplications).mockResolvedValue([])
    vi.mocked(fetchTournamentById).mockReset()
    vi.mocked(fetchTournamentById).mockResolvedValue(sampleTournament)
  })

  it('API取得成功時にチーム詳細が表示される(募集キャラクターはCharacter Nameで表示される)', async () => {
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Team Ryu')
    expect(wrapper.text()).toContain('STREET FIGHTER 6 CUP')
    expect(wrapper.text()).toContain('Owner User')
    expect(wrapper.text()).toContain('MASTER')
    expect(wrapper.text()).toContain('RYU / KEN')
    expect(wrapper.text()).not.toContain('1, 2')
    expect(wrapper.text()).toContain('誰でも歓迎です')
    expect(fetchTeamById).toHaveBeenCalledWith('10')
    expect(fetchCharacters).toHaveBeenCalledTimes(1)
  })

  it('characterRequirementsのcharacterIdがCharacter一覧に存在しない場合は不明なキャラクターと表示する', async () => {
    vi.mocked(fetchTeamById).mockResolvedValue({ ...sampleTeam, characterRequirements: [999] })

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('不明なキャラクター')
    expect(wrapper.text()).not.toContain('999')
  })

  it('Character API失敗時もTeam Detail本体(name/members等)は表示され、raw IDも表示されない', async () => {
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(fetchCharacters).mockRejectedValue(new Error('network error'))

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Team Ryu')
    expect(wrapper.text()).toContain('STREET FIGHTER 6 CUP')
    expect(wrapper.text()).toContain('MEMBERS (1)')
    expect(wrapper.text()).toContain('不明なキャラクター')
    expect(wrapper.text()).not.toContain('1, 2')
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

  it('既にこのTeamのmemberの場合、応募フォームの代わりに状態説明が表示される', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    const applicantId = sampleTeam.ownerId + 999
    authStore.user = createUserMe(applicantId)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(fetchTeamMembers).mockResolvedValue([
      ...defaultMembers,
      { userId: applicantId, userName: 'Applicant', joinedAt: '2026-09-02T00:00:00' },
    ])

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('このチームのメンバーです')
  })

  it('同一Tournamentの別Teamに所属済みの場合、応募フォームの代わりに状態説明が表示される', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe(sampleTeam.ownerId + 999)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(fetchMyTeams).mockResolvedValue([
      { ...sampleTeam, id: 99, name: 'Other Team', tournamentId: sampleTeam.tournamentId },
    ])

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('この大会ではすでに別のチームに所属しています')
  })

  it('このTeamへのPENDING応募が既にある場合、応募フォームの代わりに状態説明が表示される', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    const applicantId = sampleTeam.ownerId + 999
    authStore.user = createUserMe(applicantId)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(fetchMyApplications).mockResolvedValue([
      {
        id: 1,
        teamId: sampleTeam.id,
        userId: applicantId,
        userName: 'Applicant',
        message: null,
        status: 'PENDING',
        createdAt: '2026-09-01T00:00:00',
        updatedAt: '2026-09-01T00:00:00',
      },
    ])

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('このチームへの参加申請は受付済みです')
  })

  it('事前判定API(fetchMyTeams/fetchMyApplications)が失敗してもTeam Detailは表示され、応募フォームは利用可能なままになる', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe(sampleTeam.ownerId + 999)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(fetchMyTeams).mockRejectedValue(new Error('network error'))
    vi.mocked(fetchMyApplications).mockRejectedValue(new Error('network error'))

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Team Ryu')
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

  it('参加申請409時に汎用メッセージ(所属済み/申請中/募集終了の可能性)が表示される', async () => {
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

    expect(wrapper.text()).toContain(
      'この大会では既にチームに所属しているか、申請中、または募集が終了している可能性があります',
    )
  })

  it('募集締切前はapplication formが表示される', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe(sampleTeam.ownerId + 999)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(fetchTournamentById).toHaveBeenCalledWith(String(sampleTeam.tournamentId))
    expect(wrapper.find('form').exists()).toBe(true)
  })

  it('募集締切後はapplication formを表示せず案内メッセージへ差し替える', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe(sampleTeam.ownerId + 999)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(fetchTournamentById).mockResolvedValue({
      ...sampleTournament,
      recruitmentDeadline: '2020-01-01T00:00:00',
    })

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('この大会のチーム募集は終了しました')
    // Team/Members等の閲覧は締切後も可能。
    expect(wrapper.text()).toContain('Team Ryu')
    expect(wrapper.text()).toContain('MEMBERS (1)')
  })

  it('Tournament取得に失敗してもTeam Detail本体は表示され、応募フォームは利用可能なままになる', async () => {
    const authStore = useAuthStore()
    authStore.token = 'dummy-token'
    authStore.user = createUserMe(sampleTeam.ownerId + 999)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(fetchTournamentById).mockRejectedValue(new Error('network error'))

    const wrapper = mount(TeamDetailView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Team Ryu')
    expect(wrapper.find('form').exists()).toBe(true)
  })
})
