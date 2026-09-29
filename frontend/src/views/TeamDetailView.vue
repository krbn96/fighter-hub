<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import axios from 'axios'
import { fetchTeamById } from '@/api/teams'
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

      <p><RouterLink :to="`/tournaments/${team.tournamentId}`">大会詳細へ戻る</RouterLink></p>
    </div>
  </main>
</template>
