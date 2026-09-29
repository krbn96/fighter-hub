<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { apiClient } from '@/api/client'
import { createTeam } from '@/api/teams'
import { RANK_OPTIONS } from '@/constants/ranks'
import type { Character } from '@/types/character'
import type { TeamCreateRequest } from '@/types/team'

const route = useRoute()
const router = useRouter()

const tournamentId = String(route.params.id)

const name = ref('')
const rankRequirement = ref('')
const recruitmentMessage = ref('')
const selectedCharacterIds = ref<number[]>([])

const characters = ref<Character[]>([])
const submitting = ref(false)
const error = ref('')

onMounted(async () => {
  try {
    const response = await apiClient.get<Character[]>('/characters')
    characters.value = response.data
  } catch {
    // キャラクター一覧が取得できなくても、募集キャラクター条件を指定しない
    // チーム作成自体は継続できるため、フォーム全体は止めない。
    characters.value = []
  }
})

async function handleSubmit() {
  submitting.value = true
  error.value = ''

  const request: TeamCreateRequest = {
    tournamentId: Number(tournamentId),
    name: name.value,
    rankRequirement: rankRequirement.value === '' ? null : rankRequirement.value,
    characterRequirements:
      selectedCharacterIds.value.length > 0 ? selectedCharacterIds.value : null,
    recruitmentMessage: recruitmentMessage.value === '' ? null : recruitmentMessage.value,
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
</script>

<template>
  <main>
    <h1>チーム作成</h1>
    <form @submit.prevent="handleSubmit">
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

      <p v-if="error" role="alert">{{ error }}</p>

      <button type="submit" :disabled="submitting">
        {{ submitting ? '作成中...' : 'チームを作成' }}
      </button>
    </form>
  </main>
</template>
