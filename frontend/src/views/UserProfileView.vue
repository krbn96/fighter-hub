<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { fetchUserById } from '@/api/users'
import { fetchCharacters } from '@/api/characters'
import PlayerProfile from '@/components/user/PlayerProfile.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import type { UserPublic } from '@/types/user'
import type { Character } from '@/types/character'

const route = useRoute()

const user = ref<UserPublic | null>(null)
const characterNames = ref<Map<number, string>>(new Map())
const loading = ref(false)
const error = ref('')
const notFound = ref(false)

async function loadProfile() {
  loading.value = true
  error.value = ''
  notFound.value = false

  try {
    const userId = Number(route.params.id)
    // Character名解決の失敗はProfile自体の表示を止める理由にはしないため、
    // fetchCharactersのみ個別にcatchしfallback(空配列)にする。
    const [userResult, charactersResult] = await Promise.all([
      fetchUserById(userId),
      fetchCharacters().catch((): Character[] => []),
    ])
    user.value = userResult
    characterNames.value = new Map(
      charactersResult.map((character) => [character.id, character.name]),
    )
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 404) {
      notFound.value = true
    } else {
      error.value = 'ユーザー情報の取得に失敗しました'
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <main class="user-profile-view">
    <div class="container">
      <LoadingState v-if="loading" />
      <ErrorState v-else-if="notFound" message="指定したユーザーが見つかりません" />
      <ErrorState v-else-if="error" :message="error" retryable @retry="loadProfile" />

      <template v-else-if="user">
        <header class="user-profile-view__header">
          <h1>PLAYER PROFILE</h1>
        </header>
        <PlayerProfile :user="user" :character-names="characterNames" />
      </template>
    </div>
  </main>
</template>

<style scoped>
.user-profile-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.user-profile-view__header {
  margin-bottom: var(--space-6);
}
</style>
