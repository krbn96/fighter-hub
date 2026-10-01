<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import axios from 'axios'
import { fetchTeamById, fetchTeamMembers } from '@/api/teams'
import { fetchCharacters } from '@/api/characters'
import { createApplication } from '@/api/applications'
import { useAuthStore } from '@/stores/auth'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import type { Team, TeamMember } from '@/types/team'
import type { Character } from '@/types/character'

const route = useRoute()
const authStore = useAuthStore()

const team = ref<Team | null>(null)
const members = ref<TeamMember[]>([])
const characterNames = ref<Map<number, string>>(new Map())
const loading = ref(false)
const error = ref('')
const notFound = ref(false)

// フロント側のowner判定はUI制御のみ。実際の認可はPATCH /api/teams/{id}側で行われる。
const isOwner = computed(() => team.value !== null && team.value.ownerId === authStore.user?.id)

const applicationMessage = ref('')
const applying = ref(false)
const applicationError = ref('')
const applicationSubmitted = ref(false)

async function loadTeam() {
  loading.value = true
  error.value = ''
  notFound.value = false

  try {
    const id = String(route.params.id)
    // Character名の解決に失敗しても(characterNamesが空のままでも)Team本体・Membersの
    // 表示は継続できるよう、fetchCharactersのみ個別にcatchしfallback(空配列)にする。
    const [teamResult, membersResult, charactersResult] = await Promise.all([
      fetchTeamById(id),
      fetchTeamMembers(id),
      fetchCharacters().catch((): Character[] => []),
    ])
    team.value = teamResult
    members.value = membersResult
    characterNames.value = new Map(charactersResult.map((character) => [character.id, character.name]))
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 404) {
      notFound.value = true
    } else {
      error.value = 'チーム情報の取得に失敗しました'
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadTeam)

function memberRole(member: TeamMember): 'OWNER' | 'MEMBER' {
  return team.value !== null && member.userId === team.value.ownerId ? 'OWNER' : 'MEMBER'
}

// Character一覧の取得に失敗した場合、または個々のIDがcharacterNamesに存在しない場合は
// 内部IDをそのまま露出せず「不明なキャラクター」で表す。
const characterRequirementNames = computed(() => {
  const ids = team.value?.characterRequirements
  if (!ids || ids.length === 0) {
    return '指定なし'
  }
  return ids.map((id) => characterNames.value.get(id) ?? '不明なキャラクター').join(' / ')
})

async function handleApply() {
  if (team.value === null) {
    return
  }

  applying.value = true
  applicationError.value = ''

  try {
    await createApplication(String(team.value.id), {
      message: applicationMessage.value === '' ? null : applicationMessage.value,
    })
    // 二重送信防止のため、成功後はフォームを表示せず完了メッセージのみにする。
    applicationSubmitted.value = true
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 400) {
      applicationError.value = '自分がownerのチームには申請できません'
    } else if (axios.isAxiosError(e) && e.response?.status === 404) {
      applicationError.value = '指定したチームが見つかりません'
    } else if (axios.isAxiosError(e) && e.response?.status === 409) {
      applicationError.value = 'この大会では既にチームに所属しているか、申請中です'
    } else {
      applicationError.value = '参加申請に失敗しました'
    }
  } finally {
    applying.value = false
  }
}
</script>

<template>
  <main class="team-detail-view">
    <div class="container">
      <LoadingState v-if="loading" />
      <ErrorState v-else-if="notFound" message="指定したチームが見つかりません" />
      <ErrorState v-else-if="error" :message="error" retryable @retry="loadTeam" />

      <template v-else-if="team">
        <header class="team-detail-view__header">
          <h1>{{ team.name }}</h1>
        </header>

        <BaseCard class="team-detail-view__meta">
          <div class="team-detail-view__meta-item">
            <span class="team-detail-view__meta-label">大会</span>
            <span class="team-detail-view__meta-value">{{ team.tournamentName }}</span>
          </div>
          <div class="team-detail-view__meta-item">
            <span class="team-detail-view__meta-label">owner</span>
            <span class="team-detail-view__meta-value">{{ team.ownerName }}</span>
          </div>
          <div class="team-detail-view__meta-item">
            <span class="team-detail-view__meta-label">募集ランク</span>
            <span class="team-detail-view__meta-value">{{
              team.rankRequirement ?? '指定なし'
            }}</span>
          </div>
          <div class="team-detail-view__meta-item">
            <span class="team-detail-view__meta-label">募集キャラクター</span>
            <span class="team-detail-view__meta-value">{{ characterRequirementNames }}</span>
          </div>
          <div class="team-detail-view__meta-item team-detail-view__meta-item--full">
            <span class="team-detail-view__meta-label">募集メッセージ</span>
            <span class="team-detail-view__meta-value">{{
              team.recruitmentMessage ?? '指定なし'
            }}</span>
          </div>
        </BaseCard>

        <BaseCard class="team-detail-view__members">
          <h2 class="team-detail-view__members-heading">MEMBERS ({{ members.length }})</h2>
          <ul v-if="members.length > 0" class="team-detail-view__members-list">
            <li
              v-for="member in members"
              :key="member.userId"
              class="team-detail-view__member"
            >
              <RouterLink :to="`/users/${member.userId}`" class="team-detail-view__member-name">{{
                member.userName
              }}</RouterLink>
              <span
                class="team-detail-view__member-role"
                :class="`team-detail-view__member-role--${memberRole(member).toLowerCase()}`"
                >{{ memberRole(member) }}</span
              >
            </li>
          </ul>
          <p v-else class="team-detail-view__members-empty">メンバー情報がありません</p>
        </BaseCard>

        <div v-if="isOwner" class="team-detail-view__actions">
          <BaseButton :to="`/teams/${team.id}/edit`">チームを編集する</BaseButton>
          <BaseButton variant="secondary" :to="`/teams/${team.id}/applications`"
            >参加申請を確認する</BaseButton
          >
        </div>

        <BaseCard v-else-if="authStore.isAuthenticated" class="team-detail-view__application">
          <template v-if="applicationSubmitted">
            <p>参加申請しました</p>
            <BaseButton to="/applications/my">自分の参加申請を見る</BaseButton>
          </template>
          <form v-else @submit.prevent="handleApply">
            <h2>このチームに参加申請する</h2>
            <div class="team-detail-view__form-field">
              <label for="applicationMessage">参加申請メッセージ</label>
              <textarea id="applicationMessage" v-model="applicationMessage"></textarea>
            </div>
            <p v-if="applicationError" role="alert">{{ applicationError }}</p>
            <BaseButton type="submit" :disabled="applying">
              {{ applying ? '送信中...' : 'このチームに参加申請する' }}
            </BaseButton>
          </form>
        </BaseCard>

        <BaseCard v-else class="team-detail-view__login-prompt">
          <p>参加申請するにはログインしてください</p>
          <BaseButton to="/login">LOGIN</BaseButton>
        </BaseCard>

        <p class="team-detail-view__back">
          <RouterLink :to="`/tournaments/${team.tournamentId}`">大会詳細へ戻る</RouterLink>
        </p>
      </template>
    </div>
  </main>
</template>

<style scoped>
.team-detail-view {
  padding-top: var(--space-6);
  padding-bottom: var(--space-12);
}

.team-detail-view__header {
  margin-bottom: var(--space-6);
}

.team-detail-view__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.team-detail-view__meta-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 10rem;
}

.team-detail-view__meta-item--full {
  flex: 1 1 100%;
}

.team-detail-view__meta-label {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.team-detail-view__meta-value {
  font-weight: 700;
}

.team-detail-view__members {
  margin-bottom: var(--space-6);
}

.team-detail-view__members-heading {
  margin: 0 0 var(--space-3);
  font-size: 0.9rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.team-detail-view__members-list {
  display: flex;
  flex-direction: column;
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.team-detail-view__member {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
}

.team-detail-view__member + .team-detail-view__member {
  border-top: 1px solid var(--color-border);
}

.team-detail-view__member-name {
  color: var(--color-text);
  text-decoration: none;
  transition: color var(--transition-fast);
}

.team-detail-view__member-name:hover,
.team-detail-view__member-name:focus-visible {
  color: var(--color-primary);
  text-decoration: underline;
}

.team-detail-view__member-role {
  flex-shrink: 0;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
}

.team-detail-view__member-role--owner {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.team-detail-view__members-empty {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 0.85rem;
}

.team-detail-view__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-bottom: var(--space-8);
}

.team-detail-view__application,
.team-detail-view__login-prompt {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-3);
  margin-bottom: var(--space-8);
}

.team-detail-view__application form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  width: 100%;
}

.team-detail-view__form-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.team-detail-view__form-field textarea {
  font-family: inherit;
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg);
  color: var(--color-text);
}

.team-detail-view__back {
  font-size: 0.85rem;
}
</style>
