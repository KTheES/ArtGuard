import {test, expect} from '@playwright/test';
import {readFileSync, writeFileSync} from 'node:fs';
import {assignVariant, UPLOAD_CTA_EXPERIMENT} from '../feature-flags.js';
const panel = JSON.parse(readFileSync(new URL('../../experiments/week13/personas.json', import.meta.url)));
const enabled = process.env.WEEK13_PANEL === '1';
const output = new URL('../../reports/week13/runtime/', import.meta.url);
const manifest = enabled ? JSON.parse(readFileSync(new URL('manifest.json', output))) : null;
for (const persona of panel.personas) {
  test(`week13 synthetic panel ${persona.id}: ${persona.scenario}`, async ({page}) => {
    test.skip(!enabled, 'Run prepare.mjs and set WEEK13_PANEL=1; ordinary E2E must not create observations.');
    const email = persona.id.toLowerCase() + '@example.invalid';
    const variant = assignVariant(UPLOAD_CTA_EXPERIMENT, email);
    const judgment = manifest.assessment.responses.find(r=>r.persona === persona.id);
    const row = {persona:persona.id, variant, action:judgment.action, runId:manifest.runId,
      observedAt:new Date().toISOString(), exposure:false, click:false, scenarioPassed:false};
    try {
      await page.route('**/api/v1/**', async route => {
        const url = new URL(route.request().url());
        const data = url.pathname.endsWith('/auth/login')
          ? {accessToken:'synthetic-test-only', refreshToken:'synthetic-test-only'} : {items:[], totalPages:0};
        await route.fulfill({json:{success:true, data}});
      });
      if (persona.id === 'P10') await page.setViewportSize({width:390,height:844});
      await page.goto('/');
      await page.getByLabel('이메일').fill(email);
      await page.getByLabel('비밀번호').fill('synthetic-password');
      await page.getByRole('button', {name:'로그인',exact:true}).click();
      const label = UPLOAD_CTA_EXPERIMENT.variants.find(v => v.key === variant).label;
      const cta = page.getByRole('button',{name:label,exact:true});
      await expect(cta).toBeVisible();
      const readEvents = () => page.evaluate(() => JSON.parse(localStorage.getItem('artguard-experiment-events') || '[]'));
      row.exposure = (await readEvents()).some(e=>e.experiment === UPLOAD_CTA_EXPERIMENT.key && e.event === 'exposure' && e.variant === variant);
      expect(row.exposure).toBeTruthy();
      if (judgment.action === 'click') {
        if (persona.id === 'P03') { await cta.focus(); await page.keyboard.press('Enter'); }
        else await cta.click();
      }
      row.click = (await readEvents()).some(e=>e.experiment === UPLOAD_CTA_EXPERIMENT.key && e.event === 'upload_cta_click' && e.variant === variant);
      expect(row.click).toBe(judgment.action === 'click');
      if (judgment.action === 'click') await expect(page.getByRole('heading',{name:'새 작품 등록',exact:true})).toBeVisible();
      else {
        await expect(page.getByRole('heading',{name:'내 작품',exact:true})).toBeVisible();
        if (judgment.action === 'leave') await page.goto('about:blank');
      }
      row.scenarioPassed = true;
    } catch (error) {
      row.error = String(error.message).slice(0,2000);
      throw error;
    } finally {
      row.observedAt = new Date().toISOString();
      writeFileSync(new URL(persona.id+'.json',output), JSON.stringify(row,null,2));
    }
  });
}
