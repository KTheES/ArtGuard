import {test, expect} from '@playwright/test';
import {readFileSync, mkdirSync, writeFileSync} from 'node:fs';
import {assignVariant, UPLOAD_CTA_EXPERIMENT} from '../feature-flags.js';
const panel = JSON.parse(readFileSync(new URL('../../experiments/week13/personas.json', import.meta.url)));
const output = new URL('../../reports/week13/latest/', import.meta.url);
for (const persona of panel.personas) {
  test(`week13 synthetic panel ${persona.id}: ${persona.scenario}`, async ({page}) => {
    const email = persona.id.toLowerCase() + '@example.invalid';
    const variant = assignVariant(UPLOAD_CTA_EXPERIMENT, email);
    await page.route('**/api/v1/**', async route => {
      const url = new URL(route.request().url());
      const data = url.pathname.endsWith('/auth/login')
        ? {accessToken:'synthetic-test-only', refreshToken:'synthetic-test-only'}
        : {items:[], totalPages:0};
      await route.fulfill({json:{success:true, data}});
    });
    if (persona.id === 'P10') await page.setViewportSize({width:390,height:844});
    await page.goto('/');
    await page.getByLabel('이메일').fill(email);
    await page.getByLabel('비밀번호').fill('synthetic-password');
    await page.getByRole('button', {name:'로그인',exact:true}).click();
    const label = UPLOAD_CTA_EXPERIMENT.variants.find(v => v.key === variant).label;
    await expect(page.getByRole('button',{name:label,exact:true})).toBeVisible();
    if (persona.id === 'P03') {
      await page.getByRole('button',{name:label,exact:true}).focus();
      await page.keyboard.press('Enter');
    } else await page.getByRole('button',{name:label,exact:true}).click();
    await expect(page.getByRole('heading',{name:'새 작품 등록',exact:true})).toBeVisible();
    const events = await page.evaluate(() => JSON.parse(localStorage.getItem('artguard-experiment-events') || '[]'));
    expect(events.some(e => e.event === 'exposure' && e.variant === variant)).toBeTruthy();
    expect(events.some(e => e.event === 'upload_cta_click' && e.variant === variant)).toBeTruthy();
    mkdirSync(output,{recursive:true});
    writeFileSync(new URL(persona.id+'.json',output), JSON.stringify({
      source:'synthetic-playwright-panel',persona:persona.id,variant,observedAt:new Date().toISOString(),
      exposure:true,click:true,scenarioPassed:true,
      note:'Scripted click; not human conversion behavior. Backend mocked; actual upload not measured.'
    },null,2));
  });
}
