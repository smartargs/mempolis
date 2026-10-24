import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { defineConfig } from 'vite';

const privatePack = resolve(__dirname, '../../../packages/art/src/index.ts');
const artPack = process.env.ART_PACK ? resolve(process.env.ART_PACK) : existsSync(privatePack) ? privatePack : resolve(__dirname, '../../packages/reference-art/src/index.ts');

export default defineConfig({
  base: './',
  resolve: { alias: { 'virtual:art-pack': artPack } },
  server: { port: 5199, fs: { allow: [resolve(__dirname, '../../..')] } },
});
