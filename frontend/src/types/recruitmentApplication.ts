// バックエンドの RecruitmentApplicationResponse に対応する型
export interface RecruitmentApplication {
  id: number
  teamId: number
  userId: number
  userName: string
  message: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  createdAt: string
  updatedAt: string
}

// バックエンドの RecruitmentApplicationCreateRequest に対応する型
export interface RecruitmentApplicationCreateRequest {
  message: string | null
}
