import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'

import MyApplicationView from '../MyApplicationView.vue'
import type { RecruitmentApplication } from '@/types/recruitmentApplication'
import type { Team } from '@/types/team'

vi.mock('@/api/applications', () => ({
  fetchMyApplications: vi.fn<() => Promise<RecruitmentApplication[]>>(),
}))

vi.mock('@/api/teams', () => ({
  fetchTeamById: vi.fn<(id: string) => Promise<Team>>(),
}))

import { fetchMyApplications } from '@/api/applications'
import { fetchTeamById } from '@/api/teams'

const sampleApplication: RecruitmentApplication = {
  id: 1,
  teamId: 10,
  userId: 5,
  userName: 'Test User',
  message: 'よろしくお願いします',
  status: 'PENDING',
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

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

describe('MyApplicationView', () => {
  beforeEach(() => {
    vi.mocked(fetchMyApplications).mockReset()
    vi.mocked(fetchTeamById).mockReset()
  })

  it('自分の申請とTeam/大会情報が表示される', async () => {
    vi.mocked(fetchMyApplications).mockResolvedValue([sampleApplication])
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)

    const wrapper = mount(MyApplicationView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Team Ryu')
    expect(wrapper.text()).toContain('STREET FIGHTER 6 CUP')
    expect(wrapper.text()).toContain('よろしくお願いします')
    expect(wrapper.text()).toContain('PENDING')
    expect(fetchTeamById).toHaveBeenCalledTimes(1)
  })

  it('0件時にEmptyStateで「参加申請はありません」が表示される', async () => {
    vi.mocked(fetchMyApplications).mockResolvedValue([])

    const wrapper = mount(MyApplicationView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('参加申請はありません')
    expect(fetchTeamById).not.toHaveBeenCalled()
  })

  it('PENDING/APPROVED/REJECTEDそれぞれStatusBadgeで表示される', async () => {
    vi.mocked(fetchMyApplications).mockResolvedValue([
      { ...sampleApplication, id: 1, teamId: 10, status: 'PENDING' },
      { ...sampleApplication, id: 2, teamId: 11, status: 'APPROVED' },
      { ...sampleApplication, id: 3, teamId: 12, status: 'REJECTED' },
    ])
    vi.mocked(fetchTeamById).mockImplementation((id) =>
      Promise.resolve({ ...sampleTeam, id: Number(id) }),
    )

    const wrapper = mount(MyApplicationView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    const badges = wrapper.findAll('.status-badge')
    expect(badges).toHaveLength(3)
    expect(wrapper.text()).toContain('PENDING')
    expect(wrapper.text()).toContain('APPROVED')
    expect(wrapper.text()).toContain('REJECTED')
  })

  it('fetchMyApplications失敗時にエラーが表示される', async () => {
    vi.mocked(fetchMyApplications).mockRejectedValue(new Error('network error'))

    const wrapper = mount(MyApplicationView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('参加申請の取得に失敗しました')
  })

  it('Team個別取得が失敗した場合もfallback表示で他の申請表示は継続される', async () => {
    vi.mocked(fetchMyApplications).mockResolvedValue([sampleApplication])
    vi.mocked(fetchTeamById).mockRejectedValue(new Error('network error'))

    const wrapper = mount(MyApplicationView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain(`Team #${sampleApplication.teamId}`)
    expect(wrapper.text()).toContain('PENDING')
  })

  it('Team Detailへのリンクが/teams/{teamId}になる', async () => {
    vi.mocked(fetchMyApplications).mockResolvedValue([sampleApplication])
    vi.mocked(fetchTeamById).mockResolvedValue(sampleTeam)

    const wrapper = mount(MyApplicationView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    const link = wrapper.findComponent(RouterLinkStub)
    expect(link.props('to')).toBe(`/teams/${sampleApplication.teamId}`)
  })
})
