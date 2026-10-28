import {appendFile, mkdir, readFile, writeFile} from 'node:fs/promises';
import path from 'node:path';

const [inputPath, outputPath] = process.argv.slice(2);
if (!inputPath || !outputPath) throw new Error('Usage: render-audit-report.mjs <audit.json> <report.md>');

const audit = JSON.parse(await readFile(inputPath, 'utf8'));
const vulnerabilities = audit.metadata?.vulnerabilities ?? {};
const counts = Object.fromEntries(
  ['info', 'low', 'moderate', 'high', 'critical', 'total'].map((severity) => [severity, Number(vulnerabilities[severity] ?? 0)]),
);
const blocking = counts.high + counts.critical;
const status = blocking > 0 ? 'FAIL' : 'PASS';
const generatedAt = new Date().toISOString();
const runUrl = process.env.GITHUB_SERVER_URL && process.env.GITHUB_REPOSITORY && process.env.GITHUB_RUN_ID
  ? `${process.env.GITHUB_SERVER_URL}/${process.env.GITHUB_REPOSITORY}/actions/runs/${process.env.GITHUB_RUN_ID}`
  : 'local';

const report = `# npm Audit Security Report

- Status: **${status}**
- Generated: ${generatedAt}
- Run: ${runUrl}
- Policy: high 또는 critical 취약점이 1건 이상이면 실패

| Severity | Count |
|---|---:|
| Info | ${counts.info} |
| Low | ${counts.low} |
| Moderate | ${counts.moderate} |
| High | ${counts.high} |
| Critical | ${counts.critical} |
| Total | ${counts.total} |
`;

await mkdir(path.dirname(outputPath), {recursive: true});
await writeFile(outputPath, report, 'utf8');

if (process.env.GITHUB_OUTPUT) {
  await appendFile(process.env.GITHUB_OUTPUT, `issue_required=${blocking > 0}\nhigh=${counts.high}\ncritical=${counts.critical}\ntotal=${counts.total}\n`);
}
