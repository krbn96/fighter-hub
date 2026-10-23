<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import DiscordIcon from '@/components/ui/DiscordIcon.vue'
import { getMrColorVar, getRankColorVar } from '@/utils/rankColor'
import type { UserMe, UserPublic } from '@/types/user'

// API通信は行わないpresentational component。
// characterNamesは呼び出し元がGET /api/charactersを1回取得して作ったMap(characterId -> name)を渡す前提。
// showDiscordActions: MY PAGE(自分自身のプロフィール)としての表示かどうかを呼び出し元が
// 判別して渡す。trueの場合のみDiscordの連携/連携解除ボタンを表示する(公開プロフィールでは
// 常にfalseで、表示のみになる)。OAuth API通信はこのコンポーネントへ持ち込まず、
// discordLink/discordUnlinkイベントをemitして呼び出し元(MyPageView)へ委譲する。
// discordMessage/discordMessageTypeもMY PAGE専用(showDiscordActions=falseの場合は
// 呼び出し元が渡さない前提。公開プロフィールにはメッセージ表示機能を持ち込まない)。
const props = withDefaults(
  defineProps<{
    user: UserMe | UserPublic
    characterNames: Map<number, string>
    showDiscordActions?: boolean
    discordUnlinking?: boolean
    discordMessage?: string
    discordMessageType?: 'success' | 'error'
  }>(),
  {
    showDiscordActions: false,
    discordUnlinking: false,
    discordMessage: '',
    discordMessageType: 'success',
  },
)

const emit = defineEmits<{
  discordLink: []
  discordUnlink: []
}>()

// backendのLocalTimeは"HH:mm:ss"形式の文字列で届くため、表示用に"HH:mm"へ整形する。
function formatTime(time: string): string {
  return time.slice(0, 5)
}

const playTimeText = computed(() => {
  const { playTimeStart, playTimeEnd } = props.user

  if (playTimeStart && playTimeEnd) {
    return `${formatTime(playTimeStart)} - ${formatTime(playTimeEnd)}`
  }
  if (playTimeStart) {
    return formatTime(playTimeStart)
  }
  if (playTimeEnd) {
    return formatTime(playTimeEnd)
  }
  return 'プレイ時間は未設定です'
})

function characterName(characterId: number): string {
  return props.characterNames.get(characterId) ?? '不明なキャラクター'
}

// Discord usernameクリックコピー。コピーするのは公開用discordUsernameのみで、
// discordId(内部識別子)はこのコンポーネント/Frontendのどこにも保持していない。
// Discord OAuthのsuccess/error message(discordMessage prop)とは別物であり、
// ページ全体のmessage領域は使わず、username付近に小さく一時表示する。
const showCopyFeedback = ref(false)
let copyFeedbackTimer: ReturnType<typeof setTimeout> | undefined

async function copyDiscordUsername() {
  const username = props.user.discordUsername
  if (!username) {
    return
  }

  try {
    await navigator.clipboard.writeText(username)
  } catch {
    // コピーに失敗した場合は「コピーしました」を表示しない。機密情報ではないが、
    // 詳細なエラー内容をconsoleへ出力する必要も無いため何もしない。
    return
  }

  // 連続クリック時は「最後に成功したコピーから約2秒」表示されるようtimerをリセットする。
  if (copyFeedbackTimer !== undefined) {
    clearTimeout(copyFeedbackTimer)
  }
  showCopyFeedback.value = true
  copyFeedbackTimer = setTimeout(() => {
    showCopyFeedback.value = false
    copyFeedbackTimer = undefined
  }, 2000)
}

onUnmounted(() => {
  if (copyFeedbackTimer !== undefined) {
    clearTimeout(copyFeedbackTimer)
  }
})

// FIGHTER HUBの仕様上MRはrank===MASTERの場合のみ意味を持つ。
// 既存DBにMASTER以外でmrが設定された不整合データが存在してもここでは表示しない。

// backendにslot/順序を表すフィールドは無いため、user.charactersの配列順(登録順)を
// そのままMAIN CHARACTER(先頭1件) / SUB CHARACTERS(残り)の判定に利用する。
// 新しいslotフィールド等は追加しない。
const mainCharacter = computed(() => props.user.characters[0])
const subCharacters = computed(() => props.user.characters.slice(1))
</script>

<template>
  <div class="player-profile">
    <BaseCard class="player-profile__summary">
      <h2 class="player-profile__name">{{ user.name }}</h2>

      <!-- discordUsername==nullかつ公開プロフィール(showDiscordActions=false)の場合は
           Discord欄自体を表示しない(内部識別子であるdiscordIdはbackendが公開しないため、
           Frontend側にも一切保持・表示しない)。MY PAGE(showDiscordActions=true)では
           未連携でも「Discordと連携」導線として常にDiscord行を表示する。 -->
      <div v-if="user.discordUsername" class="player-profile__discord">
        <DiscordIcon connected class="player-profile__discord-icon" />
        <span class="player-profile__discord-username-wrapper">
          <button
            type="button"
            class="player-profile__discord-username"
            @click="copyDiscordUsername"
          >
            {{ user.discordUsername }}
          </button>
          <span v-if="showCopyFeedback" class="player-profile__discord-copy-feedback" role="status">
            コピーしました
          </span>
        </span>
        <BaseButton
          v-if="showDiscordActions"
          type="button"
          variant="secondary"
          class="player-profile__discord-button"
          :disabled="discordUnlinking"
          @click="emit('discordUnlink')"
        >
          {{ discordUnlinking ? '解除中...' : '連携解除' }}
        </BaseButton>
        <span
          v-if="showDiscordActions && discordMessage"
          class="player-profile__discord-message"
          :class="`player-profile__discord-message--${discordMessageType}`"
          :role="discordMessageType === 'error' ? 'alert' : undefined"
        >
          {{ discordMessage }}
        </span>
      </div>
      <div v-else-if="showDiscordActions" class="player-profile__discord">
        <DiscordIcon class="player-profile__discord-icon" />
        <BaseButton
          type="button"
          variant="secondary"
          class="player-profile__discord-button"
          @click="emit('discordLink')"
        >
          Discordと連携
        </BaseButton>
        <span
          v-if="discordMessage"
          class="player-profile__discord-message"
          :class="`player-profile__discord-message--${discordMessageType}`"
          :role="discordMessageType === 'error' ? 'alert' : undefined"
        >
          {{ discordMessage }}
        </span>
      </div>

      <div class="player-profile__field">
        <span class="player-profile__field-label">PLAY TIME</span>
        <p class="player-profile__field-value">{{ playTimeText }}</p>
      </div>

      <div class="player-profile__field">
        <span class="player-profile__field-label">MESSAGE</span>
        <p class="player-profile__field-value player-profile__message">
          {{ user.message || 'メッセージはまだありません' }}
        </p>
      </div>
    </BaseCard>

    <section class="player-profile__characters">
      <h3 class="player-profile__section-heading">CHARACTERS</h3>

      <template v-if="mainCharacter">
        <div class="player-profile__main-character">
          <span class="player-profile__group-label">MAIN CHARACTER</span>
          <BaseCard class="player-profile__main-character-card">
            <p class="player-profile__character-name player-profile__character-name--main">
              {{ characterName(mainCharacter.characterId) }}
            </p>
            <div class="player-profile__main-character-stats">
              <span
                class="player-profile__character-rank player-profile__character-rank--main"
                :style="{ color: getRankColorVar(mainCharacter.rank) }"
                >{{ mainCharacter.rank }}</span
              >
              <span
                v-if="mainCharacter.rank === 'MASTER' && mainCharacter.mr !== null"
                class="player-profile__character-mr player-profile__character-mr--main"
                :style="{ color: getMrColorVar(mainCharacter.mr) }"
              >
                MR {{ mainCharacter.mr }}
              </span>
            </div>
          </BaseCard>
        </div>

        <div v-if="subCharacters.length > 0" class="player-profile__sub-characters">
          <span class="player-profile__group-label">SUB CHARACTERS</span>
          <div class="player-profile__sub-character-grid">
            <BaseCard
              v-for="character in subCharacters"
              :key="character.characterId"
              class="player-profile__sub-character-card"
            >
              <p class="player-profile__character-name player-profile__character-name--sub">{{
                characterName(character.characterId)
              }}</p>
              <div class="player-profile__sub-character-stats">
                <span
                  class="player-profile__character-rank player-profile__character-rank--sub"
                  :style="{ color: getRankColorVar(character.rank) }"
                  >{{ character.rank }}</span
                >
                <span
                  v-if="character.rank === 'MASTER' && character.mr !== null"
                  class="player-profile__character-mr player-profile__character-mr--sub"
                  :style="{ color: getMrColorVar(character.mr) }"
                >
                  MR {{ character.mr }}
                </span>
              </div>
            </BaseCard>
          </div>
        </div>
      </template>
      <p v-else class="player-profile__no-characters">使用キャラクターが登録されていません</p>
    </section>
  </div>
</template>

<style scoped>
.player-profile {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.player-profile__summary {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.player-profile__name {
  margin: 0;
}

.player-profile__discord {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1);
  /* Discord公式ブランドカラー(Blurple)。DiscordIconと同じ値。 */
  color: #5865f2;
  font-size: 0.85rem;
  font-weight: 600;
}

.player-profile__discord-icon {
  width: 1.2em;
  height: 0.95em;
}

/* positionの基準にするだけのinline wrapper。レイアウトへの影響は無い。 */
.player-profile__discord-username-wrapper {
  position: relative;
  display: inline-flex;
}

/* button要素だがusernameの見た目はそのまま維持し、大きなボタンに見えないようにする
   (既定のbutton装飾をすべて打ち消し、親から色・フォントを継承する)。 */
.player-profile__discord-username {
  background: none;
  border: none;
  padding: 0;
  margin: 0;
  font: inherit;
  font-weight: 700;
  color: inherit;
  cursor: pointer;
  border-radius: var(--radius-sm);
}

.player-profile__discord-username:hover,
.player-profile__discord-username:focus-visible {
  text-decoration: underline;
}

.player-profile__discord-username:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 2px;
}

/* 「コピーしました」の小さな吹き出し。absolute positioningでusername/連携解除ボタンを
   押し出さないようにする。--color-text/--color-bgを反転利用し、light/dark両themeで
   それぞれ十分なコントラストの吹き出し背景色になる。 */
.player-profile__discord-copy-feedback {
  position: absolute;
  bottom: calc(100% + var(--space-1));
  left: 50%;
  transform: translateX(-50%);
  padding: var(--space-1) var(--space-2);
  background: var(--color-text);
  color: var(--color-bg);
  font-size: 0.7rem;
  font-weight: 600;
  white-space: nowrap;
  border-radius: var(--radius-sm);
  z-index: 1;
  pointer-events: none;
}

.player-profile__discord-button {
  margin-left: var(--space-2);
  font-size: 0.75rem;
  padding: var(--space-1) var(--space-3);
}

/* 操作ボタンの右側に表示するDiscord連携/解除の結果メッセージ。success/errorとも
   同じ位置を使い、狭い画面や長い文言では折り返す(flex-wrapにより自然に次の行へ送られる)。 */
.player-profile__discord-message {
  margin-left: var(--space-2);
  font-size: 0.8rem;
  font-weight: 400;
  white-space: normal;
}

.player-profile__discord-message--success {
  color: var(--color-success);
}

.player-profile__discord-message--error {
  color: var(--color-error);
}

.player-profile__field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.player-profile__field-label {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.player-profile__field-value {
  margin: 0;
}

.player-profile__message {
  white-space: pre-wrap;
}

.player-profile__section-heading {
  margin: 0 0 var(--space-3);
  font-size: 0.9rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary);
}

.player-profile__group-label {
  display: block;
  margin-bottom: var(--space-2);
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.player-profile__main-character {
  margin-bottom: var(--space-4);
}

.player-profile__main-character-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.player-profile__main-character-stats,
.player-profile__sub-character-stats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.player-profile__character-name {
  margin: 0;
  font-weight: 700;
  /* 長いキャラクター名でも狭い画面でrank/MRの行と重ならないよう折り返す。 */
  overflow-wrap: break-word;
}

.player-profile__character-name--main {
  font-size: 1.75rem;
  font-weight: 800;
}

.player-profile__character-name--sub {
  font-size: 1rem;
}

/* ランク別の色(文字色)はrankColor.ts(getRankColorVar/getMrColorVar)がinline styleで
   設定する。ここではStatusBadgeと同じoutlined pillスタイル(背景は塗らず、文字色と
   枠線(currentColor、つまりrank別の色と同じ)のみ)の形だけを定義する。 */
.player-profile__character-rank {
  display: inline-flex;
  align-items: center;
  margin: 0;
  padding: 0.15rem 0.6rem;
  border: 1px solid currentColor;
  border-radius: 999px;
  font-weight: 700;
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.player-profile__character-rank--main {
  font-size: 0.85rem;
}

.player-profile__character-mr {
  margin: 0;
  font-weight: 700;
  font-size: 0.8rem;
  white-space: nowrap;
}

.player-profile__character-mr--main {
  font-size: 1.1rem;
}

/* 3カラム時に1カラムぶんの幅を占めるよう、常にgrid-template-columns: repeat(3, 1fr)を基準にする
   (auto-fill/auto-fitは使わない)。登録数が3未満でも各カードの幅は1カラムぶんのまま変わらない。 */
.player-profile__sub-character-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-3);
}

.player-profile__sub-character-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.player-profile__no-characters {
  color: var(--color-text-secondary);
}

@media (min-width: 768px) {
  .player-profile__sub-character-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .player-profile__sub-character-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
