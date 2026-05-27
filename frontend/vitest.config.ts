import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    reporters: process.env['CI']
      ? ['verbose', ['junit', { outputFile: 'test-results/junit.xml' }]]
      : ['verbose'],
    coverage: {
      reporter: ['text-summary', 'lcov', 'json', 'html'],
      exclude: [
        'src/app/**/*.spec.ts',
        'src/app/**/*.routes.ts',
        'src/app/**/*.config.ts',
      ],
    },
  },
});
