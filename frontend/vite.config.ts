import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    // Os testes de ponta a ponta, em `e2e`, são do Playwright.
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
