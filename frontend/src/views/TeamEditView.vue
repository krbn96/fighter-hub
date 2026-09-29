<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { apiClient } from '@/api/client'
import { fetchTeamById, updateTeam } from '@/api/teams'
import { useAuthStore } from '@/stores/auth'
import { RANK_OPTIONS } from '@/constants/ranks'
import type { Character } from '@/types/character'
import type { Team, TeamUpdateRequest } from '@/types/team'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const teamId = String(route.params.id)

const team = ref<Team | null>(null)
const characters = ref<Character[]>([])

const name = ref('')
const rankRequirement = ref('')
const recruitmentMessage = ref('')
const selectedCharacterIds = ref<number[]>([])

const loading = ref(false)
const submitting = ref(false)
const loadError = ref('')
const submitError = ref('')
const notFound = ref(false)
const forbidden = ref(false)

const isOwner = computed(() => team.value !== null && team.value.ownerId === authStore.user?.id)

onMounted(async () => {
  loading.value = true
  loadError.value = ''
  notFound.value = false

  try {
    const [teamResult, charactersResponse] = await Promise.all([
      fetchTeamById(teamId),
      apiClient.get<Character[]>('/characters').catch(() => ({ data: [] as Character[] })),
    ])

    team.value = teamResult
    characters.value = charactersResponse.data

    name.value = teamResult.name
    rankRequirement.value = teamResult.rankRequirement ?? ''
    recruitmentMessage.value = teamResult.recruitmentMessage ?? ''
    selectedCharacterIds.value = teamResult.characterRequirements ?? []
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 404) {
      notFound.value = true
    } else {
      loadError.value = 'チーム情報の取得に失敗しました'
    }
  } finally {
    loading.value = false
  }
})

async function handleSubmit() {
  submitting.value = true
  submitError.value = ''

  const request: TeamUpdateRequest = {
    name: name.value,
    rankRequirement: rankRequirement.value === '' ? null : rankRequirement.value,
    characterRequirements:
      selectedCharacterIds.value.length > 0 ? selectedCharacterIds.value : null,
    recruitmentMessage: recruitmentMessage.value === '' ? null : recruitmentMessage.value,
  }

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
</script>

<template>
  <main>
    <p v-if="loading">Loading...</p>
    <p v-else-if="notFound" role="alert">指定したチームが見つかりません</p>
    <p v-else-if="forbidden" role="alert">このチームを編集する権限がありません</p>
    <p v-else-if="loadError" role="alert">{{ loadError }}</p>
    <template v-else-if="team">
      <div v-if="!isOwner">
        <p role="alert">このチームを編集する権限がありません</p>
      </div>
      <form v-else @submit.prevent="handleSubmit">
        <h1>チーム編集</h1>
        <div>
          <label for="name">チーム名</label>
          <input id="name" v-model="name" type="text" required />
        </div>

        <div>
          <label for="rankRequirement">募集ランク</label>
          <select id="rankRequirement" v-model="rankRequirement">
            <option value="">指定なし</option>
            <option v-for="rank in RANK_OPTIONS" :key="rank.value" :value="rank.value">
              {{ rank.label }}
            </option>
          </select>
        </div>

        <div>
          <p>募集キャラクター</p>
          <label v-for="character in characters" :key="character.id">
            <input type="checkbox" :value="character.id" v-model="selectedCharacterIds" />
            {{ character.name }}
          </label>
        </div>

        <div>
          <label for="recruitmentMessage">募集メッセージ</label>
          <textarea id="recruitmentMessage" v-model="recruitmentMessage"></textarea>
        </div>

        <p v-if="submitError" role="alert">{{ submitError }}</p>

        <button type="submit" :disabled="submitting">
          {{ submitting ? '更新中...' : '更新する' }}
        </button>
      </form>
    </template>
  </main>
</template>
