import { resolve } from 'node:path';
import { defineConfig } from 'vite';

const artPack = process.env.ART_PACK ? resolve(process.env.ART_PACK) : resolve(__dirname, '../../packages/reference-art/src/index.ts');
const allow = [resolve(__dirname, '../..'), ...(process.env.ART_ROOT ? [resolve(process.env.ART_ROOT)] : [])];

export default defineConfig({
  base: './',
  resolve: { alias: { 'virtual:art-pack': artPack } },
  server: { port: 5199, fs: { allow } },
});
