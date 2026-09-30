<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { fetchCharacters } from '@/api/characters'
import { updateMe } from '@/api/users'
import PlayerProfile from '@/components/user/PlayerProfile.vue'
import ProfileEditForm from '@/components/user/ProfileEditForm.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import type { Character } from '@/types/character'
import type { UserUpdateRequest } from '@/types/user'

const authStore = useAuthStore()

const characterList = ref<Character[]>([])
const characterNames = ref<Map<number, string>>(new Map())

const isEditing = ref(false)
const saving = ref(false)
const saveError = ref('')

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
})

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
        <PlayerProfile v-else :user="authStore.user" :character-names="characterNames" />

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
