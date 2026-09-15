import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const projectRoot = fileURLToPath(new URL('.', import.meta.url))

// https://vite.dev/config/
export default defineConfig({
  plugins: [svelte()],
  build: {
    rollupOptions: {
      input: {
        main: resolve(projectRoot, 'index.html'),
        access: resolve(projectRoot, 'access/index.html'),
        portal: resolve(projectRoot, 'portal/index.html'),
        dashboard: resolve(projectRoot, 'dashboard/index.html'),
        privacyPolicy: resolve(projectRoot, 'privacy-policy/index.html'),
      },
    },
  },
})
