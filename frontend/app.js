import { createApi, safeUrl } from './api.js';

const app = document.querySelector('#app');
const notice = document.querySelector('#notice');
let signedIn = false;
let currentUser = null;
let view = 0;
let objectUrls = [];
const api = createApi(fetch, () => {
  signedIn = false;
  currentUser = null;
  auth();
});

const icons = {
  home: '⌂', scan: '⌕', history: '▣', heart: '♡', report: '▤', plan: '◇',
  settings: '⚙', bell: '♢', upload: '⇧', link: '↗', folder: '□', image: '▧',
  user: '○', back: '‹', download: '⇩', more: '⋮', check: '✓', search: '⌕'
};

function el(tag, text, cls) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (cls) node.className = cls;
  return node;
}

function message(text = '', type = 'error') {
  notice.textContent = text;
  notice.className = text ? `toast ${type}` : '';
}

function cleanupView() {
  view += 1;
  objectUrls.forEach(URL.revokeObjectURL);
  objectUrls = [];
  message();
  return view;
}

function button(text, action, cls = '') {
  const node = el('button', text, cls);
  node.type = 'button';
  node.onclick = () => run(node, action);
  return node;
}

async function run(control, action) {
  control.disabled = true;
  message();
  try {
    await action();
  } catch (error) {
    document.querySelectorAll('.loading').forEach(node => node.remove());
    message(error.message + (error.code ? ` (${error.code})` : ''));
  } finally {
    control.disabled = false;
  }
}

function brand() {
  const mark = el('button', undefined, 'wordmark');
  mark.type = 'button';
  mark.setAttribute('aria-label', 'ArtGuard 메인으로');
  mark.append(el('span', 'Art'), el('strong', 'Guard'));
  mark.onclick = () => signedIn ? upload() : landing();
  return mark;
}

function topbar(showAuth = false) {
  const header = el('header', undefined, 'topbar');
  header.append(brand());
  if (showAuth) {
    const nav = el('nav', undefined, 'landing-nav');
    nav.setAttribute('aria-label', '소개 메뉴');
    for (const label of ['서비스 소개', '이용 방법', '요금제']) {
      const item = button(label, () => {
        if (label === '요금제') message('요금제 상세 화면은 로그인 후 내 정보에서 확인할 수 있습니다.', 'info');
        else document.querySelector('#서비스-소개')?.scrollIntoView({ behavior: 'smooth' });
      }, 'landing-link');
      nav.append(item);
    }
    const actions = el('div', undefined, 'top-actions');
    actions.append(button('로그인', () => auth(false), 'ghost'), button('회원가입', () => auth(true), 'primary compact'));
    header.append(nav, actions);
  } else if (signedIn) {
    const actions = el('div', undefined, 'top-actions');
    actions.append(button(icons.bell, () => message('새로운 알림이 없습니다.', 'info'), 'icon-button'));
    const avatar = button((currentUser?.nickname || currentUser?.email || 'K').slice(0, 1).toUpperCase(), profile, 'avatar');
    actions.append(avatar);
    header.append(actions);
  }
  return header;
}

function landing() {
  cleanupView();
  document.body.className = 'landing-page';
  app.replaceChildren();
  const page = el('div', undefined, 'landing-shell');
  page.append(topbar(true));

  const hero = el('main', undefined, 'hero');
  const copy = el('section', undefined, 'hero-copy');
  const eyebrow = el('span', 'AI-POWERED CREATOR PROTECTION', 'eyebrow');
  const title = el('h1');
  title.append(document.createTextNode('당신의\n창작물을 지키는\n'), el('em', 'AI 이미지 유사도 탐지'));
  const description = el('p', '온라인에 유통되는 유사 이미지 상품을 탐지하여 창작자와 브랜드의 권리를 보호합니다.');
  const actions = el('div', undefined, 'hero-actions');
  actions.append(button('지금 바로 탐지하기  →', () => auth(false), 'primary hero-button'), button('서비스 소개 보기', () => document.querySelector('#서비스-소개')?.scrollIntoView({ behavior: 'smooth' }), 'outline hero-button'));
  copy.append(eyebrow, title, description, actions);

  const visual = el('section', undefined, 'hero-visual');
  visual.setAttribute('aria-label', '이미지 유사도 탐지 예시');
  const artStack = el('div', undefined, 'art-stack');
  for (let index = 0; index < 4; index += 1) {
    const frame = el('div', undefined, `art-frame frame-${index + 1}`);
    frame.append(el('div', '△', 'mountain'), el('div', '△', 'mountain small'));
    artStack.append(frame);
  }
  const scan = el('div', undefined, 'scan-badge');
  scan.append(el('span', icons.search, 'scan-circle'), el('strong', '유사 상품 발견'));
  visual.append(artStack, scan);
  hero.append(copy, visual);

  const features = el('section', undefined, 'feature-strip');
  features.id = '서비스-소개';
  const featureData = [
    ['⌁', '빠른 탐지', '수 초 내 유사 이미지 탐지'],
    ['⌕', '다양한 플랫폼', '국내외 마켓 동시 검색'],
    ['⊕', '정확한 유사도 분석', 'AI 기반 이미지 매칭'],
    ['▤', '리포트 제공', '증거 자료를 손쉽게 보관']
  ];
  featureData.forEach(([symbol, name, detail]) => {
    const item = el('article', undefined, 'feature-item');
    item.append(el('span', symbol, 'feature-icon'), el('strong', name), el('small', detail));
    features.append(item);
  });
  page.append(hero, features);
  app.append(page);
}

function field(form, labelText, name, type = 'text', max) {
  const label = el('label', undefined, 'field');
  label.append(el('span', labelText));
  const input = el(type === 'textarea' ? 'textarea' : 'input');
  input.name = name;
  if (type !== 'textarea') input.type = type;
  if (max) input.maxLength = max;
  label.append(input);
  form.append(label);
  return input;
}

function auth(signup = false) {
  cleanupView();
  signedIn = false;
  currentUser = null;
  document.body.className = 'auth-page';
  app.replaceChildren();
  const shell = el('main', undefined, 'auth-shell');
  shell.append(brand());
  const tabs = el('div', undefined, 'auth-tabs');
  const loginTab = button('로그인', () => auth(false), signup ? '' : 'active');
  const signupTab = button('회원가입', () => auth(true), signup ? 'active' : '');
  tabs.append(loginTab, signupTab);

  const heading = el('div', undefined, 'auth-heading');
  heading.append(el('h1', signup ? '새 계정을 만들어보세요' : '계정에 로그인하세요'), el('p', signup ? '창작물 보호를 위한 첫 단계를 시작합니다.' : '지금 바로 내 작품을 보호할 수 있습니다.'));
  const form = el('form', undefined, 'auth-form');
  const email = field(form, '이메일 주소', 'email', 'email', 254);
  email.required = true;
  email.autocomplete = 'username';
  email.placeholder = 'art@artguard.com';
  const password = field(form, '비밀번호', 'password', 'password', 72);
  password.required = true;
  password.autocomplete = signup ? 'new-password' : 'current-password';
  password.placeholder = signup ? '12자 이상 입력해 주세요.' : '비밀번호를 입력하세요.';
  if (signup) password.minLength = 12;
  const nickname = signup ? field(form, '닉네임', 'nickname', 'text', 50) : null;
  if (nickname) {
    nickname.required = true;
    nickname.placeholder = '표시할 이름을 입력하세요.';
  }

  if (!signup) {
    const options = el('div', undefined, 'form-options');
    const rememberLabel = el('label', undefined, 'check-label');
    const remember = el('input');
    remember.type = 'checkbox';
    remember.checked = true;
    rememberLabel.append(remember, document.createTextNode(' 로그인 상태 유지'));
    options.append(rememberLabel, el('a', '비밀번호 찾기'));
    form.append(options);
  }

  const submit = el('button', signup ? '회원가입' : '로그인', 'primary wide');
  submit.type = 'submit';
  form.append(submit);
  form.onsubmit = event => {
    event.preventDefault();
    run(submit, async () => {
      if (new TextEncoder().encode(password.value).length > 72) throw Error('비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.');
      const body = { email: email.value, password: password.value };
      if (signup) {
        await api.request('/auth/signup', { method: 'POST', body: { ...body, nickname: nickname.value } });
        auth(false);
        message('가입이 완료되었습니다. 로그인해 주세요.', 'success');
      } else {
        await api.login(body);
        signedIn = true;
        currentUser = await api.request('/auth/me').catch(() => ({ email: email.value, nickname: email.value.split('@')[0] }));
        await upload();
      }
    });
  };
  const switcher = el('p', signup ? '이미 계정이 있나요? ' : '계정이 없으신가요? ', 'auth-switch');
  const switchButton = button(signup ? '로그인하기' : '회원가입하기', () => auth(!signup), 'text-button');
  switcher.append(switchButton);
  shell.append(tabs, heading, form, switcher);
  app.append(shell);
}

const navItems = [
  ['home', '홈', upload], ['scan', '탐지하기', upload], ['history', '탐지 내역', detections],
  ['heart', '즐겨찾기', () => placeholder('즐겨찾기', '관심 상품을 모아볼 수 있는 공간입니다.')],
  ['report', '신고 내역', reports]
];

function sidebar(active) {
  const side = el('aside', undefined, 'sidebar');
  const nav = el('nav', undefined, 'side-nav');
  nav.setAttribute('aria-label', '주 메뉴');
  navItems.forEach(([key, label, action]) => {
    const item = button('', action, `side-link ${active === key ? 'active' : ''}`);
    item.append(el('span', icons[key]), el('span', label));
    nav.append(item);
  });
  const bottom = el('nav', undefined, 'side-nav side-bottom');
  [['plan', '요금제', profile], ['settings', '설정', profile]].forEach(([key, label, action]) => {
    const item = button('', action, `side-link ${active === key ? 'active' : ''}`);
    item.append(el('span', icons[key]), el('span', label));
    bottom.append(item);
  });
  side.append(nav, bottom);
  return side;
}

function shell(title, subtitle, active) {
  if (!signedIn) {
    auth();
    return null;
  }
  cleanupView();
  document.body.className = 'app-page';
  app.replaceChildren();
  const frame = el('div', undefined, 'app-shell');
  frame.append(topbar(false), sidebar(active));
  const main = el('main', undefined, 'workspace');
  const heading = el('div', undefined, 'page-heading');
  heading.append(el('h1', title), el('p', subtitle));
  main.append(heading);
  frame.append(main);
  app.append(frame);
  return { main, stamp: view };
}

function placeholder(title, subtitle) {
  const layout = shell(title, subtitle, 'heart');
  if (!layout) return;
  const empty = el('section', undefined, 'empty-state');
  empty.append(el('span', '♡', 'empty-icon'), el('h2', '아직 저장된 항목이 없습니다.'), el('p', '탐지 결과에서 관심 상품을 저장하면 이곳에서 확인할 수 있습니다.'), button('탐지 결과 보기', () => detections(), 'primary'));
  layout.main.append(empty);
}

function upload() {
  const layout = shell('이미지 업로드', '탐지할 이미지를 업로드하거나 불러와 주세요.', 'scan');
  if (!layout) return;
  const form = el('form', undefined, 'upload-form');
  const dropzone = el('label', undefined, 'dropzone');
  const file = el('input');
  file.type = 'file';
  file.accept = 'image/png,image/jpeg';
  file.required = true;
  dropzone.append(file, el('span', icons.image, 'drop-icon'), el('strong', '이미지를 드래그하거나\n클릭하여 업로드하세요.'), el('small', 'PNG, JPG, JPEG (최대 20MB)'));
  const filename = el('span', '선택된 파일 없음', 'filename');
  file.onchange = () => { filename.textContent = file.files[0]?.name || '선택된 파일 없음'; };
  const title = field(form, '작품 제목', 'title', 'text', 200);
  title.required = true;
  title.placeholder = '등록할 작품의 제목을 입력하세요.';
  const description = field(form, '작품 설명 (선택)', 'description', 'textarea', 5000);
  description.placeholder = '작품을 구분할 수 있는 설명을 입력하세요.';
  form.prepend(dropzone, filename);
  const alternatives = el('div', undefined, 'upload-alternatives');
  alternatives.append(button(`${icons.link}  이미지 URL로 불러오기`, () => message('URL 가져오기는 안전한 원격 수집 정책 검토 후 제공됩니다.', 'info'), 'option-card'), button(`${icons.folder}  여러 이미지 업로드`, () => file.click(), 'option-card'));
  form.append(alternatives);
  const submit = el('button', '탐지 시작  ✣', 'primary wide scan-submit');
  submit.type = 'submit';
  form.append(submit);
  form.onsubmit = event => {
    event.preventDefault();
    run(submit, async () => {
      const selected = file.files[0];
      if (!selected || !['image/png', 'image/jpeg'].includes(selected.type) || selected.size < 1 || selected.size > 20971520) {
        throw Error('20 MiB 이하 PNG/JPEG 파일을 선택해 주세요.');
      }
      message('이미지를 안전하게 업로드하는 중입니다.', 'info');
      const prepared = await api.request('/artworks/upload-url', { method: 'POST', body: { contentType: selected.type, sizeBytes: selected.size } });
      const url = safeUrl(prepared.uploadUrl);
      if (!url) throw Error('업로드 주소를 확인할 수 없습니다.');
      const response = await fetch(url, { method: 'PUT', headers: prepared.requiredHeaders, body: selected, credentials: 'omit', referrerPolicy: 'no-referrer' });
      if (!response.ok) throw Error('이미지 업로드가 실패했습니다. 저장소 연결과 CORS 설정을 확인해 주세요.');
      const artwork = await api.request('/artworks', { method: 'POST', body: { uploadId: prepared.uploadId, title: title.value, description: description.value } });
      await artworkDetail(artwork.id);
    });
  };
  const recent = el('section', undefined, 'recent-section');
  recent.append(el('div', '최근 업로드한 이미지', 'section-title'));
  const thumbs = el('div', undefined, 'thumb-row');
  for (let index = 0; index < 4; index += 1) thumbs.append(el('div', '△', 'thumb-placeholder'));
  recent.append(thumbs, button('내 작품 전체 보기  ›', () => artworks(), 'text-button recent-more'));
  layout.main.append(form, recent);
}

async function artworks(page = 0) {
  const layout = shell('내 작품', '등록한 작품과 분석 진행 상태를 확인하세요.', 'home');
  if (!layout) return;
  const { main, stamp } = layout;
  main.append(button('+ 새 작품 등록', upload, 'primary page-action'));
  const loading = el('p', '작품을 불러오는 중…', 'loading');
  main.append(loading);
  const data = await api.request(`/artworks?page=${page}&size=12`);
  if (stamp !== view) return;
  loading.remove();
  const grid = el('div', undefined, 'artwork-grid');
  data.items.forEach(artwork => {
    const card = el('article', undefined, 'artwork-card');
    const visual = el('div', '△', 'card-image');
    const body = el('div', undefined, 'card-body');
    body.append(el('span', artwork.monitoringEnabled ? '모니터링 중' : '모니터링 꺼짐', 'pill'), el('h2', artwork.title), el('p', `${artwork.width} × ${artwork.height}`));
    const actions = el('div', undefined, 'card-actions');
    actions.append(button('작품 상세', () => artworkDetail(artwork.id), 'outline'), button('탐지 결과', () => detections(0, artwork.id), 'primary'));
    body.append(actions);
    card.append(visual, body);
    grid.append(card);
  });
  main.append(grid);
  if (!data.items.length) main.append(emptyPanel('아직 등록한 작품이 없습니다.', '첫 작품을 업로드하고 유사 상품 탐지를 시작해 보세요.', '작품 등록', upload));
  pager(main, page, data.totalPages, artworks);
}

async function artworkDetail(id) {
  const layout = shell('작품 분석', '임베딩을 준비하고 유사 상품 탐지를 시작하세요.', 'scan');
  if (!layout) return;
  const { main, stamp } = layout;
  const artwork = await api.request(`/artworks/${id}`);
  if (stamp !== view) return;
  const panel = el('section', undefined, 'product-detail');
  const visual = el('div', undefined, 'detail-visual');
  const image = el('img', undefined, 'detail-image');
  image.alt = artwork.title;
  image.onerror = () => image.replaceWith(el('div', '△', 'detail-image placeholder-image'));
  visual.append(image);
  api.request(`/artworks/${id}/image-url`).then(data => {
    if (stamp === view && safeUrl(data.url)) image.src = safeUrl(data.url);
  }).catch(() => image.replaceWith(el('div', '△', 'detail-image placeholder-image')));
  const info = el('div', undefined, 'detail-info');
  info.append(el('span', artwork.monitoringEnabled ? '모니터링 중' : '모니터링 꺼짐', 'pill'), el('h2', artwork.title), el('p', artwork.description || '등록된 작품 설명이 없습니다.'), el('p', `${artwork.width} × ${artwork.height}`, 'meta'));
  const status = el('div', '임베딩 상태를 확인해 주세요.', 'status-box');
  const controls = el('div', undefined, 'stack-actions');
  controls.append(button('임베딩 상태 확인', async () => {
    const job = await api.request(`/artworks/${id}/embedding`);
    status.textContent = `임베딩 상태: ${job.status}${job.errorCode ? ` · ${job.errorCode}` : ''}`;
  }, 'outline'), button('임베딩 요청 / 재시도', async () => {
    const job = await api.request(`/artworks/${id}/embedding`, { method: 'POST' });
    status.textContent = `임베딩 상태: ${job.status}${job.errorCode ? ` · ${job.errorCode}` : ''}`;
  }, 'outline'));
  const detectionStatus = el('div', '탐지를 시작하면 작업 상태가 표시됩니다.', 'status-box');
  controls.append(button('유사 상품 탐지 시작  ✣', async () => {
    const job = await api.request(`/artworks/${id}/detections`, { method: 'POST' });
    detectionStatus.textContent = `탐지 작업: ${job.status}`;
    controls.append(button('탐지 상태 새로고침', async () => {
      const latest = await api.request(`/artworks/${id}/detection-jobs/${job.jobId}`);
      detectionStatus.textContent = `탐지 작업: ${latest.status}${latest.errorCode ? ` · ${latest.errorCode}` : ''}`;
    }, 'outline'));
  }, 'primary'), button('탐지 결과 보기', () => detections(0, id), 'outline'));
  info.append(status, controls, detectionStatus);
  panel.append(visual, info);
  main.append(panel);
}

function selectFilter(labelText, options, value) {
  const label = el('label', undefined, 'select-field');
  label.append(el('span', labelText));
  const select = el('select');
  options.forEach(([key, text]) => {
    const option = el('option', text);
    option.value = key;
    select.append(option);
  });
  select.value = value;
  label.append(select);
  return { label, select };
}

async function detections(page = 0, artworkId = '', status = '', severity = '') {
  const layout = shell('탐지 결과', '업로드한 이미지와 유사한 상품을 검토하세요. 탐지 결과는 권리 침해의 법적 확정이 아닙니다.', 'history');
  if (!layout) return;
  const { main, stamp } = layout;
  const filters = el('section', undefined, 'result-toolbar');
  const statusFilter = selectFilter('상태', [['', '전체 상태'], ['NEW', '미검토'], ['CONFIRMED', '확인'], ['DISMISSED', '제외']], status);
  const severityFilter = selectFilter('유사도', [['', '전체 유사도'], ['MEDIUM', 'MEDIUM'], ['HIGH', 'HIGH'], ['CRITICAL', 'CRITICAL']], severity);
  filters.append(statusFilter.label, severityFilter.label, button('필터 적용', () => detections(0, artworkId, statusFilter.select.value, severityFilter.select.value), 'outline'));
  main.append(filters);
  const loading = el('p', '탐지 결과를 불러오는 중…', 'loading');
  main.append(loading);
  const query = new URLSearchParams({ page, size: 12, ...(artworkId ? { artworkId } : {}), ...(status ? { status } : {}), ...(severity ? { severity } : {}) });
  const data = await api.request(`/detections?${query}`);
  if (stamp !== view) return;
  loading.remove();
  const summary = el('div', undefined, 'result-summary');
  summary.append(el('strong', `전체 ${data.totalElements ?? data.items.length}개`), el('span', '유사도 높은 순'));
  main.insertBefore(summary, filters.nextSibling);
  const list = el('section', undefined, 'result-list');
  data.items.forEach(result => {
    const row = el('article', undefined, 'result-row');
    const check = el('input');
    check.type = 'checkbox';
    check.setAttribute('aria-label', `${result.productTitle} 선택`);
    const image = el('div', '△', 'result-image');
    const copy = el('div', undefined, 'result-copy');
    copy.append(el('h2', result.productTitle), el('span', result.artworkTitle, 'market'), el('strong', result.synthetic ? '테스트 데이터' : '유사 상품 후보'));
    const score = el('span', `${(result.similarity * 100).toFixed(0)}% 유사`, `score score-${scoreLevel(result.similarity)}`);
    row.append(check, image, copy, score, button('상세 보기  ↗', () => detail(result.id), 'outline compact'), el('span', icons.more, 'more'));
    list.append(row);
  });
  main.append(list);
  if (!data.items.length) main.append(emptyPanel('조건에 맞는 탐지 결과가 없습니다.', '다른 필터를 선택하거나 새 작품 탐지를 시작해 보세요.', '새 탐지 시작', upload));
  pager(main, page, data.totalPages, next => detections(next, artworkId, status, severity));
}

function scoreLevel(value) {
  if (value >= 0.9) return 'high';
  if (value >= 0.8) return 'medium';
  return 'low';
}

async function detail(id) {
  const layout = shell('상품 상세', '탐지 결과의 이미지와 유사도 근거를 확인하세요.', 'history');
  if (!layout) return;
  const { main, stamp } = layout;
  const back = button(`${icons.back}  탐지 결과로 돌아가기`, () => detections(), 'text-button back-button');
  main.insertBefore(back, main.firstChild);
  const data = await api.request(`/detections/${id}`);
  if (stamp !== view) return;
  const result = data.detection;
  const panel = el('section', undefined, 'product-detail');
  const gallery = el('div', undefined, 'detail-visual');
  const productImage = el('img', undefined, 'detail-image');
  productImage.alt = '비교 상품 이미지';
  productImage.onerror = () => productImage.replaceWith(el('div', '△', 'detail-image placeholder-image'));
  gallery.append(productImage);
  api.request(`/products/${result.productId}/images/${data.imageId}/preview`, { blob: true }).then(blob => {
    if (stamp !== view) return;
    const url = URL.createObjectURL(blob);
    objectUrls.push(url);
    productImage.src = url;
  }).catch(() => productImage.replaceWith(el('div', '△', 'detail-image placeholder-image')));

  const info = el('div', undefined, 'detail-info');
  info.append(el('span', result.synthetic ? '테스트 데이터' : '탐지 상품', 'market'), el('h2', result.productTitle), el('p', `등록 작품 · ${result.artworkTitle}`, 'meta'));
  const score = el('div', undefined, 'similarity-box');
  const scoreTop = el('div');
  scoreTop.append(el('span', '유사도'), el('strong', `${(result.similarity * 100).toFixed(0)}%`));
  const bar = el('div', undefined, 'score-bar');
  const fill = el('span');
  fill.style.width = `${Math.min(100, result.similarity * 100)}%`;
  bar.append(fill);
  score.append(scoreTop, bar);
  const table = el('dl', undefined, 'info-table');
  [['검토 상태', result.status], ['심각도', result.severity], ['등록 작품', result.artworkTitle], ['데이터 구분', result.synthetic ? '합성/테스트' : '수집 결과']].forEach(([term, description]) => table.append(el('dt', term), el('dd', description)));
  const actions = el('div', undefined, 'detail-actions');
  const productUrl = safeUrl(result.productUrl);
  if (productUrl) {
    const link = el('a', '상품 바로가기  ↗', 'button primary');
    link.href = productUrl;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    actions.append(link);
  }
  actions.append(button('리포트 생성', () => reports(id), 'outline'));
  const review = el('div', undefined, 'review-actions');
  [['CONFIRMED', '유사 상품 확인'], ['DISMISSED', '관련 없음'], ['NEW', '미검토로 변경']].forEach(([nextStatus, label]) => review.append(button(label, async () => {
    await api.request(`/detections/${id}/status`, { method: 'PATCH', body: { status: nextStatus, version: result.version } });
    await detail(id);
  }, nextStatus === 'CONFIRMED' ? 'primary' : 'outline')));
  info.append(score, table, actions, review);
  panel.append(gallery, info);
  main.append(panel);
}

async function reports(detectionId = '') {
  const layout = shell('탐지 리포트 생성', '탐지 결과를 정리하여 검토용 리포트를 만들 수 있습니다.', 'report');
  if (!layout) return;
  const { main } = layout;
  const form = el('form', undefined, 'report-form');
  const title = field(form, '리포트 제목', 'title', 'text', 120);
  title.value = 'ArtGuard 탐지 리포트';
  const description = field(form, '설명 (선택)', 'description', 'textarea', 200);
  description.placeholder = '이 리포트에 대한 설명을 입력하세요.';
  const included = el('fieldset', undefined, 'include-grid');
  included.append(el('legend', '포함할 항목'));
  ['탐지 결과 목록', '상품 상세 정보', '유사도 분석', '스크린샷 이미지'].forEach(labelText => {
    const label = el('label', undefined, 'check-label');
    const input = el('input');
    input.type = 'checkbox';
    input.checked = true;
    label.append(input, document.createTextNode(` ${labelText}`));
    included.append(label);
  });
  const format = el('fieldset', undefined, 'format-row');
  format.append(el('legend', '파일 형식'));
  ['PDF (권장)', 'CSV'].forEach((labelText, index) => {
    const label = el('label', undefined, 'radio-label');
    const input = el('input');
    input.type = 'radio';
    input.name = 'format';
    input.checked = index === 0;
    label.append(input, document.createTextNode(` ${labelText}`));
    format.append(label);
  });
  form.append(included, format);
  const submit = el('button', `${icons.download}  리포트 생성하기`, 'primary wide report-submit');
  submit.type = 'submit';
  form.append(submit);
  form.onsubmit = event => {
    event.preventDefault();
    message(detectionId ? '리포트 양식이 준비되었습니다. 브라우저 인쇄에서 PDF로 저장해 주세요.' : '탐지 상세에서 대상을 선택하면 실제 데이터가 포함됩니다.', 'success');
    if (detectionId) window.print();
  };
  const note = el('p', '※ 현재 MVP에서는 브라우저의 PDF 저장 기능을 사용합니다. 자동 증거 리포트는 탐지 상세에서 생성할 수 있습니다.', 'form-note');
  main.append(form, note);
}

async function profile() {
  const layout = shell('내 정보', '계정 정보와 서비스 이용 현황을 확인하세요.', 'settings');
  if (!layout) return;
  const { main, stamp } = layout;
  const [user, subscription, artworkData, detectionData] = await Promise.all([
    currentUser || api.request('/auth/me'),
    api.request('/subscription').catch(() => null),
    api.request('/artworks?page=0&size=1').catch(() => ({ totalElements: 0, items: [] })),
    api.request('/detections?page=0&size=1').catch(() => ({ totalElements: 0, items: [] }))
  ]);
  if (stamp !== view) return;
  currentUser = user;
  const identity = el('section', undefined, 'identity-card');
  identity.append(el('div', (user.nickname || user.email).slice(0, 1).toUpperCase(), 'profile-avatar'));
  const copy = el('div', undefined, 'identity-copy');
  copy.append(el('h2', user.nickname || 'ArtGuard 사용자'), el('p', user.email));
  identity.append(copy);
  const info = el('section', undefined, 'account-section');
  info.append(el('h2', '계정 정보'));
  const table = el('dl', undefined, 'account-table');
  [['이메일', user.email], ['권한', user.role || 'USER'], ['회원 유형', subscription?.plan?.name || '무료 플랜']].forEach(([term, value]) => table.append(el('dt', term), el('dd', value)));
  info.append(table);
  const usage = el('section', undefined, 'account-section');
  usage.append(el('h2', '이용 내역'));
  const stats = el('div', undefined, 'usage-list');
  const artworkCount = artworkData.totalElements ?? artworkData.items.length;
  const detectionCount = detectionData.totalElements ?? detectionData.items.length;
  [['▧', '등록 작품', `${artworkCount}회`], ['⌕', '탐지 결과', `${detectionCount}개`], ['♡', '즐겨찾기', '0개'], ['♧', '신고 내역', '0건']].forEach(([symbol, label, value]) => {
    const row = el('div');
    row.append(el('span', symbol), el('span', label), el('strong', value));
    stats.append(row);
  });
  usage.append(stats);
  const settings = el('section', undefined, 'account-section');
  settings.append(el('h2', '알림 설정'));
  ['탐지 완료 알림', '유사 상품 신규 발견 알림', '서비스 소식 알림'].forEach((labelText, index) => {
    const label = el('label', undefined, 'toggle-row');
    const input = el('input');
    input.type = 'checkbox';
    input.checked = index < 2;
    label.append(el('span', labelText), input);
    settings.append(label);
  });
  const logoutButton = button('로그아웃', async () => {
    try { await api.logout(); } finally {
      signedIn = false;
      currentUser = null;
      landing();
    }
  }, 'outline logout-button');
  main.append(identity, info, usage, settings, logoutButton);
}

function emptyPanel(title, text, actionText, action) {
  const panel = el('section', undefined, 'empty-state');
  panel.append(el('span', '▧', 'empty-icon'), el('h2', title), el('p', text), button(actionText, action, 'primary'));
  return panel;
}

function pager(container, page, totalPages, load) {
  if (!totalPages || totalPages <= 1) return;
  const row = el('div', undefined, 'pagination');
  const previous = button('‹', () => load(page - 1), 'icon-button');
  const next = button('›', () => load(page + 1), 'icon-button');
  previous.disabled = page === 0;
  next.disabled = page + 1 >= totalPages;
  row.append(previous, el('span', `${page + 1} / ${totalPages}`), next);
  container.append(row);
}

landing();
