import {cp, mkdir, rm} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = path.join(root, 'dist');
const assets = ['index.html', 'style.css', 'app.js', 'api.js'];

await rm(output, {recursive: true, force: true});
await mkdir(output, {recursive: true});
await Promise.all(assets.map((asset) => cp(path.join(root, asset), path.join(output, asset))));

console.log(`Built ${assets.length} public assets into dist/.`);
