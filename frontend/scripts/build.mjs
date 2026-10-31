import {cp, mkdir, rm, writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {runtimeConfigScript} from '../runtime-config.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = path.join(root, 'dist');
const assets = ['index.html', 'style.css', 'app.js', 'api.js', 'feature-flags.js'];

await rm(output, {recursive: true, force: true});
await mkdir(output, {recursive: true});
await Promise.all(assets.map((asset) => cp(path.join(root, asset), path.join(output, asset))));
await writeFile(path.join(output, 'runtime-config.js'), runtimeConfigScript(), 'utf8');

console.log(`Built ${assets.length + 1} public assets into dist/.`);
