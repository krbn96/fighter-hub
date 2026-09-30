import { describe, expect, it } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'

import TeamCard from '../TeamCard.vue'
import type { Team } from '@/types/team'

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

describe('TeamCard', () => {
  it('チーム名・大会名・owner名・募集ランク・募集メッセージを表示し、チーム詳細へのリンクになる', () => {
    const wrapper = mount(TeamCard, {
      props: { team: sampleTeam },
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    expect(wrapper.text()).toContain('Team Ryu')
    expect(wrapper.text()).toContain('STREET FIGHTER 6 CUP')
    expect(wrapper.text()).toContain('Owner User')
    expect(wrapper.text()).toContain('MASTER')
    expect(wrapper.text()).toContain('誰でも歓迎です')

    const link = wrapper.findComponent(RouterLinkStub)
    expect(link.props('to')).toBe('/teams/10')
  })

  it('rankRequirement/recruitmentMessageがnullの場合はfallback文言を表示する', () => {
    const wrapper = mount(TeamCard, {
      props: {
        team: { ...sampleTeam, rankRequirement: null, recruitmentMessage: null },
      },
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    expect(wrapper.text()).toContain('指定なし')
    expect(wrapper.text()).toContain('募集メッセージはありません')
  })

  it('roleLabelを渡すとOWNER/MEMBER表示が追加される', () => {
    const wrapper = mount(TeamCard, {
      props: { team: sampleTeam, roleLabel: 'OWNER' },
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    expect(wrapper.text()).toContain('OWNER')
  })

  it('募集中/RECRUITING・メンバー数・characterRequirementsは表示しない', () => {
    const wrapper = mount(TeamCard, {
      props: { team: sampleTeam },
      global: { stubs: { RouterLink: RouterLinkStub } },
    })

    expect(wrapper.text()).not.toContain('募集中')
    expect(wrapper.text()).not.toContain('RECRUITING')
    expect(wrapper.text()).not.toContain('1, 2')
  })
})
