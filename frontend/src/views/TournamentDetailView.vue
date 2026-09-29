<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import axios from 'axios'
import { fetchTournamentById } from '@/api/tournaments'
import { formatDateTime } from '@/utils/formatDateTime'
import type { Tournament } from '@/types/tournament'

const route = useRoute()

const tournament = ref<Tournament | null>(null)
const loading = ref(false)
const error = ref('')
const notFound = ref(false)

onMounted(async () => {
  loading.value = true
  error.value = ''
  notFound.value = false

  try {
    const id = String(route.params.id)
    tournament.value = await fetchTournamentById(id)
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 404) {
      notFound.value = true
    } else {
      error.value = '大会情報の取得に失敗しました'
    }
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main>
    <p v-if="loading">Loading...</p>
    <p v-else-if="notFound" role="alert">指定した大会が見つかりません</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <div v-else-if="tournament">
      <h1>{{ tournament.name }}</h1>
      <p>チームサイズ: {{ tournament.teamSize }}</p>
      <p>開始日時: {{ formatDateTime(tournament.startAt) }}</p>
      <p>最大参加人数: {{ tournament.maxPlayers }}</p>
      <p>ステータス: {{ tournament.status }}</p>

      <section>
        <h2>この大会に参加する</h2>
        <p>チームを作る・募集する（近日実装予定）</p>
        <p>
          <RouterLink :to="`/tournaments/${tournament.id}/teams`"
            >チームを探す・参加する</RouterLink
          >
        </p>
      </section>
    </div>

    <p><RouterLink to="/tournaments">大会一覧へ戻る</RouterLink></p>
  </main>
</template>
