import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';

export default defineConfig({
  site: 'https://keyjawn.amditis.tech',
  output: 'static',
  integrations: [sitemap()],
});
