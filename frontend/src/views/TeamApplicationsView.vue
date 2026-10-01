<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { fetchTeamById } from '@/api/teams'
import { approveApplication, fetchTeamApplications, rejectApplication } from '@/api/applications'
import { useAuthStore } from '@/stores/auth'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { formatDateTime } from '@/utils/formatDateTime'
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

async function loadApplications() {
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
}

onMounted(loadApplications)

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
    // 最新のstatusを反映するため一覧を再取得する(楽観的更新はしない)。
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
  <main class="team-applications-view">
    <div class="container">
      <LoadingState v-if="loading" />
      <ErrorState v-else-if="notFound" message="指定したチームが見つかりません" />
      <ErrorState
        v-else-if="forbidden"
        message="このチームの参加申請を確認する権限がありません"
      />
      <ErrorState v-else-if="loadError" :message="loadError" retryable @retry="loadApplications" />

      <template v-else-if="team">
        <ErrorState v-if="!isOwner" message="このチームの参加申請を確認する権限がありません" />
        <template v-else>
          <header class="team-applications-view__header">
            <h1>{{ team.name }} の参加申請一覧</h1>
          </header>

          <p v-if="actionError" class="team-applications-view__action-error" role="alert">
            {{ actionError }}
          </p>

          <EmptyState v-if="applications.length === 0" message="参加申請はありません" />
          <div v-else class="team-applications-view__list">
            <BaseCard
              v-for="application in applications"
              :key="application.id"
              class="team-applications-view__item"
            >
              <div class="team-applications-view__item-header">
                <span class="team-applications-view__applicant">{{ application.userName }}</span>
                <StatusBadge :status="application.status" />
              </div>
              <p class="team-applications-view__message">
                {{ application.message ?? '(メッセージなし)' }}
              </p>
              <p class="team-applications-view__date">
                申請日時: {{ formatDateTime(application.createdAt) }}
              </p>
              <div v-if="application.status === 'PENDING'" class="team-applications-view__actions">
                <BaseButton
                  type="button"
                  :disabled="processingId === application.id"
                  @click="handleApprove(application.id)"
                >
                  APPROVE
                </BaseButton>
                <BaseButton
                  type="button"
                  variant="secondary"
                  :disabled="processingId === application.id"
                  @click="handleReject(application.id)"
                >
                  REJECT
                </BaseButton>
              </div>
            </BaseCard>
          </div>
        </template>
      </template>
    </div>
  </main>
</template>

<style scoped>
.team-applications-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.team-applications-view__header {
  margin-bottom: var(--space-6);
}

.team-applications-view__action-error {
  margin: 0 0 var(--space-4);
  color: var(--color-error);
  font-size: 0.85rem;
}

.team-applications-view__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.team-applications-view__item {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.team-applications-view__item-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.team-applications-view__applicant {
  font-weight: 700;
}

.team-applications-view__message {
  margin: 0;
  color: var(--color-text);
  white-space: pre-wrap;
}

.team-applications-view__date {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 0.8rem;
}

.team-applications-view__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-2);
}
</style>
