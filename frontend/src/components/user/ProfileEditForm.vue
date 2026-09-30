<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import { RANK_OPTIONS } from '@/constants/ranks'
import type { Character } from '@/types/character'
import type { UserCharacter, UserMe, UserUpdateRequest } from '@/types/user'

// MyPageViewの編集モードでのみ使用するフォームコンポーネント。
// API通信・authStore更新はMyPageView側の責務とし、ここではローカルなフォーム状態と
// validationのみを持つ(save/cancelはemitでMyPageViewへ委譲する)。
const props = defineProps<{
  user: UserMe
  characters: Character[]
  saving: boolean
  error: string
}>()

const emit = defineEmits<{
  save: [request: UserUpdateRequest]
  cancel: []
}>()

// GET /api/charactersの取得に失敗した場合(characters=[])は、
// 誤ったCharacter IDで保存させないためCharacter編集項目自体を無効化する。
const charactersAvailable = computed(() => props.characters.length > 0)

const name = ref(props.user.name)
const playTimeStart = ref(props.user.playTimeStart?.slice(0, 5) ?? '')
const playTimeEnd = ref(props.user.playTimeEnd?.slice(0, 5) ?? '')
const message = ref(props.user.message ?? '')

interface CharacterSlot {
  characterId: number | null
  rank: string
  mrText: string
}

const defaultRank = RANK_OPTIONS[0]?.value ?? ''

// MRはrank===MASTERの場合のみ意味を持つ仕様のため、既存DBにMASTER以外でmrが
// 設定された不整合データが存在しても、編集フォームの初期値としては採用しない(nullとして扱う)。
function buildSlot(character: UserCharacter | undefined): CharacterSlot {
  if (!character) {
    return { characterId: null, rank: defaultRank, mrText: '' }
  }
  const mrText =
    character.rank === 'MASTER' && character.mr !== null ? String(character.mr) : ''
  return {
    characterId: character.characterId,
    rank: character.rank,
    mrText,
  }
}

// MAIN CHARACTER(characters[0]) / SUB CHARACTERS(characters[1..3])という
// PlayerProfileと同じ意味付けを編集フォームでも維持する。新しいslotフィールドは追加しない。
const mainSlot = ref<CharacterSlot>(buildSlot(props.user.characters[0]))
const subSlot1 = ref<CharacterSlot>(buildSlot(props.user.characters[1]))
const subSlot2 = ref<CharacterSlot>(buildSlot(props.user.characters[2]))
const subSlot3 = ref<CharacterSlot>(buildSlot(props.user.characters[3]))

// MAIN/SUB1/SUB2/SUB3すべてに共通の処理として、rankがMASTER以外へ変わった瞬間にMRをクリアする。
// MASTERへ戻した場合はこのwatch自体は何もしないため、以前のMR値は自動復元されず空欄のままになる。
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

const slotRefs = [mainSlot, subSlot1, subSlot2, subSlot3]
slotRefs.forEach(clearMrWhenNotMaster)

function isMrEditable(slot: CharacterSlot): boolean {
  return charactersAvailable.value && slot.characterId !== null && slot.rank === 'MASTER'
}

const validationError = ref('')

function handleSubmit() {
  validationError.value = ''

  const trimmedName = name.value.trim()
  if (trimmedName === '') {
    validationError.value = 'NAMEを入力してください'
    return
  }

  const allSlots = [mainSlot.value, subSlot1.value, subSlot2.value, subSlot3.value]

  if (charactersAvailable.value) {
    if (mainSlot.value.characterId === null) {
      validationError.value = 'MAIN CHARACTERを選択してください'
      return
    }

    const selectedIds = allSlots
      .filter((slot) => slot.characterId !== null)
      .map((slot) => slot.characterId)

    if (new Set(selectedIds).size !== selectedIds.length) {
      validationError.value = '同じCharacterを複数選択することはできません'
      return
    }
  }

  const request: UserUpdateRequest = {
    name: trimmedName,
    playTimeStart: playTimeStart.value === '' ? null : playTimeStart.value,
    playTimeEnd: playTimeEnd.value === '' ? null : playTimeEnd.value,
    message: message.value.trim() === '' ? null : message.value,
  }

  // Character一覧が取得できていない場合はcharacters自体をpayloadから省略する。
  // JSON.stringifyでキーごと省略されJsonNullableのundefined(更新しない)として扱われるため、
  // 既存のcharactersはbackend側でそのまま維持される。
  if (charactersAvailable.value) {
    request.characters = allSlots
      .filter((slot) => slot.characterId !== null)
      .map((slot) => ({
        characterId: slot.characterId as number,
        rank: slot.rank,
        // MRはrank===MASTERの場合のみ有効。MASTER以外は入力値に関わらずnullを送る。
        mr: slot.rank === 'MASTER' && slot.mrText.trim() !== '' ? Number(slot.mrText) : null,
      }))
  }

  emit('save', request)
}
</script>

<template>
  <form class="profile-edit-form" @submit.prevent="handleSubmit">
    <BaseCard class="profile-edit-form__summary">
      <div class="profile-edit-form__field">
        <label class="profile-edit-form__field-label" for="profile-edit-name">NAME</label>
        <input id="profile-edit-name" v-model="name" type="text" required />
      </div>

      <div class="profile-edit-form__field">
        <span class="profile-edit-form__field-label">PLAY TIME</span>
        <div class="profile-edit-form__playtime">
          <div>
            <label class="profile-edit-form__sublabel" for="profile-edit-playtime-start"
              >START</label
            >
            <input id="profile-edit-playtime-start" v-model="playTimeStart" type="time" />
          </div>
          <div>
            <label class="profile-edit-form__sublabel" for="profile-edit-playtime-end"
              >END</label
            >
            <input id="profile-edit-playtime-end" v-model="playTimeEnd" type="time" />
          </div>
        </div>
      </div>

      <div class="profile-edit-form__field">
        <label class="profile-edit-form__field-label" for="profile-edit-message">MESSAGE</label>
        <textarea id="profile-edit-message" v-model="message" rows="4"></textarea>
      </div>
    </BaseCard>

    <section class="profile-edit-form__characters">
      <h3 class="profile-edit-form__section-heading">CHARACTERS</h3>

      <p v-if="!charactersAvailable" class="profile-edit-form__characters-unavailable" role="alert">
        キャラクター一覧を取得できなかったため、使用キャラクターは編集できません
      </p>

      <div class="profile-edit-form__main-character">
        <span class="profile-edit-form__group-label">MAIN CHARACTER</span>
        <BaseCard class="profile-edit-form__character-card">
          <div class="profile-edit-form__character-field">
            <label class="profile-edit-form__sublabel">CHARACTER</label>
            <select v-model="mainSlot.characterId" :disabled="!charactersAvailable" required>
              <option :value="null" disabled>選択してください</option>
              <option v-for="character in characters" :key="character.id" :value="character.id">
                {{ character.name }}
              </option>
            </select>
          </div>
          <div class="profile-edit-form__character-field">
            <label class="profile-edit-form__sublabel">RANK</label>
            <select v-model="mainSlot.rank" :disabled="!charactersAvailable">
              <option v-for="rank in RANK_OPTIONS" :key="rank.value" :value="rank.value">
                {{ rank.label }}
              </option>
            </select>
          </div>
          <div class="profile-edit-form__character-field">
            <label class="profile-edit-form__sublabel">MR (MASTERのみ入力可)</label>
            <input
              v-model="mainSlot.mrText"
              type="text"
              inputmode="numeric"
              pattern="[0-9]*"
              :disabled="!isMrEditable(mainSlot)"
            />
          </div>
        </BaseCard>
      </div>

      <div class="profile-edit-form__sub-characters">
        <span class="profile-edit-form__group-label">SUB CHARACTERS</span>
        <div class="profile-edit-form__sub-character-grid">
          <BaseCard class="profile-edit-form__character-card">
            <p class="profile-edit-form__slot-label">SUB 1</p>
            <div class="profile-edit-form__character-field">
              <label class="profile-edit-form__sublabel">CHARACTER</label>
              <select v-model="subSlot1.characterId" :disabled="!charactersAvailable">
                <option :value="null">未設定</option>
                <option v-for="character in characters" :key="character.id" :value="character.id">
                  {{ character.name }}
                </option>
              </select>
            </div>
            <div class="profile-edit-form__character-field">
              <label class="profile-edit-form__sublabel">RANK</label>
              <select
                v-model="subSlot1.rank"
                :disabled="!charactersAvailable || subSlot1.characterId === null"
              >
                <option v-for="rank in RANK_OPTIONS" :key="rank.value" :value="rank.value">
                  {{ rank.label }}
                </option>
              </select>
            </div>
            <div class="profile-edit-form__character-field">
              <label class="profile-edit-form__sublabel">MR (MASTERのみ入力可)</label>
              <input
                v-model="subSlot1.mrText"
                type="text"
                inputmode="numeric"
                pattern="[0-9]*"
                :disabled="!isMrEditable(subSlot1)"
              />
            </div>
          </BaseCard>

          <BaseCard class="profile-edit-form__character-card">
            <p class="profile-edit-form__slot-label">SUB 2</p>
            <div class="profile-edit-form__character-field">
              <label class="profile-edit-form__sublabel">CHARACTER</label>
              <select v-model="subSlot2.characterId" :disabled="!charactersAvailable">
                <option :value="null">未設定</option>
                <option v-for="character in characters" :key="character.id" :value="character.id">
                  {{ character.name }}
                </option>
              </select>
            </div>
            <div class="profile-edit-form__character-field">
              <label class="profile-edit-form__sublabel">RANK</label>
              <select
                v-model="subSlot2.rank"
                :disabled="!charactersAvailable || subSlot2.characterId === null"
              >
                <option v-for="rank in RANK_OPTIONS" :key="rank.value" :value="rank.value">
                  {{ rank.label }}
                </option>
              </select>
            </div>
            <div class="profile-edit-form__character-field">
              <label class="profile-edit-form__sublabel">MR (MASTERのみ入力可)</label>
              <input
                v-model="subSlot2.mrText"
                type="text"
                inputmode="numeric"
                pattern="[0-9]*"
                :disabled="!isMrEditable(subSlot2)"
              />
            </div>
          </BaseCard>

          <BaseCard class="profile-edit-form__character-card">
            <p class="profile-edit-form__slot-label">SUB 3</p>
            <div class="profile-edit-form__character-field">
              <label class="profile-edit-form__sublabel">CHARACTER</label>
              <select v-model="subSlot3.characterId" :disabled="!charactersAvailable">
                <option :value="null">未設定</option>
                <option v-for="character in characters" :key="character.id" :value="character.id">
                  {{ character.name }}
                </option>
              </select>
            </div>
            <div class="profile-edit-form__character-field">
              <label class="profile-edit-form__sublabel">RANK</label>
              <select
                v-model="subSlot3.rank"
                :disabled="!charactersAvailable || subSlot3.characterId === null"
              >
                <option v-for="rank in RANK_OPTIONS" :key="rank.value" :value="rank.value">
                  {{ rank.label }}
                </option>
              </select>
            </div>
            <div class="profile-edit-form__character-field">
              <label class="profile-edit-form__sublabel">MR (MASTERのみ入力可)</label>
              <input
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

    <p v-if="validationError || error" class="profile-edit-form__error" role="alert">
      {{ validationError || error }}
    </p>

    <div class="profile-edit-form__actions">
      <BaseButton type="submit" :disabled="saving">
        {{ saving ? 'SAVING...' : 'SAVE CHANGES' }}
      </BaseButton>
      <BaseButton type="button" variant="secondary" :disabled="saving" @click="emit('cancel')">
        CANCEL
      </BaseButton>
    </div>
  </form>
</template>

<style scoped>
.profile-edit-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.profile-edit-form__summary {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.profile-edit-form__field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.profile-edit-form__field-label,
.profile-edit-form__group-label {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.profile-edit-form__group-label {
  display: block;
  margin-bottom: var(--space-2);
}

.profile-edit-form__sublabel {
  display: block;
  font-size: 0.65rem;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.03em;
  margin-bottom: var(--space-1);
}

.profile-edit-form input[type='text'],
.profile-edit-form input[type='time'],
.profile-edit-form input[type='number'],
.profile-edit-form select,
.profile-edit-form textarea {
  width: 100%;
  font-family: inherit;
  font-size: 0.9rem;
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg);
  color: var(--color-text);
}

.profile-edit-form textarea {
  resize: vertical;
}

.profile-edit-form__playtime {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.profile-edit-form__playtime > div {
  flex: 1 1 140px;
}

.profile-edit-form__section-heading {
  margin: 0 0 var(--space-3);
  font-size: 0.9rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary);
}

.profile-edit-form__characters-unavailable {
  margin: 0 0 var(--space-3);
  color: var(--color-error);
  font-size: 0.85rem;
}

.profile-edit-form__main-character {
  margin-bottom: var(--space-4);
}

.profile-edit-form__character-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.profile-edit-form__character-field {
  display: flex;
  flex-direction: column;
}

.profile-edit-form__slot-label {
  margin: 0;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.profile-edit-form__sub-character-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-3);
}

@media (min-width: 768px) {
  .profile-edit-form__sub-character-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .profile-edit-form__sub-character-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

.profile-edit-form__error {
  margin: 0;
  color: var(--color-error);
  font-size: 0.85rem;
}

.profile-edit-form__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}
</style>
