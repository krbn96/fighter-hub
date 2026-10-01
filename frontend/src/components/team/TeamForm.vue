<script setup lang="ts">
import { ref } from 'vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import { RANK_OPTIONS } from '@/constants/ranks'
import type { Character } from '@/types/character'
import type { TeamFormValues } from '@/types/team'

// Create/Editで重複していたTEAM NAME/RANK REQUIREMENT/CHARACTER REQUIREMENTS/
// RECRUITMENT MESSAGEのフォームUIのみを共通化したpresentational/form component。
// API通信・router操作・authStore操作・owner判定・Team取得・createTeam/updateTeam呼び出しは
// 一切行わず、すべて呼び出し元のView(TeamCreateView/TeamEditView)の責務のまま維持する。
const props = withDefaults(
  defineProps<{
    initialValues?: TeamFormValues
    characters: Character[]
    submitting: boolean
    submitLabel: string
    submittingLabel: string
    error?: string
  }>(),
  {
    initialValues: () => ({
      name: '',
      rankRequirement: null,
      characterRequirements: null,
      recruitmentMessage: null,
    }),
    error: '',
  },
)

const emit = defineEmits<{
  submit: [values: TeamFormValues]
  cancel: []
}>()

const name = ref(props.initialValues.name)
const rankRequirement = ref(props.initialValues.rankRequirement ?? '')
const recruitmentMessage = ref(props.initialValues.recruitmentMessage ?? '')
const selectedCharacterIds = ref<number[]>(props.initialValues.characterRequirements ?? [])

function handleSubmit() {
  emit('submit', {
    name: name.value,
    rankRequirement: rankRequirement.value === '' ? null : rankRequirement.value,
    characterRequirements:
      selectedCharacterIds.value.length > 0 ? selectedCharacterIds.value : null,
    recruitmentMessage: recruitmentMessage.value.trim() === '' ? null : recruitmentMessage.value,
  })
}
</script>

<template>
  <form class="team-form" @submit.prevent="handleSubmit">
    <BaseCard class="team-form__card">
      <div class="team-form__field">
        <label class="team-form__label" for="team-form-name">TEAM NAME</label>
        <input id="team-form-name" v-model="name" type="text" required maxlength="255" />
      </div>

      <div class="team-form__field">
        <label class="team-form__label" for="team-form-rank">RANK REQUIREMENT</label>
        <select id="team-form-rank" v-model="rankRequirement">
          <option value="">指定なし</option>
          <option v-for="rank in RANK_OPTIONS" :key="rank.value" :value="rank.value">
            {{ rank.label }}
          </option>
        </select>
      </div>

      <div class="team-form__field">
        <span class="team-form__label">CHARACTER REQUIREMENTS</span>
        <div v-if="characters.length > 0" class="team-form__character-grid">
          <label
            v-for="character in characters"
            :key="character.id"
            class="team-form__character-option"
          >
            <input type="checkbox" :value="character.id" v-model="selectedCharacterIds" />
            {{ character.name }}
          </label>
        </div>
        <p v-else class="team-form__character-empty">キャラクター条件を指定できません</p>
      </div>

      <div class="team-form__field">
        <label class="team-form__label" for="team-form-message">RECRUITMENT MESSAGE</label>
        <textarea id="team-form-message" v-model="recruitmentMessage" rows="4"></textarea>
      </div>

      <p v-if="error" class="team-form__error" role="alert">{{ error }}</p>

      <div class="team-form__actions">
        <BaseButton type="submit" :disabled="submitting">
          {{ submitting ? submittingLabel : submitLabel }}
        </BaseButton>
        <BaseButton
          type="button"
          variant="secondary"
          :disabled="submitting"
          @click="emit('cancel')"
        >
          CANCEL
        </BaseButton>
      </div>
    </BaseCard>
  </form>
</template>

<style scoped>
.team-form {
  width: 100%;
  max-width: 640px;
  margin: 0 auto;
}

.team-form__card {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.team-form__field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.team-form__label {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.team-form input[type='text'],
.team-form select,
.team-form textarea {
  width: 100%;
  font-family: inherit;
  font-size: 0.9rem;
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg);
  color: var(--color-text);
}

.team-form textarea {
  resize: vertical;
}

.team-form__character-grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
}

.team-form__character-option {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: 0.85rem;
  text-transform: none;
  letter-spacing: normal;
}

.team-form__character-empty {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 0.85rem;
}

.team-form__error {
  margin: 0;
  color: var(--color-error);
  font-size: 0.85rem;
}

.team-form__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-2);
}

.team-form__actions > * {
  flex: 1 1 auto;
}

@media (min-width: 480px) {
  .team-form__actions > * {
    flex: 0 1 auto;
  }
}
</style>
