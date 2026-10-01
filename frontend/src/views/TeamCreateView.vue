<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { createTeam } from '@/api/teams'
import { fetchCharacters } from '@/api/characters'
import TeamForm from '@/components/team/TeamForm.vue'
import type { Character } from '@/types/character'
import type { TeamCreateRequest, TeamFormValues } from '@/types/team'

const route = useRoute()
const router = useRouter()

const tournamentId = String(route.params.id)

const characters = ref<Character[]>([])
const submitting = ref(false)
const error = ref('')

onMounted(async () => {
  try {
    characters.value = await fetchCharacters()
  } catch {
    // キャラクター一覧が取得できなくても、募集キャラクター条件を指定しない
    // チーム作成自体は継続できるため、フォーム全体は止めない。
    characters.value = []
  }
})

async function handleSubmit(values: TeamFormValues) {
  submitting.value = true
  error.value = ''

  const request: TeamCreateRequest = {
    tournamentId: Number(tournamentId),
    ...values,
  }

  try {
    const created = await createTeam(request)
    router.push(`/teams/${created.id}`)
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 400) {
      error.value = '入力内容を確認してください'
    } else if (axios.isAxiosError(e) && e.response?.status === 404) {
      error.value = '指定した大会が見つかりません'
    } else if (axios.isAxiosError(e) && e.response?.status === 409) {
      error.value = 'この大会では既にチームに所属しています'
    } else {
      error.value = 'チームの作成に失敗しました'
    }
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  router.push(`/tournaments/${tournamentId}`)
}
</script>

<template>
  <main class="team-create-view">
    <div class="container">
      <header class="team-create-view__header">
        <h1>CREATE TEAM</h1>
        <p>新しいチームを作って、メンバーを募集しよう。</p>
      </header>

      <TeamForm
        :characters="characters"
        :submitting="submitting"
        submit-label="CREATE TEAM"
        submitting-label="CREATING..."
        :error="error"
        @submit="handleSubmit"
        @cancel="handleCancel"
      />
    </div>
  </main>
</template>

<style scoped>
.team-create-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.team-create-view__header {
  max-width: 640px;
  margin: 0 auto var(--space-6);
  text-align: center;
}

.team-create-view__header p {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
}
</style>
