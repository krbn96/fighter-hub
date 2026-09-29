import { apiClient } from './client'
import type {
  RecruitmentApplication,
  RecruitmentApplicationCreateRequest,
} from '@/types/recruitmentApplication'

// Axios呼び出しのみを担当する(api/teams.ts等と同じ形式)。
// localStorage/Bearer Tokenの扱いはapi/client.tsのrequest interceptorに任せる。

// POST /api/teams/{teamId}/applications
export async function createApplication(
  teamId: string,
  request: RecruitmentApplicationCreateRequest,
): Promise<RecruitmentApplication> {
  const response = await apiClient.post<RecruitmentApplication>(
    `/teams/${teamId}/applications`,
    request,
  )
  return response.data
}

// GET /api/applications/me
export async function fetchMyApplications(): Promise<RecruitmentApplication[]> {
  const response = await apiClient.get<RecruitmentApplication[]>('/applications/me')
  return response.data
}

// GET /api/teams/{teamId}/applications
export async function fetchTeamApplications(teamId: string): Promise<RecruitmentApplication[]> {
  const response = await apiClient.get<RecruitmentApplication[]>(`/teams/${teamId}/applications`)
  return response.data
}

// PATCH /api/teams/{teamId}/applications/{applicationId}/approve
export async function approveApplication(
  teamId: string,
  applicationId: number,
): Promise<RecruitmentApplication> {
  const response = await apiClient.patch<RecruitmentApplication>(
    `/teams/${teamId}/applications/${applicationId}/approve`,
  )
  return response.data
}

// PATCH /api/teams/{teamId}/applications/{applicationId}/reject
export async function rejectApplication(
  teamId: string,
  applicationId: number,
): Promise<RecruitmentApplication> {
  const response = await apiClient.patch<RecruitmentApplication>(
    `/teams/${teamId}/applications/${applicationId}/reject`,
  )
  return response.data
}
