<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { createTeam } from '@/api/teams'
import { fetchCharacters } from '@/api/characters'
import { fetchTournamentById } from '@/api/tournaments'
import { isRecruiting } from '@/utils/recruitmentStatus'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import TeamForm from '@/components/team/TeamForm.vue'
import type { Character } from '@/types/character'
import type { Tournament } from '@/types/tournament'
import type { TeamCreateRequest, TeamFormValues } from '@/types/team'

const route = useRoute()
const router = useRouter()

const tournamentId = String(route.params.id)

const characters = ref<Character[]>([])
const submitting = ref(false)
const error = ref('')

// TournamentDetail側でのdisabled表示はdirect URL accessを防げないため、ここでも
// recruitmentDeadlineを確認する。Tournament取得に失敗した場合(またはまだ取得できていない
// 場合)は、既存のcharacters取得失敗時と同じ考え方で「事前判定なし=フォームを表示し、
// 最終判定はbackendの409に委ねる」方向にfail-openする。
const tournament = ref<Tournament | null>(null)
const recruitmentClosed = computed(
  () => tournament.value !== null && !isRecruiting(tournament.value.recruitmentDeadline),
)

onMounted(async () => {
  try {
    characters.value = await fetchCharacters()
  } catch {
    // キャラクター一覧が取得できなくても、募集キャラクター条件を指定しない
    // チーム作成自体は継続できるため、フォーム全体は止めない。
    characters.value = []
  }

  try {
    tournament.value = await fetchTournamentById(tournamentId)
  } catch {
    tournament.value = null
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
      error.value = 'この大会では既にチームに所属しているか、募集が終了している可能性があります'
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

      <BaseCard v-if="recruitmentClosed" class="team-create-view__closed">
        <p>この大会のチーム募集は終了しました</p>
        <BaseButton :to="`/tournaments/${tournamentId}`">大会詳細へ戻る</BaseButton>
      </BaseCard>

      <TeamForm
        v-else
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

.team-create-view__closed {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-3);
  max-width: 640px;
  margin: 0 auto;
}
</style>
