<script setup lang="ts">
import BaseCard from '@/components/ui/BaseCard.vue'

const features = [
  { title: 'Find Tournaments', description: '参加したい大会を探し、詳細を確認できる。' },
  { title: 'Create / Find Teams', description: '大会ごとにチームを作成する、または募集中のチームを探せる。' },
  { title: 'Recruitment Applications', description: 'チームへ参加申請を送り、Ownerが承認/拒否を管理できる。' },
  { title: 'Player Profiles', description: '使用キャラクターやRank/MRを公開プロフィールとして共有できる。' },
]

const techStack = ['Vue 3', 'TypeScript', 'Java 21', 'Spring Boot', 'PostgreSQL']
</script>

<template>
  <main class="about-view">
    <div class="container">
      <header class="about-view__header">
        <h1>ABOUT FIGHTER HUB</h1>
        <p>
          格闘ゲーム大会で、チームを作る・チームを探す・メンバーを募集するためのWeb
          Application。
        </p>
      </header>

      <section class="about-view__section">
        <h2 class="about-view__section-heading">WHAT YOU CAN DO</h2>
        <div class="about-view__feature-grid">
          <BaseCard
            v-for="feature in features"
            :key="feature.title"
            class="about-view__feature-card"
          >
            <h3 class="about-view__feature-title">{{ feature.title }}</h3>
            <p class="about-view__feature-description">{{ feature.description }}</p>
          </BaseCard>
        </div>
      </section>

      <section class="about-view__section">
        <h2 class="about-view__section-heading">TECH STACK</h2>
        <BaseCard class="about-view__tech-card">
          <ul class="about-view__tech-list">
            <li v-for="tech in techStack" :key="tech">{{ tech }}</li>
          </ul>
        </BaseCard>
      </section>
    </div>
  </main>
</template>

<style scoped>
.about-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.about-view__header {
  max-width: 640px;
  margin: 0 auto var(--space-8);
  text-align: center;
}

.about-view__header p {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
}

.about-view__section {
  margin-bottom: var(--space-8);
}

.about-view__section-heading {
  margin: 0 0 var(--space-4);
  font-size: 0.9rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary);
  text-align: center;
}

.about-view__feature-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-4);
}

@media (min-width: 768px) {
  .about-view__feature-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.about-view__feature-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.about-view__feature-title {
  margin: 0;
}

.about-view__feature-description {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

.about-view__tech-card {
  max-width: 480px;
  margin: 0 auto;
}

.about-view__tech-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
  justify-content: center;
}

.about-view__tech-list li {
  padding: var(--space-1) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}
</style>
