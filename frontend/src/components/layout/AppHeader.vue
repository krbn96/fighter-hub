<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'

const authStore = useAuthStore()
const router = useRouter()
const { theme, toggleTheme } = useTheme()

const menuOpen = ref(false)

function closeMenu() {
  menuOpen.value = false
}

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function handleLogout() {
  closeMenu()
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__bar container">
      <RouterLink to="/" class="app-header__logo" @click="closeMenu">FIGHTER HUB</RouterLink>

      <button
        type="button"
        class="app-header__toggle"
        :aria-expanded="menuOpen"
        aria-label="メニューを開閉する"
        @click="toggleMenu"
      >
        <span></span>
        <span></span>
        <span></span>
      </button>

      <nav class="app-header__nav" :class="{ 'app-header__nav--open': menuOpen }">
        <RouterLink to="/tournaments" class="app-header__link" @click="closeMenu"
          >Tournaments</RouterLink
        >
        <RouterLink
          v-if="authStore.isAuthenticated"
          to="/teams/my"
          class="app-header__link"
          @click="closeMenu"
          >My Teams</RouterLink
        >

        <div class="app-header__auth">
          <RouterLink v-if="!authStore.isAuthenticated" to="/login" class="app-header__link"
            >Login</RouterLink
          >
          <template v-else>
            <RouterLink to="/mypage" class="app-header__link" @click="closeMenu"
              >My Page</RouterLink
            >
            <button type="button" class="app-header__logout" @click="handleLogout">Logout</button>
          </template>

          <button
            type="button"
            class="app-header__theme-toggle"
            :aria-label="
              theme === 'light' ? 'ダークテーマに切り替える' : 'ライトテーマに切り替える'
            "
            :title="theme === 'light' ? 'ダークテーマに切り替える' : 'ライトテーマに切り替える'"
            @click="toggleTheme"
          >
            <svg
              v-if="theme === 'light'"
              viewBox="0 0 24 24"
              width="18"
              height="18"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79Z" />
            </svg>
            <svg
              v-else
              viewBox="0 0 24 24"
              width="18"
              height="18"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <circle cx="12" cy="12" r="4" />
              <path
                d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"
              />
            </svg>
          </button>
        </div>
      </nav>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.app-header__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  min-height: 64px;
}

.app-header__logo {
  color: var(--color-text);
  font-weight: 800;
  font-size: 1.1rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.app-header__logo:hover {
  color: var(--color-primary);
}

.app-header__toggle {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  width: 32px;
  height: 32px;
  padding: 0;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.app-header__toggle span {
  display: block;
  height: 2px;
  background: var(--color-text);
  border-radius: 1px;
}

.app-header__nav {
  display: flex;
  align-items: center;
  gap: var(--space-6);
}

.app-header__auth {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.app-header__link {
  color: var(--color-text-secondary);
  font-weight: 600;
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.app-header__link:hover,
.app-header__link.router-link-active {
  color: var(--color-text);
}

.app-header__logout {
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  font-family: inherit;
  font-weight: 600;
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition:
    color var(--transition-fast),
    border-color var(--transition-fast);
}

.app-header__logout:hover {
  color: var(--color-text);
  border-color: var(--color-text-secondary);
}

.app-header__theme-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition:
    color var(--transition-fast),
    border-color var(--transition-fast);
}

.app-header__theme-toggle:hover {
  color: var(--color-text);
  border-color: var(--color-text-secondary);
}

@media (max-width: 767px) {
  .app-header__toggle {
    display: flex;
  }

  .app-header__nav {
    position: absolute;
    top: 64px;
    left: 0;
    right: 0;
    flex-direction: column;
    align-items: stretch;
    gap: 0;
    background: var(--color-surface);
    border-bottom: 1px solid var(--color-border);
    max-height: 0;
    overflow: hidden;
    transition: max-height var(--transition-base);
  }

  .app-header__nav--open {
    max-height: 360px;
  }

  .app-header__auth {
    flex-direction: column;
    align-items: stretch;
    gap: 0;
  }

  .app-header__link,
  .app-header__logout {
    width: 100%;
    padding: var(--space-3) var(--space-4);
    text-align: left;
    border-radius: 0;
    border: 0;
    border-top: 1px solid var(--color-border);
  }

  .app-header__theme-toggle {
    align-self: flex-start;
    margin: var(--space-3) var(--space-4);
  }
}

@media (min-width: 768px) {
  .app-header__toggle {
    display: none;
  }
}
</style>
