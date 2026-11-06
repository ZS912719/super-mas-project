import { copyFile, mkdir } from 'node:fs/promises';
await mkdir('public', { recursive: true });
await Promise.all(['index.html', 'style.css'].map(file => copyFile(`web/${file}`, `public/${file}`)));
