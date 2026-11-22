import {test, expect} from '@playwright/test';

test('visitor can switch between login and signup while runtime flags load', async ({page}) => {
  await page.goto('/');

  await expect(page).toHaveTitle(/ArtworkGuard/);
  await expect(page.getByRole('heading', {name: '다시 만나 반갑습니다'})).toBeVisible();
  await expect(page.getByLabel('이메일')).toBeVisible();
  await expect(page.getByLabel('비밀번호')).toBeVisible();

  const flags = await page.evaluate(() => globalThis.__ARTGUARD_CONFIG__?.flags);
  expect(Object.keys(flags)).toEqual(['enhancedUpload', 'confidenceBadges', 'compactNavigation']);

  await page.getByRole('button', {name: '새 계정 만들기'}).click();
  await expect(page.getByRole('heading', {name: '작품 보호를 시작하세요'})).toBeVisible();
  await expect(page.getByLabel('닉네임')).toBeVisible();
  await expect(page.getByLabel('비밀번호')).toHaveAttribute('minlength', '12');

  await page.getByRole('button', {name: '로그인으로 돌아가기'}).click();
  await expect(page.getByRole('heading', {name: '다시 만나 반갑습니다'})).toBeVisible();
});
