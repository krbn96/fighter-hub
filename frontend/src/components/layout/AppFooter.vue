<script setup lang="ts"></script>

<template>
  <footer class="app-footer">
    <div class="app-footer__inner container">
      <div class="app-footer__brand">
        <p class="app-footer__title">FIGHTER HUB</p>
        <p class="app-footer__tagline">Find teammates. Join tournaments. Fight together.</p>
      </div>
      <div class="app-footer__meta">
        <a
          href="https://github.com/krbn96/fighter-hub"
          target="_blank"
          rel="noopener"
          class="app-footer__github"
          aria-label="GitHub"
          title="GitHub"
        >
          <svg viewBox="0 0 16 16" width="20" height="20" fill="currentColor" aria-hidden="true">
            <path
              d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0 0 16 8c0-4.42-3.58-8-8-8Z"
            />
          </svg>
        </a>
        <span class="app-footer__copyright">&copy; 2026 FIGHTER HUB</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  border-top: 1px solid var(--color-border);
  background: var(--color-surface);
  flex-shrink: 0;
}

.app-footer__inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding-top: var(--space-6);
  padding-bottom: var(--space-6);
}

.app-footer__title {
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  font-size: 0.95rem;
}

.app-footer__tagline {
  color: var(--color-text-secondary);
  font-size: 0.85rem;
  margin-top: var(--space-1);
}

.app-footer__meta {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  color: var(--color-text-secondary);
  font-size: 0.85rem;
}

.app-footer__github {
  display: inline-flex;
  color: var(--color-text-secondary);
  transition: color var(--transition-fast);
}

.app-footer__github:hover {
  color: var(--color-primary);
}

.app-footer__copyright {
  color: var(--color-text-secondary);
}
</style>
