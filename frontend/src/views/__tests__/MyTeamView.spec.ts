import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import MyTeamView from '../MyTeamView.vue'
import { useAuthStore } from '@/stores/auth'
import type { Team } from '@/types/team'
import type { UserMe } from '@/types/user'

vi.mock('@/api/teams', () => ({
  fetchMyTeams: vi.fn<() => Promise<Team[]>>(),
}))

import { fetchMyTeams } from '@/api/teams'

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

const ownedTeam: Team = {
  id: 1,
  tournamentId: 1,
  tournamentName: 'Cup A',
  ownerId: 1,
  ownerName: 'Test User',
  name: 'Team Ryu',
  rankRequirement: 'MASTER',
  characterRequirements: null,
  recruitmentMessage: null,
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

const joinedTeam: Team = {
  id: 2,
  tournamentId: 2,
  tournamentName: 'Cup B',
  ownerId: 999,
  ownerName: 'Other Owner',
  name: 'Team Ken',
  rankRequirement: null,
  characterRequirements: null,
  recruitmentMessage: null,
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

describe('MyTeamView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(fetchMyTeams).mockReset()
  })

  it('Team一覧が表示されowner/memberの区別が正しく表示される', async () => {
    const authStore = useAuthStore()
    authStore.user = createUserMe(1)
    vi.mocked(fetchMyTeams).mockResolvedValue([ownedTeam, joinedTeam])

    const wrapper = mount(MyTeamView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('Team Ryu')
    expect(text).toContain('Team Ken')
    expect(text).toContain('owner')
    expect(text).toContain('member')
  })

  it('0件時に「所属しているチームはありません」が表示される', async () => {
    const authStore = useAuthStore()
    authStore.user = createUserMe(1)
    vi.mocked(fetchMyTeams).mockResolvedValue([])

    const wrapper = mount(MyTeamView, {
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('所属しているチームはありません')
  })
})
