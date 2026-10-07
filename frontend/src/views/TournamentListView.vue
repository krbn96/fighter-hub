<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { fetchTournaments } from '@/api/tournaments'
import TournamentCard from '@/components/tournament/TournamentCard.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import type { Tournament } from '@/types/tournament'

type RecruitingFilter = 'ALL' | 'RECRUITING' | 'CLOSED'

const tournaments = ref<Tournament[]>([])
const loading = ref(false)
const error = ref('')

const searchName = ref('')
const recruitingFilter = ref<RecruitingFilter>('ALL')

const isFiltered = computed(() => searchName.value.trim() !== '' || recruitingFilter.value !== 'ALL')

function toRecruitingParam(filter: RecruitingFilter): boolean | undefined {
  if (filter === 'RECRUITING') return true
  if (filter === 'CLOSED') return false
  return undefined
}

async function loadTournaments() {
  loading.value = true
  error.value = ''

  try {
    // APIから返された順序をそのまま表示する(フロント側でのsortはしない)
    tournaments.value = await fetchTournaments({
      name: searchName.value,
      recruiting: toRecruitingParam(recruitingFilter.value),
    })
  } catch {
    error.value = '大会一覧の取得に失敗しました'
  } finally {
    loading.value = false
  }
}

// 大会名はキー入力ごとの再取得を避けるため、入力停止から一定時間後にのみ再取得する
// (募集状態はselectのchangeごとに即時再取得でよいため対象外)。
let searchNameDebounceTimer: ReturnType<typeof setTimeout> | undefined

function handleSearchNameInput() {
  if (searchNameDebounceTimer !== undefined) {
    clearTimeout(searchNameDebounceTimer)
  }
  searchNameDebounceTimer = setTimeout(() => {
    searchNameDebounceTimer = undefined
    loadTournaments()
  }, 300)
}

function handleRecruitingFilterChange() {
  if (searchNameDebounceTimer !== undefined) {
    clearTimeout(searchNameDebounceTimer)
    searchNameDebounceTimer = undefined
  }
  loadTournaments()
}

onMounted(loadTournaments)
onUnmounted(() => {
  if (searchNameDebounceTimer !== undefined) {
    clearTimeout(searchNameDebounceTimer)
  }
})
</script>

<template>
  <main class="tournament-list-view">
    <div class="container">
      <header class="tournament-list-view__header">
        <h1>Tournaments</h1>
        <p>参加する大会を選んで、チームを作る・探す。</p>
      </header>

      <BaseCard class="tournament-list-view__search">
        <div class="tournament-list-view__search-field tournament-list-view__search-field--name">
          <label class="tournament-list-view__search-label" for="tournament-search-name"
            >大会を検索</label
          >
          <div class="tournament-list-view__search-input-wrapper">
            <svg
              class="tournament-list-view__search-icon"
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
              id="tournament-search-name"
              v-model="searchName"
              type="text"
              placeholder="大会名を入力"
              class="tournament-list-view__search-name"
              @input="handleSearchNameInput"
            />
          </div>
        </div>

        <div class="tournament-list-view__search-field tournament-list-view__search-field--status">
          <label class="tournament-list-view__search-label" for="tournament-search-status"
            >募集状態</label
          >
          <select
            id="tournament-search-status"
            v-model="recruitingFilter"
            class="tournament-list-view__search-status"
            @change="handleRecruitingFilterChange"
          >
            <option value="ALL">すべて</option>
            <option value="RECRUITING">募集中</option>
            <option value="CLOSED">募集終了</option>
          </select>
        </div>
      </BaseCard>

      <LoadingState v-if="loading" />
      <ErrorState v-else-if="error" :message="error" retryable @retry="loadTournaments" />
      <EmptyState
        v-else-if="tournaments.length === 0"
        :message="isFiltered ? '条件に一致する大会がありません' : '大会がありません'"
      />
      <div v-else class="tournament-list-view__grid">
        <TournamentCard
          v-for="tournament in tournaments"
          :key="tournament.id"
          :tournament="tournament"
        />
      </div>
    </div>
  </main>
</template>

<style scoped>
.tournament-list-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.tournament-list-view__header {
  margin-bottom: var(--space-6);
}

.tournament-list-view__header p {
  color: var(--color-text-secondary);
  margin-top: var(--space-2);
}

.tournament-list-view__search {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.tournament-list-view__search-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.tournament-list-view__search-field--name {
  flex: 1 1 auto;
}

.tournament-list-view__search-field--status {
  flex: 0 1 auto;
}

.tournament-list-view__search-label {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.tournament-list-view__search-input-wrapper {
  position: relative;
}

.tournament-list-view__search-icon {
  position: absolute;
  top: 50%;
  left: var(--space-3);
  transform: translateY(-50%);
  width: 20px;
  height: 20px;
  color: var(--color-text-secondary);
  pointer-events: none;
}

.tournament-list-view__search-name,
.tournament-list-view__search-status {
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

.tournament-list-view__search-name {
  padding-left: calc(var(--space-3) + 20px + var(--space-2));
}

.tournament-list-view__search-name:focus-visible,
.tournament-list-view__search-status:focus-visible {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px var(--color-primary);
}

@media (min-width: 768px) {
  .tournament-list-view__search {
    flex-direction: row;
    align-items: flex-start;
  }

  .tournament-list-view__search-field--status {
    flex-basis: 220px;
    min-width: 180px;
  }
}

.tournament-list-view__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-4);
}

@media (min-width: 768px) {
  .tournament-list-view__grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .tournament-list-view__grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
