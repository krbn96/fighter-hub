<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import axios from 'axios'
import { fetchMyTeams, fetchTeamById, fetchTeamMembers } from '@/api/teams'
import { fetchCharacters } from '@/api/characters'
import { fetchTournamentById } from '@/api/tournaments'
import { createApplication, fetchMyApplications } from '@/api/applications'
import { useAuthStore } from '@/stores/auth'
import { isRecruiting } from '@/utils/recruitmentStatus'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import type { Team, TeamMember } from '@/types/team'
import type { Character } from '@/types/character'
import type { Tournament } from '@/types/tournament'
import type { RecruitmentApplication } from '@/types/recruitmentApplication'

const route = useRoute()
const authStore = useAuthStore()

const team = ref<Team | null>(null)
const members = ref<TeamMember[]>([])
const characterNames = ref<Map<number, string>>(new Map())
const loading = ref(false)
const error = ref('')
const notFound = ref(false)

// 応募欄の事前判定(member/別Team所属/申請済み)用。取得失敗時はnullのままとし、
// Team Detail本体はクラッシュさせず、事前判定なし(=既存どおりフォームを表示可能)として扱う。
const myTeams = ref<Team[] | null>(null)
const myApplications = ref<RecruitmentApplication[] | null>(null)

// 募集期限はTeamではなくTournamentの責務のため、team.tournamentIdから別途取得する。
// 取得に失敗した場合も、他の事前判定(myTeams/myApplications)と同じ考え方で
// 「事前判定なし=募集中として扱い、最終判定はbackendの409に委ねる」方向にfail-openする。
const tournament = ref<Tournament | null>(null)
const recruitmentClosed = computed(
  () => tournament.value !== null && !isRecruiting(tournament.value.recruitmentDeadline),
)

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
    // myTeams/myApplicationsは応募欄の事前判定(UX上の事前表示)にのみ使う補助情報のため、
    // 取得に失敗してもTeam Detail本体は表示できるようnullにフォールバックする
    // (未ログイン時はそもそも呼び出さない)。
    const myTeamsPromise = authStore.isAuthenticated
      ? fetchMyTeams().catch((): Team[] | null => null)
      : Promise.resolve(null)
    const myApplicationsPromise = authStore.isAuthenticated
      ? fetchMyApplications().catch((): RecruitmentApplication[] | null => null)
      : Promise.resolve(null)

    const [teamResult, membersResult, charactersResult, myTeamsResult, myApplicationsResult] =
      await Promise.all([
        fetchTeamById(id),
        fetchTeamMembers(id),
        fetchCharacters().catch((): Character[] => []),
        myTeamsPromise,
        myApplicationsPromise,
      ])
    team.value = teamResult
    members.value = membersResult
    characterNames.value = new Map(charactersResult.map((character) => [character.id, character.name]))
    myTeams.value = myTeamsResult
    myApplications.value = myApplicationsResult

    tournament.value = await fetchTournamentById(String(teamResult.tournamentId)).catch(
      (): Tournament | null => null,
    )
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

// A. 既にこのTeamのmemberかどうか(取得済みのmembersだけで判定可能、追加APIなし)
const isCurrentTeamMember = computed(() =>
  members.value.some((member) => member.userId === authStore.user?.id),
)

// B. 同一Tournamentの別Teamに所属済みかどうか(既存fetchMyTeams()の結果で判定)。
// myTeamsが取得できていない(未ログイン or 取得失敗)場合はfalse扱いとし、
// 事前判定なし=既存どおりフォームを表示可能な状態にフォールバックする。
const isInAnotherTeamSameTournament = computed(() => {
  const currentTeam = team.value
  if (currentTeam === null || myTeams.value === null) {
    return false
  }
  return myTeams.value.some(
    (myTeam) => myTeam.tournamentId === currentTeam.tournamentId && myTeam.id !== currentTeam.id,
  )
})

// C. このTeamへのPENDING応募が既にあるかどうか(既存fetchMyApplications()の結果で判定)。
// RecruitmentApplicationにはtournamentIdが無く、同一Tournament内の「別Team」への応募まで
// 判定するには応募ごとにfetchTeamByIdが必要になり複雑化するため、今回は
// 「このTeamへのPENDING応募があるか」までに判定範囲を限定している(claude-report.md参照)。
const hasPendingApplicationForThisTeam = computed(() => {
  const currentTeam = team.value
  if (currentTeam === null || myApplications.value === null) {
    return false
  }
  return myApplications.value.some(
    (application) => application.teamId === currentTeam.id && application.status === 'PENDING',
  )
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
      applicationError.value =
        'この大会では既にチームに所属しているか、申請中、または募集が終了している可能性があります'
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
            <span class="team-detail-view__meta-value team-detail-view__message">{{
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

        <template v-else-if="authStore.isAuthenticated">
          <BaseCard v-if="isCurrentTeamMember" class="team-detail-view__status-card">
            <p>このチームのメンバーです</p>
          </BaseCard>
          <BaseCard v-else-if="isInAnotherTeamSameTournament" class="team-detail-view__status-card">
            <p>この大会ではすでに別のチームに所属しています</p>
          </BaseCard>
          <BaseCard
            v-else-if="hasPendingApplicationForThisTeam"
            class="team-detail-view__status-card"
          >
            <p>このチームへの参加申請は受付済みです</p>
          </BaseCard>
          <BaseCard v-else-if="recruitmentClosed" class="team-detail-view__status-card">
            <p>この大会のチーム募集は終了しました</p>
          </BaseCard>
          <BaseCard v-else class="team-detail-view__application">
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
        </template>

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

.team-detail-view__message {
  white-space: pre-wrap;
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
.team-detail-view__login-prompt,
.team-detail-view__status-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-3);
  margin-bottom: var(--space-8);
}

.team-detail-view__status-card p {
  margin: 0;
  color: var(--color-text-secondary);
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
