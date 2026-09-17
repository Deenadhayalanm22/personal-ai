import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    baseURL: 'http://localhost:4173',
    trace: 'on',
    screenshot: 'on',
    video: 'on',
    launchOptions: { slowMo: process.env.PW_SLOW_MO ? Number(process.env.PW_SLOW_MO) : 0 }
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    { command: 'cd ../backend && ./scripts/e2e-reset-db.sh && SPRING_PROFILES_ACTIVE=e2e ./mvnw spring-boot:run', url: 'http://localhost:8080/actuator/health', reuseExistingServer: false, timeout: 120000 },
    { command: 'npm run dev -- --host localhost --port 4173', url: 'http://localhost:4173', reuseExistingServer: !process.env.CI }
  ]
});
