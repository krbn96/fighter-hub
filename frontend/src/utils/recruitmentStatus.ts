// Tournamentの募集状態は、backendのstatus文字列ではなくrecruitmentDeadlineから導出する
// (backendもDBへ募集状態そのものは保持せず、recruitmentDeadlineと現在時刻から都度判定する設計)。
// 締切ちょうど(now === recruitmentDeadline)はCLOSED(backendのisRecruitmentOpenと同じ境界)。
export type RecruitmentStatus = 'RECRUITING' | 'CLOSED'

export function getRecruitmentStatus(recruitmentDeadline: string, now: Date = new Date()): RecruitmentStatus {
  return now < new Date(recruitmentDeadline) ? 'RECRUITING' : 'CLOSED'
}

export function isRecruiting(recruitmentDeadline: string, now: Date = new Date()): boolean {
  return getRecruitmentStatus(recruitmentDeadline, now) === 'RECRUITING'
}
