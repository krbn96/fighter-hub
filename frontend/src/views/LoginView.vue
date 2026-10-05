<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

const email = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

// /login?registered=true でアクセスした場合のみ登録成功メッセージを表示する。
// 通常の/loginアクセス(registeredクエリなし)では表示しない。既存のログインエラー表示
// (role="alert"のerror)とは別の要素として扱い、混同しないようにする。
const showRegisteredMessage = computed(() => route.query.registered === 'true')

async function handleSubmit() {
  loading.value = true
  error.value = ''

  try {
    await authStore.login(email.value, password.value)
    router.push('/mypage')
  } catch {
    error.value = 'メールアドレスまたはパスワードが正しくありません'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-view">
    <div class="container">
      <header class="login-view__header">
        <h1>LOGIN</h1>
        <p>チームを作る・探すには、まずログインしてください。</p>
      </header>

      <form class="login-view__form" @submit.prevent="handleSubmit">
        <BaseCard class="login-view__card">
          <p v-if="showRegisteredMessage" class="login-view__success">
            アカウントを作成しました。ログインしてください。
          </p>

          <div class="login-view__field">
            <label class="login-view__label" for="email">EMAIL</label>
            <input id="email" v-model="email" type="email" required autocomplete="email" />
          </div>

          <div class="login-view__field">
            <label class="login-view__label" for="password">PASSWORD</label>
            <input
              id="password"
              v-model="password"
              type="password"
              required
              autocomplete="current-password"
            />
          </div>

          <p v-if="error" class="login-view__error" role="alert">{{ error }}</p>

          <BaseButton type="submit" class="login-view__submit" :disabled="loading">
            {{ loading ? 'LOGGING IN...' : 'LOGIN' }}
          </BaseButton>

          <p class="login-view__register-link">
            Don't have an account? <RouterLink to="/register">CREATE ACCOUNT</RouterLink>
          </p>
        </BaseCard>
      </form>
    </div>
  </main>
</template>

<style scoped>
.login-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.login-view__header {
  max-width: 480px;
  margin: 0 auto var(--space-6);
  text-align: center;
}

.login-view__header p {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
}

.login-view__form {
  width: 100%;
  max-width: 480px;
  margin: 0 auto;
}

.login-view__card {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.login-view__submit {
  margin-top: var(--space-4);
}

.login-view__field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.login-view__label {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.login-view input[type='email'],
.login-view input[type='password'] {
  width: 100%;
  font-family: inherit;
  font-size: 0.9rem;
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg);
  color: var(--color-text);
}

.login-view__error {
  margin: 0;
  color: var(--color-error);
  font-size: 0.85rem;
}

.login-view__success {
  margin: 0;
  color: var(--color-primary);
  font-size: 0.85rem;
}

.login-view__register-link {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  text-align: center;
}
</style>
