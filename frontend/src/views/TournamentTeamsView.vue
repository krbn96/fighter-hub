<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import axios from 'axios'
import { fetchTeamsByTournament } from '@/api/teams'
import { fetchCharacters } from '@/api/characters'
import TeamCard from '@/components/team/TeamCard.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { RANK_OPTIONS } from '@/constants/ranks'
import type { Team } from '@/types/team'
import type { Character } from '@/types/character'

type AvailableFilter = 'AVAILABLE' | 'ALL'

const route = useRoute()

const teams = ref<Team[]>([])
const loading = ref(false)
const error = ref('')
const notFound = ref(false)

const characterList = ref<Character[]>([])

const searchName = ref('')
const selectedCharacterId = ref('')
const selectedRank = ref('')
// 初期表示はavailable=true(空きありのみ)を送る。
const availableFilter = ref<AvailableFilter>('AVAILABLE')

const isFiltered = computed(
  () =>
    searchName.value.trim() !== '' ||
    selectedCharacterId.value !== '' ||
    selectedRank.value !== '' ||
    availableFilter.value !== 'AVAILABLE',
)

function toCharacterIdParam(value: string): number | undefined {
  return value === '' ? undefined : Number(value)
}

function toRankParam(value: string): string | undefined {
  return value === '' ? undefined : value
}

function toAvailableParam(filter: AvailableFilter): boolean | undefined {
  return filter === 'AVAILABLE' ? true : undefined
}

async function loadTeams() {
  loading.value = true
  error.value = ''
  notFound.value = false

  try {
    const tournamentId = String(route.params.id)
    // APIレスポンスの順序をそのまま表示する(フロント側でのsortはしない)
    teams.value = await fetchTeamsByTournament(tournamentId, {
      name: searchName.value,
      characterId: toCharacterIdParam(selectedCharacterId.value),
      rank: toRankParam(selectedRank.value),
      available: toAvailableParam(availableFilter.value),
    })
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 404) {
      notFound.value = true
    } else {
      error.value = 'チーム一覧の取得に失敗しました'
    }
  } finally {
    loading.value = false
  }
}

async function loadCharacters() {
  try {
    characterList.value = await fetchCharacters()
  } catch {
    // キャラクター一覧の取得に失敗しても、チーム検索自体は壊さない
    // (募集キャラクターの選択肢が「すべて」のみになるだけで、他の検索条件は使える)。
  }
}

// チーム名はキー入力ごとの再取得を避けるため、入力停止から一定時間後にのみ再取得する
// (募集キャラクター/プレイヤーランク/空き状況はselectのchangeごとに即時再取得でよいため対象外)。
let searchNameDebounceTimer: ReturnType<typeof setTimeout> | undefined

function handleSearchNameInput() {
  if (searchNameDebounceTimer !== undefined) {
    clearTimeout(searchNameDebounceTimer)
  }
  searchNameDebounceTimer = setTimeout(() => {
    searchNameDebounceTimer = undefined
    loadTeams()
  }, 300)
}

function handleFilterChange() {
  if (searchNameDebounceTimer !== undefined) {
    clearTimeout(searchNameDebounceTimer)
    searchNameDebounceTimer = undefined
  }
  loadTeams()
}

onMounted(() => {
  loadCharacters()
  loadTeams()
})
onUnmounted(() => {
  if (searchNameDebounceTimer !== undefined) {
    clearTimeout(searchNameDebounceTimer)
  }
})
</script>

<template>
  <main class="tournament-teams-view">
    <div class="container">
      <header class="tournament-teams-view__header">
        <h1>FIND A TEAM</h1>
      </header>

      <BaseCard class="tournament-teams-view__search">
        <div class="tournament-teams-view__search-field tournament-teams-view__search-field--name">
          <label class="tournament-teams-view__search-label" for="team-search-name">チームを検索</label>
          <div class="tournament-teams-view__search-input-wrapper">
            <svg
              class="tournament-teams-view__search-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <circle cx="11" cy="11" r="7" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
            <input
              id="team-search-name"
              v-model="searchName"
              type="text"
              placeholder="チーム名を入力"
              class="tournament-teams-view__search-name"
              @input="handleSearchNameInput"
            />
          </div>
        </div>

        <div
          class="tournament-teams-view__search-field tournament-teams-view__search-field--character"
        >
          <label class="tournament-teams-view__search-label" for="team-search-character"
            >募集キャラクター</label
          >
          <select
            id="team-search-character"
            v-model="selectedCharacterId"
            class="tournament-teams-view__search-character"
            @change="handleFilterChange"
          >
            <option value="">すべて</option>
            <option v-for="character in characterList" :key="character.id" :value="character.id">
              {{ character.name }}
            </option>
          </select>
        </div>

        <div class="tournament-teams-view__search-field tournament-teams-view__search-field--rank">
          <label class="tournament-teams-view__search-label" for="team-search-rank"
            >プレイヤーランク</label
          >
          <select
            id="team-search-rank"
            v-model="selectedRank"
            class="tournament-teams-view__search-rank"
            @change="handleFilterChange"
          >
            <option value="">すべて</option>
            <option v-for="rank in RANK_OPTIONS" :key="rank.value" :value="rank.value">
              {{ rank.label }}
            </option>
          </select>
        </div>

        <div
          class="tournament-teams-view__search-field tournament-teams-view__search-field--available"
        >
          <label class="tournament-teams-view__search-label" for="team-search-available"
            >空き状況</label
          >
          <select
            id="team-search-available"
            v-model="availableFilter"
            class="tournament-teams-view__search-available"
            @change="handleFilterChange"
          >
            <option value="AVAILABLE">空きあり</option>
            <option value="ALL">すべて</option>
          </select>
        </div>
      </BaseCard>

      <LoadingState v-if="loading" />
      <ErrorState v-else-if="notFound" message="指定した大会が見つかりません" />
      <ErrorState v-else-if="error" :message="error" retryable @retry="loadTeams" />
      <EmptyState
        v-else-if="teams.length === 0"
        :message="isFiltered ? '条件に一致するチームが見つかりません' : 'この大会にはまだチームがありません'"
      />
      <div v-else class="tournament-teams-view__grid">
        <TeamCard v-for="team in teams" :key="team.id" :team="team" />
      </div>

      <p class="tournament-teams-view__back">
        <RouterLink :to="`/tournaments/${route.params.id}`">大会詳細へ戻る</RouterLink>
      </p>
    </div>
  </main>
</template>

<style scoped>
.tournament-teams-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.tournament-teams-view__header {
  margin-bottom: var(--space-6);
}

.tournament-teams-view__search {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.tournament-teams-view__search-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.tournament-teams-view__search-field--name {
  flex: 1 1 auto;
}

.tournament-teams-view__search-field--character,
.tournament-teams-view__search-field--rank,
.tournament-teams-view__search-field--available {
  flex: 0 1 auto;
}

.tournament-teams-view__search-label {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.tournament-teams-view__search-input-wrapper {
  position: relative;
}

.tournament-teams-view__search-icon {
  position: absolute;
  top: 50%;
  left: var(--space-3);
  transform: translateY(-50%);
  width: 20px;
  height: 20px;
  color: var(--color-text-secondary);
  pointer-events: none;
}

.tournament-teams-view__search-name,
.tournament-teams-view__search-character,
.tournament-teams-view__search-rank,
.tournament-teams-view__search-available {
  width: 100%;
  height: 50px;
  font-family: inherit;
  font-size: 1rem;
  padding: 0 var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
  color: var(--color-text);
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast);
}

.tournament-teams-view__search-name {
  padding-left: calc(var(--space-3) + 20px + var(--space-2));
}

.tournament-teams-view__search-name:focus-visible,
.tournament-teams-view__search-character:focus-visible,
.tournament-teams-view__search-rank:focus-visible,
.tournament-teams-view__search-available:focus-visible {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px var(--color-primary);
}

@media (min-width: 768px) {
  .tournament-teams-view__search {
    flex-direction: row;
    flex-wrap: wrap;
    align-items: flex-start;
  }

  .tournament-teams-view__search-field--name {
    flex-basis: 240px;
  }

  .tournament-teams-view__search-field--character,
  .tournament-teams-view__search-field--rank,
  .tournament-teams-view__search-field--available {
    flex-basis: 180px;
    min-width: 160px;
  }
}

.tournament-teams-view__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-4);
}

@media (min-width: 768px) {
  .tournament-teams-view__grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .tournament-teams-view__grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

.tournament-teams-view__back {
  margin-top: var(--space-8);
  font-size: 0.85rem;
}
</style>
