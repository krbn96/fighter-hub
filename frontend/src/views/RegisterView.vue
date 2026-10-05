<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import axios from 'axios'
import { createUser } from '@/api/user'
import { fetchCharacters } from '@/api/characters'
import { RANK_OPTIONS } from '@/constants/ranks'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import type { Character } from '@/types/character'
import type { UserCreateCharacterRequest, UserCreateRequest } from '@/types/user'

const router = useRouter()

const characters = ref<Character[]>([])
const charactersLoading = ref(false)
const charactersError = ref('')

const name = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const playTimeStart = ref('')
const playTimeEnd = ref('')
const message = ref('')

interface CharacterSlot {
  characterId: number | null
  rank: string
  mrText: string
}

const defaultRank = RANK_OPTIONS[0]?.value ?? ''

function emptySlot(): CharacterSlot {
  return { characterId: null, rank: defaultRank, mrText: '' }
}

const mainSlot = ref<CharacterSlot>(emptySlot())
const subSlot1 = ref<CharacterSlot>(emptySlot())
const subSlot2 = ref<CharacterSlot>(emptySlot())
const subSlot3 = ref<CharacterSlot>(emptySlot())

// 既存ProfileEditFormと同じ仕様: rankがMASTER以外へ変わった瞬間にMRをクリアする。
function clearMrWhenNotMaster(slot: typeof mainSlot) {
  watch(
    () => slot.value.rank,
    (rank) => {
      if (rank !== 'MASTER') {
        slot.value.mrText = ''
      }
    },
  )
}

;[mainSlot, subSlot1, subSlot2, subSlot3].forEach(clearMrWhenNotMaster)

function isMrEditable(slot: CharacterSlot): boolean {
  return slot.characterId !== null && slot.rank === 'MASTER'
}

const submitting = ref(false)
const error = ref('')

async function loadCharacters() {
  charactersLoading.value = true
  charactersError.value = ''

  try {
    characters.value = await fetchCharacters()
  } catch {
    // 無効なcharacterIdを仮定したfallbackやハードコードデータは使わず、
    // 取得失敗時は登録自体を実行できない状態にする(フォームを表示しない)。
    charactersError.value = 'キャラクター一覧の取得に失敗しました。時間を置いて再度お試しください。'
  } finally {
    charactersLoading.value = false
  }
}

onMounted(loadCharacters)

function validate(): string {
  const trimmedName = name.value.trim()
  if (trimmedName === '') {
    return 'USER NAMEを入力してください'
  }
  if (trimmedName.length > 50) {
    return 'USER NAMEは50文字以内で入力してください'
  }

  if (email.value.trim() === '') {
    return 'EMAILを入力してください'
  }

  if (password.value === '') {
    return 'PASSWORDを入力してください'
  }
  if (password.value.length < 8 || password.value.length > 100) {
    return 'PASSWORDは8文字以上100文字以内で入力してください'
  }
  if (password.value !== confirmPassword.value) {
    return 'PASSWORDとCONFIRM PASSWORDが一致しません'
  }

  if (mainSlot.value.characterId === null) {
    return 'MAIN CHARACTERを選択してください'
  }
  if (mainSlot.value.rank === '') {
    return 'MAIN CHARACTERのRANKを選択してください'
  }

  const subSlots = [subSlot1.value, subSlot2.value, subSlot3.value]
  for (const slot of subSlots) {
    if (slot.characterId !== null && slot.rank === '') {
      return 'SUB CHARACTERのRANKを選択してください'
    }
  }

  const allSlots = [mainSlot.value, ...subSlots]
  const selectedIds = allSlots
    .filter((slot) => slot.characterId !== null)
    .map((slot) => slot.characterId)
  if (new Set(selectedIds).size !== selectedIds.length) {
    return '同じCharacterを複数選択することはできません'
  }

  for (const slot of allSlots) {
    if (slot.characterId === null || slot.rank !== 'MASTER' || slot.mrText.trim() === '') {
      continue
    }
    const mrValue = Number(slot.mrText)
    if (!Number.isInteger(mrValue) || mrValue < 0) {
      return 'MRは0以上の整数で入力してください'
    }
  }

  return ''
}

function toCharacterRequest(slot: CharacterSlot): UserCreateCharacterRequest {
  return {
    characterId: slot.characterId as number,
    rank: slot.rank,
    // rankがMASTER以外の場合、backendはmr!==nullを拒否するため常にnullを送る。
    mr: slot.rank === 'MASTER' && slot.mrText.trim() !== '' ? Number(slot.mrText) : null,
  }
}

async function handleSubmit() {
  error.value = ''

  const validationError = validate()
  if (validationError !== '') {
    error.value = validationError
    return
  }

  const allSlots = [mainSlot.value, subSlot1.value, subSlot2.value, subSlot3.value]
  const requestCharacters = allSlots
    .filter((slot) => slot.characterId !== null)
    .map(toCharacterRequest)

  const request: UserCreateRequest = {
    email: email.value,
    password: password.value,
    name: name.value.trim(),
    characters: requestCharacters,
    playTimeStart: playTimeStart.value === '' ? null : playTimeStart.value,
    playTimeEnd: playTimeEnd.value === '' ? null : playTimeEnd.value,
    message: message.value.trim() === '' ? null : message.value,
  }

  submitting.value = true

  try {
    await createUser(request)
    // 自動ログインは行わない。登録成功後はログイン画面へ遷移し、ユーザー自身の
    // email/passwordでログインしてもらう(authStore.loginはここから呼ばない)。
    router.push('/login?registered=true')
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 409) {
      error.value = 'このメールアドレスは既に登録されています'
    } else if (axios.isAxiosError(e) && e.response?.status === 400) {
      error.value = '入力内容を確認してください'
    } else {
      error.value = '登録に失敗しました'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="register-view">
    <div class="container">
      <header class="register-view__header">
        <h1>CREATE ACCOUNT</h1>
        <p>FIGHTER HUBに登録して、チームを作る・探すを始めよう。</p>
      </header>

      <LoadingState v-if="charactersLoading" />
      <ErrorState
        v-else-if="charactersError"
        :message="charactersError"
        retryable
        @retry="loadCharacters"
      />

      <form v-else class="register-view__form" @submit.prevent="handleSubmit">
        <BaseCard class="register-view__card">
          <h2 class="register-view__section-heading">ACCOUNT</h2>

          <div class="register-view__field">
            <label class="register-view__label" for="register-name">USER NAME *</label>
            <input id="register-name" v-model="name" type="text" required maxlength="50" />
          </div>

          <div class="register-view__field">
            <label class="register-view__label" for="register-email">EMAIL *</label>
            <input
              id="register-email"
              v-model="email"
              type="email"
              required
              autocomplete="email"
            />
          </div>

          <div class="register-view__field">
            <label class="register-view__label" for="register-password">PASSWORD *</label>
            <input
              id="register-password"
              v-model="password"
              type="password"
              required
              autocomplete="new-password"
            />
          </div>

          <div class="register-view__field">
            <label class="register-view__label" for="register-confirm-password"
              >CONFIRM PASSWORD *</label
            >
            <input
              id="register-confirm-password"
              v-model="confirmPassword"
              type="password"
              required
              autocomplete="new-password"
            />
          </div>
        </BaseCard>

        <section class="register-view__characters">
          <h2 class="register-view__section-heading">CHARACTERS</h2>

          <div class="register-view__main-character">
            <span class="register-view__group-label">MAIN CHARACTER *</span>
            <BaseCard class="register-view__character-card">
              <div class="register-view__character-field">
                <label class="register-view__sublabel" for="register-character-main"
                  >CHARACTER *</label
                >
                <select id="register-character-main" v-model="mainSlot.characterId" required>
                  <option :value="null" disabled>選択してください</option>
                  <option
                    v-for="character in characters"
                    :key="character.id"
                    :value="character.id"
                  >
                    {{ character.name }}
                  </option>
                </select>
              </div>
              <div class="register-view__character-field">
                <label class="register-view__sublabel" for="register-rank-main">RANK *</label>
                <select id="register-rank-main" v-model="mainSlot.rank">
                  <option v-for="rank in RANK_OPTIONS" :key="rank.value" :value="rank.value">
                    {{ rank.label }}
                  </option>
                </select>
              </div>
              <div class="register-view__character-field">
                <label class="register-view__sublabel" for="register-mr-main"
                  >MR (MASTERのみ入力可)</label
                >
                <input
                  id="register-mr-main"
                  v-model="mainSlot.mrText"
                  type="text"
                  inputmode="numeric"
                  pattern="[0-9]*"
                  :disabled="!isMrEditable(mainSlot)"
                />
              </div>
            </BaseCard>
          </div>

          <div class="register-view__sub-characters">
            <span class="register-view__group-label">SUB CHARACTERS</span>
            <div class="register-view__sub-character-grid">
              <BaseCard class="register-view__character-card">
                <p class="register-view__slot-label">SUB 1</p>
                <div class="register-view__character-field">
                  <label class="register-view__sublabel" for="register-character-sub1"
                    >CHARACTER</label
                  >
                  <select id="register-character-sub1" v-model="subSlot1.characterId">
                    <option :value="null">未設定</option>
                    <option
                      v-for="character in characters"
                      :key="character.id"
                      :value="character.id"
                    >
                      {{ character.name }}
                    </option>
                  </select>
                </div>
                <div class="register-view__character-field">
                  <label class="register-view__sublabel" for="register-rank-sub1">RANK</label>
                  <select
                    id="register-rank-sub1"
                    v-model="subSlot1.rank"
                    :disabled="subSlot1.characterId === null"
                  >
                    <option v-for="rank in RANK_OPTIONS" :key="rank.value" :value="rank.value">
                      {{ rank.label }}
                    </option>
                  </select>
                </div>
                <div class="register-view__character-field">
                  <label class="register-view__sublabel" for="register-mr-sub1"
                    >MR (MASTERのみ入力可)</label
                  >
                  <input
                    id="register-mr-sub1"
                    v-model="subSlot1.mrText"
                    type="text"
                    inputmode="numeric"
                    pattern="[0-9]*"
                    :disabled="!isMrEditable(subSlot1)"
                  />
                </div>
              </BaseCard>

              <BaseCard class="register-view__character-card">
                <p class="register-view__slot-label">SUB 2</p>
                <div class="register-view__character-field">
                  <label class="register-view__sublabel" for="register-character-sub2"
                    >CHARACTER</label
                  >
                  <select id="register-character-sub2" v-model="subSlot2.characterId">
                    <option :value="null">未設定</option>
                    <option
                      v-for="character in characters"
                      :key="character.id"
                      :value="character.id"
                    >
                      {{ character.name }}
                    </option>
                  </select>
                </div>
                <div class="register-view__character-field">
                  <label class="register-view__sublabel" for="register-rank-sub2">RANK</label>
                  <select
                    id="register-rank-sub2"
                    v-model="subSlot2.rank"
                    :disabled="subSlot2.characterId === null"
                  >
                    <option v-for="rank in RANK_OPTIONS" :key="rank.value" :value="rank.value">
                      {{ rank.label }}
                    </option>
                  </select>
                </div>
                <div class="register-view__character-field">
                  <label class="register-view__sublabel" for="register-mr-sub2"
                    >MR (MASTERのみ入力可)</label
                  >
                  <input
                    id="register-mr-sub2"
                    v-model="subSlot2.mrText"
                    type="text"
                    inputmode="numeric"
                    pattern="[0-9]*"
                    :disabled="!isMrEditable(subSlot2)"
                  />
                </div>
              </BaseCard>

              <BaseCard class="register-view__character-card">
                <p class="register-view__slot-label">SUB 3</p>
                <div class="register-view__character-field">
                  <label class="register-view__sublabel" for="register-character-sub3"
                    >CHARACTER</label
                  >
                  <select id="register-character-sub3" v-model="subSlot3.characterId">
                    <option :value="null">未設定</option>
                    <option
                      v-for="character in characters"
                      :key="character.id"
                      :value="character.id"
                    >
                      {{ character.name }}
                    </option>
                  </select>
                </div>
                <div class="register-view__character-field">
                  <label class="register-view__sublabel" for="register-rank-sub3">RANK</label>
                  <select
                    id="register-rank-sub3"
                    v-model="subSlot3.rank"
                    :disabled="subSlot3.characterId === null"
                  >
                    <option v-for="rank in RANK_OPTIONS" :key="rank.value" :value="rank.value">
                      {{ rank.label }}
                    </option>
                  </select>
                </div>
                <div class="register-view__character-field">
                  <label class="register-view__sublabel" for="register-mr-sub3"
                    >MR (MASTERのみ入力可)</label
                  >
                  <input
                    id="register-mr-sub3"
                    v-model="subSlot3.mrText"
                    type="text"
                    inputmode="numeric"
                    pattern="[0-9]*"
                    :disabled="!isMrEditable(subSlot3)"
                  />
                </div>
              </BaseCard>
            </div>
          </div>
        </section>

        <BaseCard class="register-view__card">
          <h2 class="register-view__section-heading">PLAY TIME</h2>
          <div class="register-view__playtime">
            <div>
              <label class="register-view__sublabel" for="register-playtime-start"
                >START TIME</label
              >
              <input id="register-playtime-start" v-model="playTimeStart" type="time" />
            </div>
            <div>
              <label class="register-view__sublabel" for="register-playtime-end">END TIME</label>
              <input id="register-playtime-end" v-model="playTimeEnd" type="time" />
            </div>
          </div>

          <div class="register-view__field">
            <label class="register-view__label" for="register-message">MESSAGE</label>
            <textarea id="register-message" v-model="message" rows="4"></textarea>
          </div>
        </BaseCard>

        <p v-if="error" class="register-view__error" role="alert">{{ error }}</p>

        <BaseButton type="submit" class="register-view__submit" :disabled="submitting">
          {{ submitting ? 'CREATING ACCOUNT...' : 'CREATE ACCOUNT' }}
        </BaseButton>

        <p class="register-view__login-link">
          Already have an account? <RouterLink to="/login">LOGIN</RouterLink>
        </p>
      </form>
    </div>
  </main>
</template>

<style scoped>
.register-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.register-view__header {
  max-width: 640px;
  margin: 0 auto var(--space-6);
  text-align: center;
}

.register-view__header p {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
}

.register-view__form {
  width: 100%;
  max-width: 640px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.register-view__card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.register-view__section-heading {
  margin: 0;
  font-size: 0.9rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary);
}

.register-view__field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.register-view__label,
.register-view__group-label {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.register-view__group-label {
  display: block;
  margin-bottom: var(--space-2);
}

.register-view__sublabel {
  display: block;
  font-size: 0.65rem;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.03em;
  margin-bottom: var(--space-1);
}

.register-view input[type='text'],
.register-view input[type='email'],
.register-view input[type='password'],
.register-view input[type='time'],
.register-view select,
.register-view textarea {
  width: 100%;
  font-family: inherit;
  font-size: 0.9rem;
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg);
  color: var(--color-text);
}

.register-view textarea {
  resize: vertical;
}

.register-view__playtime {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.register-view__playtime > div {
  flex: 1 1 140px;
}

.register-view__characters {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.register-view__main-character {
  margin-bottom: var(--space-2);
}

.register-view__character-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.register-view__character-field {
  display: flex;
  flex-direction: column;
}

.register-view__slot-label {
  margin: 0;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.register-view__sub-character-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-3);
}

@media (min-width: 768px) {
  .register-view__sub-character-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .register-view__sub-character-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

.register-view__error {
  margin: 0;
  color: var(--color-error);
  font-size: 0.85rem;
}

.register-view__submit {
  width: 100%;
}

.register-view__login-link {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  text-align: center;
}
</style>
