<script setup lang="ts">
import { computed } from 'vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import type { UserMe, UserPublic } from '@/types/user'

// API通信は行わないpresentational component。
// characterNamesは呼び出し元がGET /api/charactersを1回取得して作ったMap(characterId -> name)を渡す前提。
const props = defineProps<{
  user: UserMe | UserPublic
  characterNames: Map<number, string>
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
            <!-- 将来Characterへ画像(imageUrl等)が追加された場合、ここを<img>へ差し替える想定のvisual領域。
                 現時点ではAPIに画像情報が無いため、CSSのみのfallback visualを表示する(fake imageは使用しない)。 -->
            <div class="player-profile__main-character-visual" aria-hidden="true">
              <span class="player-profile__main-character-visual-fallback">{{
                characterName(mainCharacter.characterId).charAt(0)
              }}</span>
            </div>
            <div class="player-profile__main-character-body">
              <p class="player-profile__character-name player-profile__character-name--main">
                {{ characterName(mainCharacter.characterId) }}
              </p>
              <p class="player-profile__character-rank">{{ mainCharacter.rank }}</p>
              <p
                v-if="mainCharacter.rank === 'MASTER' && mainCharacter.mr !== null"
                class="player-profile__character-mr"
              >
                MR {{ mainCharacter.mr }}
              </p>
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
              <div class="player-profile__sub-character-visual" aria-hidden="true">
                <span class="player-profile__sub-character-visual-fallback">{{
                  characterName(character.characterId).charAt(0)
                }}</span>
              </div>
              <div class="player-profile__sub-character-body">
                <p class="player-profile__character-name">{{
                  characterName(character.characterId)
                }}</p>
                <p class="player-profile__character-rank">{{ character.rank }}</p>
                <p
                  v-if="character.rank === 'MASTER' && character.mr !== null"
                  class="player-profile__character-mr"
                >
                  MR {{ character.mr }}
                </p>
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
  gap: var(--space-4);
}

/* BaseCardの既定padding(var(--space-4))ぶんを負のmarginで打ち消し、
   visual areaをカード上部いっぱいに配置する(BaseCard自体のpadding指定は変更しない)。 */
.player-profile__main-character-visual {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 16 / 9;
  margin: calc(-1 * var(--space-4)) calc(-1 * var(--space-4)) 0;
  background: linear-gradient(135deg, var(--color-surface-hover), var(--color-bg));
  border-bottom: 1px solid var(--color-border);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
}

.player-profile__main-character-visual-fallback {
  font-size: 2.5rem;
  font-weight: 800;
  color: var(--color-text-secondary);
  text-transform: uppercase;
}

.player-profile__main-character-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.player-profile__character-name {
  margin: 0;
  font-weight: 700;
}

.player-profile__character-name--main {
  font-size: 1.3rem;
}

.player-profile__character-rank {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 0.85rem;
}

.player-profile__character-mr {
  margin: 0;
  font-size: 0.85rem;
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
  gap: var(--space-4);
}

.player-profile__sub-character-visual {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 16 / 9;
  margin: calc(-1 * var(--space-4)) calc(-1 * var(--space-4)) 0;
  background: linear-gradient(135deg, var(--color-surface-hover), var(--color-bg));
  border-bottom: 1px solid var(--color-border);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
}

.player-profile__sub-character-visual-fallback {
  font-size: 1.5rem;
  font-weight: 800;
  color: var(--color-text-secondary);
  text-transform: uppercase;
}

.player-profile__sub-character-body {
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
  .player-profile__main-character-visual {
    aspect-ratio: 16 / 5.5;
  }

  .player-profile__sub-character-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
