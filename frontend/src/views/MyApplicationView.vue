<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchMyApplications } from '@/api/applications'
import { fetchTeamById } from '@/api/teams'
import type { RecruitmentApplication } from '@/types/recruitmentApplication'
import type { Team } from '@/types/team'

interface ApplicationWithTeam {
  application: RecruitmentApplication
  team: Team | null
}

const applications = ref<ApplicationWithTeam[]>([])
const loading = ref(false)
const error = ref('')

onMounted(async () => {
  loading.value = true
  error.value = ''

  try {
    const myApplications = await fetchMyApplications()

    // APIレスポンスにはteamIdしか無いため、fetchTeamByIdでTeam情報を補完する。
    // 同じteamIdを重複して取得しないよう、ユニークなteamIdだけをMapへキャッシュする。
    const uniqueTeamIds = [...new Set(myApplications.map((application) => application.teamId))]
    const teamEntries = await Promise.all(
      uniqueTeamIds.map(async (teamId): Promise<[number, Team | null]> => {
        try {
          const team = await fetchTeamById(String(teamId))
          return [teamId, team]
        } catch {
          return [teamId, null]
        }
      }),
    )
    const teamMap = new Map<number, Team | null>(teamEntries)

    applications.value = myApplications.map((application) => ({
      application,
      team: teamMap.get(application.teamId) ?? null,
    }))
  } catch {
    error.value = '参加申請の取得に失敗しました'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main>
    <h1>自分の参加申請一覧</h1>

    <p v-if="loading">Loading...</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <p v-else-if="applications.length === 0">参加申請はありません</p>
    <ul v-else>
      <li v-for="item in applications" :key="item.application.id">
        <RouterLink :to="`/teams/${item.application.teamId}`">
          {{ item.team ? item.team.name : `Team #${item.application.teamId}` }}
        </RouterLink>
        <span v-if="item.team"> / 大会: {{ item.team.tournamentName }}</span>
        <span> / メッセージ: {{ item.application.message ?? '(なし)' }}</span>
        <span> / status: {{ item.application.status }}</span>
      </li>
    </ul>
  </main>
</template>
