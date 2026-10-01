<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { fetchTeamById, updateTeam } from '@/api/teams'
import { fetchCharacters } from '@/api/characters'
import { useAuthStore } from '@/stores/auth'
import TeamForm from '@/components/team/TeamForm.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import type { Character } from '@/types/character'
import type { Team, TeamFormValues, TeamUpdateRequest } from '@/types/team'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const teamId = String(route.params.id)

const team = ref<Team | null>(null)
const characters = ref<Character[]>([])

const loading = ref(false)
const submitting = ref(false)
const loadError = ref('')
const submitError = ref('')
const notFound = ref(false)
const forbidden = ref(false)

const isOwner = computed(() => team.value !== null && team.value.ownerId === authStore.user?.id)

async function loadTeam() {
  loading.value = true
  loadError.value = ''
  notFound.value = false

  try {
    const [teamResult, charactersResult] = await Promise.all([
      fetchTeamById(teamId),
      fetchCharacters().catch((): Character[] => []),
    ])

    team.value = teamResult
    characters.value = charactersResult
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 404) {
      notFound.value = true
    } else {
      loadError.value = 'チーム情報の取得に失敗しました'
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadTeam)

async function handleSubmit(values: TeamFormValues) {
  submitting.value = true
  submitError.value = ''

  const request: TeamUpdateRequest = values

  try {
    await updateTeam(teamId, request)
    router.push(`/teams/${teamId}`)
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 400) {
      submitError.value = '入力内容を確認してください'
    } else if (axios.isAxiosError(e) && e.response?.status === 403) {
      forbidden.value = true
    } else if (axios.isAxiosError(e) && e.response?.status === 404) {
      notFound.value = true
    } else {
      submitError.value = 'チームの更新に失敗しました'
    }
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  router.push(`/teams/${teamId}`)
}
</script>

<template>
  <main class="team-edit-view">
    <div class="container">
      <LoadingState v-if="loading" />
      <ErrorState v-else-if="notFound" message="指定したチームが見つかりません" />
      <ErrorState v-else-if="forbidden" message="このチームを編集する権限がありません" />
      <ErrorState v-else-if="loadError" :message="loadError" retryable @retry="loadTeam" />

      <template v-else-if="team">
        <ErrorState v-if="!isOwner" message="このチームを編集する権限がありません" />
        <template v-else>
          <header class="team-edit-view__header">
            <h1>EDIT TEAM</h1>
            <p>チーム情報を更新する。</p>
          </header>

          <TeamForm
            :initial-values="{
              name: team.name,
              rankRequirement: team.rankRequirement,
              characterRequirements: team.characterRequirements,
              recruitmentMessage: team.recruitmentMessage,
            }"
            :characters="characters"
            :submitting="submitting"
            submit-label="SAVE CHANGES"
            submitting-label="SAVING..."
            :error="submitError"
            @submit="handleSubmit"
            @cancel="handleCancel"
          />
        </template>
      </template>
    </div>
  </main>
</template>

<style scoped>
.team-edit-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.team-edit-view__header {
  max-width: 640px;
  margin: 0 auto var(--space-6);
  text-align: center;
}

.team-edit-view__header p {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
}
</style>
