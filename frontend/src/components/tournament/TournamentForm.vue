<script setup lang="ts">
import { ref } from 'vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import type { TournamentFormValues } from '@/types/tournament'

// Create/Edit(ADMIN専用)で重複するフォームUIのみを共通化したpresentational/form component
// (TeamForm.vueと同じ方針)。API通信・router操作・権限判定は一切行わず、
// すべて呼び出し元のView(AdminTournamentCreateView/AdminTournamentEditView)の責務のまま維持する。
const props = withDefaults(
  defineProps<{
    initialValues?: TournamentFormValues
    submitting: boolean
    submitLabel: string
    submittingLabel: string
    error?: string
  }>(),
  {
    initialValues: () => ({
      name: '',
      teamSize: 0,
      maxPlayers: 0,
      startAt: '',
      recruitmentDeadline: '',
    }),
    error: '',
  },
)

const emit = defineEmits<{
  submit: [values: TournamentFormValues]
  cancel: []
}>()

const name = ref(props.initialValues.name)
// teamSize/maxPlayersはProfileEditForm.vueのMR入力と同じ方針で、一旦文字列の
// refで保持し、送信直前にNumber()へ変換して検証する(空文字判定・NaN判定をしやすくするため)。
const teamSizeInput = ref(props.initialValues.teamSize > 0 ? String(props.initialValues.teamSize) : '')
const maxPlayersInput = ref(
  props.initialValues.maxPlayers > 0 ? String(props.initialValues.maxPlayers) : '',
)
// startAt/recruitmentDeadlineは常にdatetime-local用の秒無し文字列("yyyy-MM-ddTHH:mm")で保持する。
const startAt = ref(props.initialValues.startAt)
const recruitmentDeadline = ref(props.initialValues.recruitmentDeadline)

const validationError = ref('')

// datetime-local(<input>)のvalueは秒を含まない("yyyy-MM-ddTHH:mm")が、
// backendのLocalDateTimeとの整合のため送信時は秒を補う("yyyy-MM-ddTHH:mm:ss")。
// Dateオブジェクトへの変換は行わない(UTC変換による意図しない時刻のずれを防ぐため、
// 文字列操作のみで行う。formatDateTime.tsと同じ方針)。
function toApiDateTime(datetimeLocalValue: string): string {
  return datetimeLocalValue.length === 16 ? `${datetimeLocalValue}:00` : datetimeLocalValue
}

function handleSubmit() {
  validationError.value = ''

  const trimmedName = name.value.trim()
  if (trimmedName === '') {
    validationError.value = '大会名を入力してください'
    return
  }

  const teamSize = Number(teamSizeInput.value)
  if (teamSizeInput.value.trim() === '' || !Number.isInteger(teamSize) || teamSize <= 0) {
    validationError.value = 'チームサイズは1以上の整数で入力してください'
    return
  }

  const maxPlayers = Number(maxPlayersInput.value)
  if (maxPlayersInput.value.trim() === '' || !Number.isInteger(maxPlayers) || maxPlayers <= 0) {
    validationError.value = '最大参加人数は1以上の整数で入力してください'
    return
  }

  if (startAt.value === '') {
    validationError.value = '開始日時を入力してください'
    return
  }
  if (recruitmentDeadline.value === '') {
    validationError.value = '募集締切日時を入力してください'
    return
  }

  const startAtApi = toApiDateTime(startAt.value)
  const recruitmentDeadlineApi = toApiDateTime(recruitmentDeadline.value)

  // backendの仕様(recruitmentDeadline < startAt、同時刻も不可)と同じ境界をフロントでも検証する。
  // 文字列はISO 8601のyyyy-MM-ddTHH:mm:ss形式で、辞書順の比較がそのまま時系列の比較になるため
  // Dateへ変換せず文字列比較のみで判定する。
  if (!(recruitmentDeadlineApi < startAtApi)) {
    validationError.value = '募集締切日時は開始日時より前に設定してください'
    return
  }

  emit('submit', {
    name: trimmedName,
    teamSize,
    maxPlayers,
    startAt: startAtApi,
    recruitmentDeadline: recruitmentDeadlineApi,
  })
}
</script>

<template>
  <form class="tournament-form" @submit.prevent="handleSubmit">
    <BaseCard class="tournament-form__card">
      <div class="tournament-form__field">
        <label class="tournament-form__label" for="tournament-form-name">TOURNAMENT NAME</label>
        <input id="tournament-form-name" v-model="name" type="text" required maxlength="255" />
      </div>

      <div class="tournament-form__row">
        <div class="tournament-form__field">
          <label class="tournament-form__label" for="tournament-form-team-size">TEAM SIZE</label>
          <input
            id="tournament-form-team-size"
            v-model="teamSizeInput"
            type="text"
            inputmode="numeric"
          />
        </div>
        <div class="tournament-form__field">
          <label class="tournament-form__label" for="tournament-form-max-players"
            >MAX PLAYERS</label
          >
          <input
            id="tournament-form-max-players"
            v-model="maxPlayersInput"
            type="text"
            inputmode="numeric"
          />
        </div>
      </div>

      <div class="tournament-form__row">
        <div class="tournament-form__field">
          <label class="tournament-form__label" for="tournament-form-start-at">START AT</label>
          <input id="tournament-form-start-at" v-model="startAt" type="datetime-local" />
        </div>
        <div class="tournament-form__field">
          <label class="tournament-form__label" for="tournament-form-deadline"
            >RECRUITMENT DEADLINE</label
          >
          <input
            id="tournament-form-deadline"
            v-model="recruitmentDeadline"
            type="datetime-local"
          />
        </div>
      </div>

      <p v-if="validationError || error" class="tournament-form__error" role="alert">
        {{ validationError || error }}
      </p>

      <div class="tournament-form__actions">
        <BaseButton type="submit" :disabled="submitting">
          {{ submitting ? submittingLabel : submitLabel }}
        </BaseButton>
        <BaseButton type="button" variant="secondary" :disabled="submitting" @click="emit('cancel')">
          CANCEL
        </BaseButton>
      </div>
    </BaseCard>
  </form>
</template>

<style scoped>
.tournament-form__card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.tournament-form__row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
}

.tournament-form__row .tournament-form__field {
  flex: 1 1 220px;
}

.tournament-form__field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.tournament-form__label {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.tournament-form input[type='text'],
.tournament-form input[type='datetime-local'] {
  width: 100%;
  font-family: inherit;
  font-size: 0.9rem;
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg);
  color: var(--color-text);
}

.tournament-form__error {
  margin: 0;
  color: var(--color-error);
  font-size: 0.85rem;
}

.tournament-form__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}
</style>
