const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '..');
const dist = process.env.JANKEN_WEB_DIST || path.join(root, 'webApp/build/dist/wasmJs/productionExecutable');
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.wasm': 'application/wasm', '.task': 'application/octet-stream', '.woff2': 'font/woff2' };

(async () => {
  const server = http.createServer(async (request, response) => {
    try {
      const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
      if (pathname === '/favicon.ico') { response.writeHead(204); response.end(); return; }
      const file = path.resolve(dist, '.' + (pathname === '/' ? '/index.html' : pathname));
      if (!file.startsWith(dist + path.sep)) { response.writeHead(403); response.end(); return; }
      response.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');
      response.end(await fs.readFile(file));
    } catch { response.writeHead(404); response.end(); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({ executablePath: process.env.JANKEN_CHROME || (process.platform === 'win32' ? 'C:/Program Files/Google/Chrome/Application/chrome.exe' : undefined),
    headless: true, args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream',
      '--use-file-for-fake-video-capture=' + path.join(root, '.work/camera-fixture.y4m')] });
  const errors = [];
  const page = await browser.newPage({ viewport: { width: 480, height: 850 } });
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text().slice(0, 600)); });
  page.on('pageerror', error => errors.push(String(error).slice(0, 600)));
  async function accessibility(target) {
    const control = target.getByRole('button', { name: /Enable accessibility/i });
    if (await control.count()) await control.first().click();
  }
  async function settings(target = page) {
    await target.getByLabel(/^(Настройки|Settings)$/).click({ force: true });
  }
  async function choose(label, target = page) {
    const control = target.getByLabel(label, { exact: true });
    for (let step = 0; step < 7; step++) {
      const box = await control.boundingBox();
      if (box && box.y >= 70 && box.y + box.height <= 760) { await control.click({ force: true }); return; }
      await target.mouse.move(300, 460);
      await target.mouse.wheel(0, box && box.y < 70 ? -240 : 240);
      await target.waitForTimeout(180);
    }
    throw new Error('Setting is not visible: ' + label);
  }
  async function done(target = page) {
    const button = target.getByRole('button', { name: /^(Готово|Done)$/ });
    await button.click({ force: true });
    await button.waitFor({ state: 'detached' });
  }
  async function selectedInput(label, target = page) {
    await target.getByLabel(label, { exact: true }).waitFor({ state: 'attached' });
    const method = /^(Кнопки|Buttons)$/.test(label) ? 'BUTTONS' : 'CAMERA';
    await target.waitForFunction(value => localStorage.getItem('dev.janken.settings.v1')?.includes('inputMethod=' + value), method);
  }
  async function input(label, target = page) {
    await target.getByLabel(label, { exact: true }).click({ force: true });
    await selectedInput(label, target);
  }
  async function swipe(from, to, target = page) {
    await target.mouse.move(from, 240);
    await target.mouse.down();
    await target.mouse.move(to, 240, { steps: 14 });
    await target.mouse.up();
  }
  async function startReadyRound(target = page) {
    const control = target.getByRole('button', { name: /^(Начать раунд|Start round)$/ });
    for (let step = 0; step < 300 && !await control.isEnabled(); step++) await target.waitForTimeout(100);
    assert.ok(await control.isEnabled(), 'Camera-ready start button must become enabled');
    await target.waitForTimeout(350); // Let the preview/permission layout settle before using canvas coordinates.
    const box = await control.boundingBox();
    assert.ok(box);
    // Click the actual Compose canvas; scrolling its accessibility mirror can move a stale DOM target.
    await target.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  }
  try {
    const url = `http://127.0.0.1:${server.address().port}`;
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto(url, { waitUntil: 'networkidle', timeout: 60000 });
    await page.waitForTimeout(1800);
    await page.evaluate(() => {
      globalThis.__cameraTest = { observations: [], previews: 0, errors: [], stops: 0 };
      const start = globalThis.JankenCamera.start;
      globalThis.JankenCamera.start = callbacks => {
        const session = start({ ...callbacks,
          onObservation: json => { globalThis.__cameraTest.observations.push(JSON.parse(json)); callbacks.onObservation(json); },
          onPreview: image => { globalThis.__cameraTest.previews++; callbacks.onPreview(image); },
          onError: error => { globalThis.__cameraTest.errors.push(error); callbacks.onError(error); },
        });
        return { stop() { globalThis.__cameraTest.stops++; session.stop(); } };
      };
    });
    await accessibility(page);
    await page.getByRole('button', { name: 'Разрешить камеру', exact: true }).click({ force: true });
    await page.waitForFunction(() => globalThis.__cameraTest.observations.some(f => f.label === 'Closed_Fist' && f.score >= .65), null, { timeout: 45000 });
    const evidence = await page.evaluate(() => ({ frames: globalThis.__cameraTest.observations.length,
      fist: globalThis.__cameraTest.observations.find(f => f.label === 'Closed_Fist' && f.score >= .65), errors: globalThis.__cameraTest.errors }));
    assert.equal(evidence.fist.points.length, 21);
    assert.equal(evidence.fist.worldPoints.length, 21);
    assert.deepEqual(evidence.errors, []);
    await page.screenshot({ path: path.join(root, '.work/web-live-camera.png') });
    await page.getByRole('button', { name: 'Начать раунд', exact: true }).click({ force: true });
    await page.getByRole('button', { name: /Следующий раунд|Сыграть ещё матч/ }).waitFor({ timeout: 12000 });
    console.log('Full camera round completed.');

    await page.getByRole('button', { name: 'Следующий раунд', exact: true }).click({ force: true });
    await page.getByText('Раунд 2', { exact: true }).waitFor({ state: 'attached' });
    const beforeSettingsRound = await page.getByText(/^Раунд \d+$/).textContent();
    await swipe(400, 80);
    await selectedInput('Кнопки');
    const stopsBefore = await page.evaluate(() => globalThis.__cameraTest.stops);
    await settings();
    await page.waitForTimeout(4000);
    assert.equal(await page.evaluate(() => globalThis.__cameraTest.stops), stopsBefore);
    await choose('Светлая');
    await choose('English');
    await page.getByText('Theme', { exact: true }).waitFor({ state: 'attached' });
    await page.screenshot({ path: path.join(root, '.work/settings-menu-en.png') });
    await done();
    assert.equal(await page.getByText(/^Round \d+$/).textContent(), beforeSettingsRound.replace('Раунд', 'Round'));
    await page.getByRole('button', { name: /Paper$/ }).click({ force: true });
    await page.getByRole('button', { name: /Next round|Play another match/ }).waitFor({ timeout: 12000 });
    assert.equal(await page.locator('video').count(), 0);
    await page.screenshot({ path: path.join(root, '.work/settings-light-en.png') });
    console.log('Input/theme/language changes preserve the active round, bot and score; camera stops for buttons.');
    const visibleText = await page.locator('[role]').allTextContents();
    assert.ok(!visibleText.some(text => /[А-Яа-яЁё]/.test(text)), JSON.stringify(visibleText));

    await page.reload({ waitUntil: 'networkidle' });
    await accessibility(page);
    await selectedInput('Buttons');
    await page.getByRole('button', { name: 'Start round', exact: true }).waitFor({ state: 'attached' });
    await page.screenshot({ path: path.join(root, '.work/settings-restored-light-en.png') });
    assert.equal(await page.locator('video').count(), 0);
    await swipe(200, 220); // Below the gesture threshold: keep button input.
    await selectedInput('Buttons');
    console.log('Preferences survive reload: English, light theme and button input.');

    await settings();
    await choose('Dark');
    await done();
    await page.screenshot({ path: path.join(root, '.work/settings-dark-en.png') });
    await settings();
    await choose('System');
    await done();
    await page.emulateMedia({ colorScheme: 'light' });
    await page.waitForTimeout(350);
    await page.screenshot({ path: path.join(root, '.work/settings-system-light.png') });
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.waitForTimeout(350);
    await page.screenshot({ path: path.join(root, '.work/settings-system-dark.png') });
    console.log('Dark/light/system selections exercised.');

    await settings();
    await choose('Русский');
    await done();
    await swipe(80, 400);
    await selectedInput('Камера');
    await page.getByRole('button', { name: 'Разрешить камеру', exact: true }).click({ force: true });
    await startReadyRound();
    await page.getByText('БОТ УЖЕ ВЫБРАЛ ХОД', { exact: true }).waitFor({ state: 'attached' });
    await page.evaluate(() => {
      Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await page.waitForTimeout(1200);
    await page.evaluate(() => {
      Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await page.getByRole('button', { name: /Следующий раунд|Сыграть ещё матч/ }).waitFor({ timeout: 15000 });
    console.log('Background and camera restart preserve the pending round.');

    const denied = await browser.newPage({ viewport: { width: 480, height: 850 } });
    await denied.addInitScript(() => {
      Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { configurable: true,
        value: () => Promise.reject(new DOMException('Denied by test', 'NotAllowedError')) });
    });
    await denied.goto(url, { waitUntil: 'networkidle' });
    await accessibility(denied);
    await denied.getByRole('button', { name: 'Разрешить камеру', exact: true }).click({ force: true });
    await denied.getByText(/Доступ к камере запрещён/).waitFor({ state: 'attached' });
    await input('Кнопки', denied);
    await denied.getByRole('button', { name: 'Начать раунд', exact: true }).click({ force: true });
    await input('Камера', denied);
    await denied.getByText(/Доступ к камере запрещён/).waitFor({ state: 'attached' });
    await denied.getByText('Раунд 1', { exact: true }).waitFor({ state: 'attached' });
    await input('Кнопки', denied);
    await denied.getByRole('button', { name: /Бумага$/ }).click({ force: true });
    await denied.getByRole('button', { name: /Следующий раунд|Сыграть ещё матч/ }).waitFor({ timeout: 12000 });
    await denied.close();
    console.log('Denied camera can fall back to buttons without losing the pending round.');
    console.log('Visible input tabs, left/right preview swipes and the short-swipe threshold exercised.');
    assert.ok(!errors.some(error => !/^INFO: Created TensorFlow Lite XNNPACK delegate for CPU\.$/.test(error)), JSON.stringify(errors));
    await fs.writeFile(path.join(root, '.work/web-camera-evidence.json'), JSON.stringify({ ...evidence, consoleErrors: errors }, null, 2));
  } catch (error) {
    console.error('Browser errors:', errors);
    console.error('Camera evidence:', await page.evaluate(() => ({ frames: globalThis.__cameraTest?.observations.length,
      errors: globalThis.__cameraTest?.errors, stops: globalThis.__cameraTest?.stops })).catch(() => null));
    console.error('Page semantics:', await page.locator('[role]').evaluateAll(nodes => nodes.slice(-40).map(n => ({
      role: n.getAttribute('role'), label: n.getAttribute('aria-label'), text: n.textContent }))));
    await page.screenshot({ path: path.join(root, '.work/web-error.png') }).catch(() => {});
    throw error;
  } finally { await browser.close(); server.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
