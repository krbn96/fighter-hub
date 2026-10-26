<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { deleteTournament, fetchTournaments } from '@/api/tournaments'
import { formatDateTime } from '@/utils/formatDateTime'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import type { Tournament } from '@/types/tournament'

// ADMIN専用の大会管理一覧。ルートガード(requiresAdmin)で非ADMIN/未認証は
// このコンポーネントへ到達しないが、実際の認可はbackendのService層が最終防衛線のため、
// 削除時の403はここでも表示する(router guardのバイパス・role変更後の古いキャッシュ等に備える)。
const tournaments = ref<Tournament[]>([])
const loading = ref(false)
const error = ref('')

const deleteTarget = ref<Tournament | null>(null)
const deleting = ref(false)
const deleteError = ref('')

async function loadTournaments() {
  loading.value = true
  error.value = ''

  try {
    tournaments.value = await fetchTournaments()
  } catch {
    error.value = '大会一覧の取得に失敗しました'
  } finally {
    loading.value = false
  }
}

onMounted(loadTournaments)

function openDeleteModal(tournament: Tournament) {
  deleteTarget.value = tournament
  deleteError.value = ''
}

function closeDeleteModal() {
  if (deleting.value) {
    return
  }
  deleteTarget.value = null
  deleteError.value = ''
}

async function confirmDelete() {
  if (deleteTarget.value === null) {
    return
  }

  deleting.value = true
  deleteError.value = ''

  try {
    await deleteTournament(String(deleteTarget.value.id))
    deleteTarget.value = null
    // 削除成功後に一覧を更新する(削除した大会をその場で除去するのではなく、
    // 再取得して実際のサーバー状態と一致させる)。
    await loadTournaments()
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 403) {
      deleteError.value = 'ADMIN権限が必要です'
    } else if (axios.isAxiosError(e) && e.response?.status === 404) {
      deleteError.value = '指定した大会が見つかりません'
    } else {
      deleteError.value = '大会の削除に失敗しました'
    }
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <main class="admin-tournament-list-view">
    <div class="container">
      <header class="admin-tournament-list-view__header">
        <div>
          <h1>ADMIN: TOURNAMENTS</h1>
          <p>大会の作成・編集・削除を行う(ADMIN専用)。</p>
        </div>
        <BaseButton to="/admin/tournaments/new">CREATE TOURNAMENT</BaseButton>
      </header>

      <LoadingState v-if="loading" />
      <ErrorState v-else-if="error" :message="error" retryable @retry="loadTournaments" />
      <EmptyState v-else-if="tournaments.length === 0" message="大会がありません" />

      <div v-else class="admin-tournament-list-view__list">
        <BaseCard
          v-for="tournament in tournaments"
          :key="tournament.id"
          class="admin-tournament-list-view__row"
        >
          <div class="admin-tournament-list-view__info">
            <h2 class="admin-tournament-list-view__name">{{ tournament.name }}</h2>
            <div class="admin-tournament-list-view__meta">
              <span>{{ tournament.teamSize }}人チーム / 最大{{ tournament.maxPlayers }}人</span>
              <span>START {{ formatDateTime(tournament.startAt) }}</span>
              <span>DEADLINE {{ formatDateTime(tournament.recruitmentDeadline) }}</span>
            </div>
          </div>
          <div class="admin-tournament-list-view__actions">
            <BaseButton variant="secondary" :to="`/admin/tournaments/${tournament.id}/edit`"
              >EDIT</BaseButton
            >
            <BaseButton variant="danger" type="button" @click="openDeleteModal(tournament)"
              >DELETE</BaseButton
            >
          </div>
        </BaseCard>
      </div>

      <BaseModal
        v-if="deleteTarget"
        title="大会を削除しますか？"
        @close="closeDeleteModal"
      >
        <p>「{{ deleteTarget.name }}」を削除します。</p>
        <p>大会削除後、配下のチームは表示・操作できなくなります。この操作は取り消せません。</p>
        <p v-if="deleteError" class="admin-tournament-list-view__delete-error" role="alert">
          {{ deleteError }}
        </p>

        <template #actions>
          <BaseButton
            type="button"
            variant="secondary"
            :disabled="deleting"
            @click="closeDeleteModal"
          >
            キャンセル
          </BaseButton>
          <BaseButton type="button" variant="danger" :disabled="deleting" @click="confirmDelete">
            {{ deleting ? '削除中...' : '削除する' }}
          </BaseButton>
        </template>
      </BaseModal>
    </div>
  </main>
</template>

<style scoped>
.admin-tournament-list-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.admin-tournament-list-view__header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.admin-tournament-list-view__header p {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
}

.admin-tournament-list-view__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.admin-tournament-list-view__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.admin-tournament-list-view__name {
  margin: 0 0 var(--space-1);
  font-size: 1rem;
}

.admin-tournament-list-view__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  color: var(--color-text-secondary);
  font-size: 0.8rem;
}

.admin-tournament-list-view__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.admin-tournament-list-view__delete-error {
  margin: 0;
  color: var(--color-error);
  font-size: 0.85rem;
}
</style>
