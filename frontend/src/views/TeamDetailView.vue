<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import axios from 'axios'
import { fetchTeamById } from '@/api/teams'
import { createApplication } from '@/api/applications'
import { useAuthStore } from '@/stores/auth'
import type { Team } from '@/types/team'

const route = useRoute()
const authStore = useAuthStore()

const team = ref<Team | null>(null)
const loading = ref(false)
const error = ref('')
const notFound = ref(false)

// フロント側のowner判定はUI制御のみ。実際の認可はPATCH /api/teams/{id}側で行われる。
const isOwner = computed(() => team.value !== null && team.value.ownerId === authStore.user?.id)

const applicationMessage = ref('')
const applying = ref(false)
const applicationError = ref('')
const applicationSubmitted = ref(false)

onMounted(async () => {
  loading.value = true
  error.value = ''
  notFound.value = false

  try {
    const id = String(route.params.id)
    team.value = await fetchTeamById(id)
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 404) {
      notFound.value = true
    } else {
      error.value = 'チーム情報の取得に失敗しました'
    }
  } finally {
    loading.value = false
  }
})

async function handleApply() {
  if (team.value === null) {
    return
  }

  applying.value = true
  applicationError.value = ''

  try {
    await createApplication(String(team.value.id), {
      message: applicationMessage.value === '' ? null : applicationMessage.value,
    })
    // 二重送信防止のため、成功後はフォームを表示せず完了メッセージのみにする。
    applicationSubmitted.value = true
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 400) {
      applicationError.value = '自分がownerのチームには申請できません'
    } else if (axios.isAxiosError(e) && e.response?.status === 404) {
      applicationError.value = '指定したチームが見つかりません'
    } else if (axios.isAxiosError(e) && e.response?.status === 409) {
      applicationError.value = 'この大会では既にチームに所属しているか、申請中です'
    } else {
      applicationError.value = '参加申請に失敗しました'
    }
  } finally {
    applying.value = false
  }
}
</script>

<template>
  <main>
    <p v-if="loading">Loading...</p>
    <p v-else-if="notFound" role="alert">指定したチームが見つかりません</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <div v-else-if="team">
      <h1>{{ team.name }}</h1>
      <p>大会: {{ team.tournamentName }}</p>
      <p>owner: {{ team.ownerName }}</p>
      <p>募集ランク: {{ team.rankRequirement ?? '指定なし' }}</p>
      <p>
        募集キャラクターID:
        {{
          team.characterRequirements && team.characterRequirements.length > 0
            ? team.characterRequirements.join(', ')
            : '指定なし'
        }}
      </p>
      <p>募集メッセージ: {{ team.recruitmentMessage ?? '指定なし' }}</p>

      <p v-if="isOwner">
        <RouterLink :to="`/teams/${team.id}/edit`">チームを編集する</RouterLink>
      </p>

      <div v-if="isOwner">
        <p><RouterLink :to="`/teams/${team.id}/applications`">参加申請を確認する</RouterLink></p>
      </div>
      <div v-else-if="authStore.isAuthenticated">
        <div v-if="applicationSubmitted">
          <p>参加申請しました</p>
          <p><RouterLink to="/applications/my">自分の参加申請を見る</RouterLink></p>
        </div>
        <form v-else @submit.prevent="handleApply">
          <div>
            <label for="applicationMessage">参加申請メッセージ</label>
            <textarea id="applicationMessage" v-model="applicationMessage"></textarea>
          </div>
          <p v-if="applicationError" role="alert">{{ applicationError }}</p>
          <button type="submit" :disabled="applying">
            {{ applying ? '送信中...' : 'このチームに参加申請する' }}
          </button>
        </form>
      </div>

      <p><RouterLink :to="`/tournaments/${team.tournamentId}`">大会詳細へ戻る</RouterLink></p>
    </div>
  </main>
</template>
