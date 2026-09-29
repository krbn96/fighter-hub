import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import TeamApplicationsView from '../TeamApplicationsView.vue'
import { useAuthStore } from '@/stores/auth'
import type { RecruitmentApplication } from '@/types/recruitmentApplication'
import type { Team } from '@/types/team'
import type { UserMe } from '@/types/user'

vi.mock('@/api/teams', () => ({
  fetchTeamById: vi.fn<(id: string) => Promise<Team>>(),
}))

vi.mock('@/api/applications', () => ({
  fetchTeamApplications: vi.fn<(teamId: string) => Promise<RecruitmentApplication[]>>(),
  approveApplication: vi.fn<
    (teamId: string, applicationId: number) => Promise<RecruitmentApplication>
  >(),
  rejectApplication: vi.fn<
    (teamId: string, applicationId: number) => Promise<RecruitmentApplication>
  >(),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ params: { id: '10' } }),
  }
})

import { fetchTeamById } from '@/api/teams'
import { fetchTeamApplications, approveApplication } from '@/api/applications'

const sampleTeam: Team = {
  id: 10,
  tournamentId: 1,
  tournamentName: 'STREET FIGHTER 6 CUP',
  ownerId: 1,
  ownerName: 'Owner User',
  name: 'Team Ryu',
  rankRequirement: 'MASTER',
  characterRequirements: null,
  recruitmentMessage: null,
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

const pendingApplication: RecruitmentApplication = {
  id: 100,
  teamId: 10,
  userId: 5,
  userName: 'Applicant User',
  message: 'よろしくお願いします',
  status: 'PENDING',
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

const approvedApplication: RecruitmentApplication = {
  id: 101,
  teamId: 10,
  userId: 6,
  userName: 'Other Applicant',
  message: null,
  status: 'APPROVED',
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

describe('TeamApplicationsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(fetchTeamById).mockReset()
    vi.mocked(fetchTeamApplications).mockReset()
    vi.mocked(approveApplication).mockReset()
  })

  it('ownerで応募一覧が表示され、PENDINGのみ承認/拒否ボタンが表示される', async () => {
    const authStore = useAuthStore()
    authStore.user = createUserMe(sampleTeam.ownerId)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(fetchTeamApplications).mockResolvedValue([pendingApplication, approvedApplication])

    const wrapper = mount(TeamApplicationsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Applicant User')
    expect(wrapper.text()).toContain('Other Applicant')

    // PENDINGの1件分(承認+拒否)のみボタンが表示される
    expect(wrapper.findAll('button').length).toBe(2)
  })

  it('approve成功後、一覧が再取得され最新statusが表示される', async () => {
    const authStore = useAuthStore()
    authStore.user = createUserMe(sampleTeam.ownerId)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(fetchTeamApplications)
      .mockResolvedValueOnce([pendingApplication])
      .mockResolvedValueOnce([{ ...pendingApplication, status: 'APPROVED' }])
    vi.mocked(approveApplication).mockResolvedValue({ ...pendingApplication, status: 'APPROVED' })

    const wrapper = mount(TeamApplicationsView)
    await flushPromises()

    const approveButton = wrapper.findAll('button')[0]
    await approveButton?.trigger('click')
    await flushPromises()

    expect(approveApplication).toHaveBeenCalledWith('10', pendingApplication.id)
    expect(fetchTeamApplications).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('APPROVED')
    expect(wrapper.findAll('button').length).toBe(0)
  })

  it('non-ownerの場合「このチームの参加申請を確認する権限がありません」が表示される', async () => {
    const authStore = useAuthStore()
    authStore.user = createUserMe(sampleTeam.ownerId + 999)

    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)
    vi.mocked(fetchTeamApplications).mockRejectedValue({
      isAxiosError: true,
      response: { status: 403 },
    })

    const wrapper = mount(TeamApplicationsView)
    await flushPromises()

    expect(wrapper.text()).toContain('このチームの参加申請を確認する権限がありません')
  })
})
