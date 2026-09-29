<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { fetchTeamById } from '@/api/teams'
import { approveApplication, fetchTeamApplications, rejectApplication } from '@/api/applications'
import { useAuthStore } from '@/stores/auth'
import type { RecruitmentApplication } from '@/types/recruitmentApplication'
import type { Team } from '@/types/team'

const route = useRoute()
const authStore = useAuthStore()

const teamId = String(route.params.id)

const team = ref<Team | null>(null)
const applications = ref<RecruitmentApplication[]>([])

const loading = ref(false)
const loadError = ref('')
const notFound = ref(false)
const forbidden = ref(false)

const processingId = ref<number | null>(null)
const actionError = ref('')

const isOwner = computed(() => team.value !== null && team.value.ownerId === authStore.user?.id)

onMounted(async () => {
  loading.value = true
  loadError.value = ''
  notFound.value = false
  forbidden.value = false

  try {
    const [teamResult, applicationsResult] = await Promise.all([
      fetchTeamById(teamId),
      fetchTeamApplications(teamId),
    ])
    team.value = teamResult
    applications.value = applicationsResult
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 403) {
      forbidden.value = true
    } else if (axios.isAxiosError(e) && e.response?.status === 404) {
      notFound.value = true
    } else {
      loadError.value = '参加申請の取得に失敗しました'
    }
  } finally {
    loading.value = false
  }
})

function actionErrorMessage(e: unknown): string {
  if (axios.isAxiosError(e) && e.response?.status === 403) {
    return 'このチームの参加申請を確認する権限がありません'
  }
  if (axios.isAxiosError(e) && e.response?.status === 404) {
    return '指定したチームまたは参加申請が見つかりません'
  }
  if (axios.isAxiosError(e) && e.response?.status === 409) {
    return 'この申請は既に処理済みか、承認できない状態です'
  }
  return '参加申請の処理に失敗しました'
}

async function handleApprove(applicationId: number) {
  processingId.value = applicationId
  actionError.value = ''

  try {
    await approveApplication(teamId, applicationId)
    // 最新のstatusを反映するため一覧を再取得する。
    applications.value = await fetchTeamApplications(teamId)
  } catch (e) {
    actionError.value = actionErrorMessage(e)
  } finally {
    processingId.value = null
  }
}

async function handleReject(applicationId: number) {
  processingId.value = applicationId
  actionError.value = ''

  try {
    await rejectApplication(teamId, applicationId)
    applications.value = await fetchTeamApplications(teamId)
  } catch (e) {
    actionError.value = actionErrorMessage(e)
  } finally {
    processingId.value = null
  }
}
</script>

<template>
  <main>
    <p v-if="loading">Loading...</p>
    <p v-else-if="notFound" role="alert">指定したチームが見つかりません</p>
    <p v-else-if="forbidden" role="alert">このチームの参加申請を確認する権限がありません</p>
    <p v-else-if="loadError" role="alert">{{ loadError }}</p>
    <template v-else-if="team">
      <div v-if="!isOwner">
        <p role="alert">このチームの参加申請を確認する権限がありません</p>
      </div>
      <div v-else>
        <h1>{{ team.name }} の参加申請一覧</h1>

        <p v-if="actionError" role="alert">{{ actionError }}</p>

        <p v-if="applications.length === 0">参加申請はありません</p>
        <ul v-else>
          <li v-for="application in applications" :key="application.id">
            <span>{{ application.userName }}</span>
            <span> / メッセージ: {{ application.message ?? '(なし)' }}</span>
            <span> / status: {{ application.status }}</span>
            <template v-if="application.status === 'PENDING'">
              <button
                type="button"
                :disabled="processingId === application.id"
                @click="handleApprove(application.id)"
              >
                承認
              </button>
              <button
                type="button"
                :disabled="processingId === application.id"
                @click="handleReject(application.id)"
              >
                拒否
              </button>
            </template>
          </li>
        </ul>
      </div>
    </template>
  </main>
</template>
