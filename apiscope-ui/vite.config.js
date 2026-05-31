import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  plugins: [react(), tailwindcss()],

  // Must match the Spring Boot static resource path
  base: '/apiscope/',

  build: {
    // Output directly into the starter's static folder so mvn package bundles it
    outDir: path.resolve(
      __dirname,
      '../apiscope-spring-boot-starter/src/main/resources/static/apiscope'
    ),
    emptyOutDir: true,
  },

  server: {
    port: 5173,
    proxy: {
      // Forward API calls to the running Spring Boot app during development
      '/apiscope/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Forward sample-app endpoint calls (tryItApi, warmupApi) to Spring Boot
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
