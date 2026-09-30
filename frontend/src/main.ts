import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { initTheme } from './composables/useTheme'

// localStorageに保存済みのTheme(無ければLight)を、初回描画前に適用する。
initTheme()

const app = createApp(App)

app.use(createPinia())
app.use(router)

// localStorageに保存済みのJWTがあれば、ルーティング開始前に認証状態を復元する。
const authStore = useAuthStore()
authStore.restore().finally(() => {
  app.mount('#app')
})
