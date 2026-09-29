import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import { fileURLToPath } from 'node:url';
export default defineConfig({ plugins: [svelte()], base: './', resolve: { dedupe: ['svelte'] }, build: { manifest: true, rollupOptions: { input: { app: fileURLToPath(new URL('./index.html', import.meta.url)), auth: fileURLToPath(new URL('./auth.html', import.meta.url)) } } }, server: { fs: { allow: [fileURLToPath(new URL('.', import.meta.url)), fileURLToPath(new URL('../frontend', import.meta.url))] } } });
