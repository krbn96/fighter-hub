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

  it('0件時に「参加申請はありません」が表示される', async () => {
    vi.mocked(fetchMyApplications).mockResolvedValue([])

    const wrapper = mount(MyApplicationView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('参加申請はありません')
    expect(fetchTeamById).not.toHaveBeenCalled()
  })
})
