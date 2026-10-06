<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { fetchCharacters } from '@/api/characters'
import { updateMe } from '@/api/users'
import { startDiscordAuthorization, unlinkDiscordAccount } from '@/api/discord'
import PlayerProfile from '@/components/user/PlayerProfile.vue'
import ProfileEditForm from '@/components/user/ProfileEditForm.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import type { Character } from '@/types/character'
import type { UserUpdateRequest } from '@/types/user'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const characterList = ref<Character[]>([])
const characterNames = ref<Map<number, string>>(new Map())

const isEditing = ref(false)
const saving = ref(false)
const saveError = ref('')

// Discord OAuth callback(GET /api/oauth/discord/callback)がBackendで処理された後、
// ?oauth=discord&result=success|error&reason=<固定reason> を付けてこのページへredirectする
// (reasonはerror時のみ、Backendが定義した固定文字列。Discordからの生のerror_description等は
// 一切URLへ含まれない)。ここではその固定reasonのみを安全な日本語メッセージへmappingする。
const OAUTH_REASON_MESSAGES: Record<string, string> = {
  cancelled: 'Discord連携がキャンセルされました。',
  invalid_state: 'Discord連携情報を確認できませんでした。もう一度お試しください。',
  expired_state: 'Discord連携の有効期限が切れました。もう一度お試しください。',
  provider_error: 'Discordとの通信に失敗しました。時間をおいてもう一度お試しください。',
  server_error: 'Discord連携中にエラーが発生しました。もう一度お試しください。',
}
const OAUTH_UNKNOWN_ERROR_MESSAGE = 'Discord連携中にエラーが発生しました。もう一度お試しください。'

// Discord関連のメッセージ(OAuth callback結果・連携開始失敗・連携解除成功/失敗)は
// すべてこの1箇所(PLAYER PROFILE付近)にまとめて表示し、複数箇所への重複表示を避ける。
const discordMessage = ref('')
const discordMessageType = ref<'success' | 'error'>('success')

function setDiscordMessage(type: 'success' | 'error', message: string) {
  discordMessageType.value = type
  discordMessage.value = message
}

const showDiscordLinkModal = ref(false)
const discordLinkLoading = ref(false)
const discordUnlinkLoading = ref(false)

onMounted(async () => {
  try {
    const characters = await fetchCharacters()
    characterList.value = characters
    characterNames.value = new Map(characters.map((character) => [character.id, character.name]))
  } catch {
    // Character一覧の取得に失敗しても、User情報自体はauthStoreにあるため
    // My Page全体をErrorStateにはしない。通常表示はPlayerProfile側のfallback表示
    // (不明なキャラクター)に委ね、編集モードではcharacterList=[]をもって
    // Character編集を無効化する(ProfileEditForm側で対応)。
  }

  if (route.query.oauth === 'discord') {
    if (route.query.result === 'success') {
      setDiscordMessage('success', 'Discordアカウントを連携しました。')
    } else {
      const reason = route.query.reason
      const message =
        typeof reason === 'string'
          ? OAUTH_REASON_MESSAGES[reason] ?? OAUTH_UNKNOWN_ERROR_MESSAGE
          : OAUTH_UNKNOWN_ERROR_MESSAGE
      setDiscordMessage('error', message)
    }

    // メッセージはすでにdiscordMessageへ保持したうえで、oauth/result/reason queryのみを
    // URLから除去する(他のqueryがあれば保持する)。vue-routerはquery値がundefinedの
    // キーをURLから省略するため、この3キーだけをundefinedで上書きすればよい。
    router.replace({
      query: { ...route.query, oauth: undefined, result: undefined, reason: undefined },
    })
  }
})

// 「Discordと連携」導線は即座にDiscordへ遷移せず、まず確認モーダルを表示する
// (同一Discordアカウントが別のFIGHTER HUBアカウントへ連携済みの場合、連携が移行する
// ことの明示的な同意を得るため)。
function openDiscordLinkModal() {
  discordMessage.value = ''
  showDiscordLinkModal.value = true
}

function closeDiscordLinkModal() {
  showDiscordLinkModal.value = false
}

async function confirmDiscordLink() {
  discordLinkLoading.value = true

  try {
    const { authorizationUrl } = await startDiscordAuthorization()
    // Discordへの遷移後はこのページ自体が破棄されるため、discordLinkLoading/
    // showDiscordLinkModalをfalseへ戻す必要はない。
    window.location.assign(authorizationUrl)
  } catch {
    showDiscordLinkModal.value = false
    discordLinkLoading.value = false
    setDiscordMessage('error', 'Discordとの連携を開始できませんでした')
  }
}

async function handleDiscordUnlink() {
  discordUnlinkLoading.value = true
  discordMessage.value = ''

  try {
    await unlinkDiscordAccount()
    // DELETEは204(bodyなし)のため、既存のfetchMe相当の再取得は行わず、
    // 解除後の既知の結果(discordUsername: null)をauthStore.userへ直接反映する
    // (MyPageView#handleSaveがPATCHレスポンスをそのままauthStore.userへ反映する
    // 既存パターンと同じ考え方)。
    if (authStore.user) {
      authStore.user.discordUsername = null
    }
    setDiscordMessage('success', 'Discord連携を解除しました')
  } catch {
    setDiscordMessage('error', 'Discord連携の解除に失敗しました')
  } finally {
    discordUnlinkLoading.value = false
  }
}

function startEditing() {
  saveError.value = ''
  isEditing.value = true
}

function cancelEditing() {
  // API呼び出しは行わない。ProfileEditFormごと破棄することで入力内容を破棄し、
  // 次回編集開始時はauthStore.userの現在値から再度フォームを初期化する。
  isEditing.value = false
  saveError.value = ''
}

async function handleSave(request: UserUpdateRequest) {
  saving.value = true
  saveError.value = ''

  try {
    // PATCHのレスポンスは更新後の最新UserMeResponseそのものなので、
    // 再度fetchMe()を呼ばずそのままauthStore.userへ反映する。
    authStore.user = await updateMe(request)
    isEditing.value = false
  } catch {
    saveError.value = 'プロフィールの更新に失敗しました'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <main class="my-page-view">
    <div class="container">
      <header class="my-page-view__header">
        <h1>PLAYER PROFILE</h1>
        <BaseButton
          v-if="!isEditing && authStore.user"
          type="button"
          variant="secondary"
          @click="startEditing"
        >
          EDIT PROFILE
        </BaseButton>
      </header>

      <template v-if="authStore.user">
        <ProfileEditForm
          v-if="isEditing"
          :user="authStore.user"
          :characters="characterList"
          :saving="saving"
          :error="saveError"
          @save="handleSave"
          @cancel="cancelEditing"
        />
        <PlayerProfile
          v-else
          :user="authStore.user"
          :character-names="characterNames"
          show-discord-actions
          :discord-unlinking="discordUnlinkLoading"
          :discord-message="discordMessage"
          :discord-message-type="discordMessageType"
          @discord-link="openDiscordLinkModal"
          @discord-unlink="handleDiscordUnlink"
        />

        <BaseCard class="my-page-view__account">
          <h2 class="my-page-view__section-heading">ACCOUNT</h2>
          <p>{{ authStore.user.email }}</p>
        </BaseCard>

        <section class="my-page-view__activity">
          <h2 class="my-page-view__section-heading">MY ACTIVITY</h2>
          <div class="my-page-view__activity-links">
            <BaseButton variant="secondary" to="/teams/my">MY TEAMS</BaseButton>
            <BaseButton variant="secondary" to="/applications/my">MY APPLICATIONS</BaseButton>
          </div>
        </section>
      </template>
    </div>

    <BaseModal v-if="showDiscordLinkModal" title="Discordアカウントを連携" @close="closeDiscordLinkModal">
      <p>Discordアカウントを連携すると、Discordユーザー名がプロフィールに公開されます。</p>
      <p>別のFIGHTER HUBアカウントに連携済みの場合、このアカウントへ連携が移行します。</p>

      <template #actions>
        <BaseButton
          type="button"
          variant="secondary"
          :disabled="discordLinkLoading"
          @click="closeDiscordLinkModal"
        >
          キャンセル
        </BaseButton>
        <BaseButton type="button" :disabled="discordLinkLoading" @click="confirmDiscordLink">
          {{ discordLinkLoading ? '連携中...' : 'Discordと連携' }}
        </BaseButton>
      </template>
    </BaseModal>
  </main>
</template>

<style scoped>
.my-page-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.my-page-view__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-6);
}

.my-page-view__account {
  margin-top: var(--space-6);
}

.my-page-view__section-heading {
  margin: 0 0 var(--space-3);
  font-size: 0.9rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary);
}

.my-page-view__activity {
  margin-top: var(--space-6);
}

.my-page-view__activity-links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}
</style>
