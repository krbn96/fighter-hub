import { describe, expect, it } from 'vitest'

import { getRecruitmentStatus, isRecruiting } from '../recruitmentStatus'

describe('recruitmentStatus', () => {
  const deadline = '2026-10-01T23:59:59'

  it('締切前はRECRUITINGを返す', () => {
    const now = new Date('2026-10-01T23:59:58')
    expect(getRecruitmentStatus(deadline, now)).toBe('RECRUITING')
    expect(isRecruiting(deadline, now)).toBe(true)
  })

  it('締切ちょうどはCLOSEDを返す', () => {
    const now = new Date(deadline)
    expect(getRecruitmentStatus(deadline, now)).toBe('CLOSED')
    expect(isRecruiting(deadline, now)).toBe(false)
  })

  it('締切後はCLOSEDを返す', () => {
    const now = new Date('2026-10-02T00:00:00')
    expect(getRecruitmentStatus(deadline, now)).toBe('CLOSED')
    expect(isRecruiting(deadline, now)).toBe(false)
  })
})
