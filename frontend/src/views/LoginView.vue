<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()

const email = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

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
  <main>
    <h1>ログイン</h1>
    <form @submit.prevent="handleSubmit">
      <div>
        <label for="email">メールアドレス</label>
        <input id="email" v-model="email" type="email" required autocomplete="email" />
      </div>
      <div>
        <label for="password">パスワード</label>
        <input
          id="password"
          v-model="password"
          type="password"
          required
          autocomplete="current-password"
        />
      </div>
      <p v-if="error" role="alert">{{ error }}</p>
      <button type="submit" :disabled="loading">
        {{ loading ? 'ログイン中...' : 'ログイン' }}
      </button>
    </form>
  </main>
</template>
