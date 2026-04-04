import { svelte } from '@sveltejs/vite-plugin-svelte';

export default {
  plugins: [svelte()],
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
}
