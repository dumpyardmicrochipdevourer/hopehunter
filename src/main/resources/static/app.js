/*
 * hopehunter - мини-приложение Telegram.
 *
 * Без сборки и без фреймворка: один файл, который браузер исполняет как есть. Экранов десять,
 * состояния на каждом - на одну форму; сборщик и зависимости стоили бы здесь дороже, чем дают.
 *
 * Каждый экран - функция, которая рисует разметку в #app и возвращает обработчики своих кнопок.
 * Всё пользовательское проходит через esc(): названия вакансий и письма пишут люди.
 */
(() => {
  'use strict';

  const tg = window.Telegram && window.Telegram.WebApp;
  const initData = (tg && tg.initData) || '';
  // Вне Telegram подписи нет и API ответит 401. На localhost не мешаем - так удобно верстать.
  const LOCAL = ['localhost', '127.0.0.1'].includes(location.hostname);

  const app = document.getElementById('app');
  const sheetRoot = document.getElementById('sheet-root');
  const toastEl = document.getElementById('toast');

  // ---------- мелочи

  const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  const money = (n) => String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');

  /** plural(24, 'вакансия', 'вакансии', 'вакансий') */
  const plural = (n, one, few, many) => {
    const d = Math.abs(n) % 100, u = d % 10;
    if (d > 10 && d < 20) return many;
    if (u === 1) return one;
    if (u >= 2 && u <= 4) return few;
    return many;
  };

  const pad = (n) => String(n).padStart(2, '0');

  /** «14:20» сегодня, «вчера», иначе «03.10». */
  const when = (iso) => {
    const d = new Date(iso), now = new Date();
    const day = (x) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
    const diff = Math.round((day(now) - day(d)) / 86400000);
    if (diff === 0) return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
    if (diff === 1) return 'вчера';
    return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}`;
  };

  const whenLong = (iso) => {
    const d = new Date(iso), now = new Date();
    const time = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
    return d.toDateString() === now.toDateString() ? `сегодня, ${time}` : `${pad(d.getDate())}.${pad(d.getMonth() + 1)}, ${time}`;
  };

  const interval = (m) => (m < 60 ? `${m} минут` : m === 60 ? '1 час' : `${m / 60} часа`);

  const haptic = (kind) => { try { tg && tg.HapticFeedback && tg.HapticFeedback.impactOccurred(kind || 'light'); } catch (_) { /* старый клиент */ } };

  let toastTimer;
  const toast = (text, isError) => {
    toastEl.textContent = text;
    toastEl.className = 'show' + (isError ? ' error' : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { toastEl.className = ''; }, 3200);
  };

  // ---------- иконки (из макета)

  const svg = (size, stroke, width, body) =>
    `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${stroke}" stroke-width="${width}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${body}</svg>`;
  const ICON = {
    back: svg(20, 'currentColor', 1.8, '<path d="M14.5 5 8 12l6.5 7"/>'),
    chevron: svg(16, '#86868B', 1.8, '<path d="m9.5 5 6.5 7-6.5 7"/>'),
    trash: svg(19, '#86868B', 1.6, '<path d="M4 7h16"/><path d="M9 7V4h6v3"/><path d="M6 7l.9 12.1A1 1 0 0 0 7.9 20h8.2a1 1 0 0 0 1-.9L18 7"/><path d="M10 11v5"/><path d="M14 11v5"/>'),
    play: svg(16, 'currentColor', 1.8, '<path d="M7 4.5 19 12 7 19.5z"/>'),
    retry: svg(17, 'currentColor', 1.8, '<path d="M20 12a8 8 0 1 1-2.6-5.9"/><path d="M20 4v4.5h-4.5"/>'),
    home: svg(20, 'currentColor', 1.6, '<path d="M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1z"/>'),
    rules: svg(20, 'currentColor', 1.6, '<path d="M4 8h4"/><path d="M13 8h7"/><path d="M4 16h7"/><path d="M16 16h4"/><circle cx="10.5" cy="8" r="2.5"/><circle cx="13.5" cy="16" r="2.5"/>'),
    letters: svg(20, 'currentColor', 1.6, '<path d="M3.5 6.5h17v11h-17z"/><path d="m4 7.2 8 5.4 8-5.4"/>'),
    stats: svg(20, 'currentColor', 1.6, '<path d="M5 20v-6.5"/><path d="M12 20V4"/><path d="M19 20v-10"/>'),
  };

  // ---------- API

  class ApiError extends Error {
    constructor(status, body) {
      // Без message ответ пришёл не от нашего сервера (прокси, туннель) - код помогает понять, от кого.
      super(body.message || `что-то пошло не так (${status || 'нет связи'})`);
      this.status = status;
      this.code = body.error || 'unknown';
      this.field = body.field || null;
    }
  }

  async function api(method, path, body) {
    let response;
    try {
      response = await fetch('/api' + path, {
        method,
        headers: Object.assign({ Authorization: 'tma ' + initData }, body === undefined ? {} : { 'Content-Type': 'application/json' }),
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch (_) {
      throw new ApiError(0, { error: 'network', message: 'Нет связи с сервером.' });
    }
    if (response.status === 204) return null;
    let data = {};
    try { data = await response.json(); } catch (_) { /* тело не json - разберёмся по статусу */ }
    if (!response.ok) throw new ApiError(response.status, data);
    return data;
  }

  let metaCache;
  const meta = () => (metaCache = metaCache || api('GET', '/meta').catch((e) => { metaCache = null; throw e; }));

  // ---------- общие куски разметки

  const nav = (active) => {
    const tab = (key, href, label) =>
      `<a href="${href}"${active === key ? ' aria-current="page"' : ''}>${ICON[key]}<span>${label}</span></a>`;
    return `<nav class="nav"><div>${tab('home', '#/', 'главная')}${tab('rules', '#/rules', 'правила')}${tab('letters', '#/letters', 'письма')}${tab('stats', '#/stats', 'статистика')}</div></nav>`;
  };

  const backBar = (title, backHref, trail) =>
    `<div class="back-bar"><a class="icon-btn lead" href="${backHref}" aria-label="Назад">${ICON.back}</a><h1>${title}</h1>${trail || ''}</div>`;

  const skeleton = (n) => Array.from({ length: n }, (_, i) =>
    `<div class="sk"><i style="width:${[72, 58, 80, 64, 70][i % 5]}%"></i><i style="width:${[48, 40, 52, 44, 38][i % 5]}%"></i><i style="width:${[38, 44, 34, 40, 46][i % 5]}%"></i></div>`).join('');

  const switchBtn = (checked, label, act, extra) =>
    `<button type="button" class="switch" role="switch" aria-checked="${checked}" aria-label="${esc(label)}" data-act="${act}"${extra || ''}></button>`;

  const message = (title, text, button) =>
    `<div class="center"><div class="message"><div><h2>${title}</h2><p>${esc(text)}</p></div>${button || ''}</div></div>`;

  const logRows = (entries) => entries.map((e, i) =>
    `${i ? '<div class="hr"></div>' : ''}<div class="log"><span><b>${esc(e.vacancyName)}</b><small>${esc(e.company)}</small></span><small>${when(e.at)}</small></div>`).join('');

  // ---------- шторка

  let closeSheet = () => {};

  /**
   * options: [{label, sub, value, checked}] - выбор из списка;
   * search: {placeholder, load(query) -> options} - список с поиском;
   * confirm: {label, danger, run} - подтверждение с кнопками «отмена / действие»;
   * multi: true - можно отметить несколько, результат отдаётся в onDone по кнопке «готово».
   */
  function openSheet({ title, text, options, search, confirm, onPick, multi, onDone }) {
    closeSheet();
    const wrap = document.createElement('div');
    const list = (items) => items.map((o, i) =>
      `<button type="button" class="option" role="${multi ? 'checkbox' : 'radio'}" aria-checked="${!!o.checked}" data-i="${i}"><span>${esc(o.label)}${o.sub ? `<small>${esc(o.sub)}</small>` : ''}</span></button>`).join('');
    let current = options || [];
    wrap.innerHTML = `<div class="scrim"></div><div class="sheet" role="dialog" aria-modal="true" aria-label="${esc(title)}">
      <div class="sheet-head"><h2>${esc(title)}</h2>${text ? `<p>${esc(text)}</p>` : ''}</div>
      ${search ? `<input class="field" type="search" placeholder="${esc(search.placeholder)}" autocomplete="off">` : ''}
      ${confirm ? `<div class="row"><button type="button" class="btn second" data-cancel>отмена</button><button type="button" class="btn${confirm.danger ? ' danger' : ''}" data-ok>${esc(confirm.label)}</button></div>`
        : `<div class="sheet-list" role="${multi ? 'group' : 'radiogroup'}">${list(current)}</div>`}
      ${multi ? '<button type="button" class="btn" data-done>готово</button>' : ''}
    </div>`;
    sheetRoot.appendChild(wrap);
    requestAnimationFrame(() => wrap.classList.add('open'));

    const close = () => {
      wrap.classList.remove('open');
      setTimeout(() => wrap.remove(), 220);
      closeSheet = () => {};
    };
    closeSheet = close;

    wrap.addEventListener('click', (event) => {
      if (event.target.classList.contains('scrim') || event.target.closest('[data-cancel]')) return close();
      if (event.target.closest('[data-ok]')) { close(); return confirm.run(); }
      if (event.target.closest('[data-done]')) {
        const picked = [...wrap.querySelectorAll('.option[aria-checked="true"]')].map((el) => current[Number(el.dataset.i)].value);
        close();
        return onDone(picked);
      }
      const option = event.target.closest('.option');
      if (!option) return;
      haptic();
      if (multi) { option.setAttribute('aria-checked', String(option.getAttribute('aria-checked') !== 'true')); return; }
      close();
      onPick(current[Number(option.dataset.i)]);
    });

    if (search) {
      const input = wrap.querySelector('input');
      const box = wrap.querySelector('.sheet-list');
      let timer, seq = 0;
      const run = async () => {
        const mine = ++seq;
        try {
          const found = await search.load(input.value.trim());
          if (mine !== seq) return;
          current = found;
          box.innerHTML = list(current);
        } catch (e) {
          if (mine === seq) box.innerHTML = `<p class="field-error">${esc(e.message)}</p>`;
        }
      };
      input.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(run, 250); });
      setTimeout(() => input.focus(), 250);
    }
  }

  // ---------- маршрутизация

  let actions = {};
  let renderSeq = 0;
  let backHref = null;

  /** Экран считается устаревшим, если пока он грузился, человек ушёл на другой. */
  const stale = (seq) => seq !== renderSeq;

  function paint(html, opts) {
    app.innerHTML = `<main class="screen ${opts && opts.nav ? 'with-nav' : 'no-nav'}">${html}</main>${opts && opts.nav ? nav(opts.nav) : ''}`;
  }

  /** Отказ, после которого экран показать нечем. */
  function fatal(error, retry) {
    if (error.code === 'unauthorized') {
      return paint(message('откройте заново', 'Подпись Telegram устарела. Закройте приложение и откройте его из бота.'));
    }
    if (error.code === 'forbidden') {
      return paint(message('нет доступа', error.message));
    }
    actions.retry = retry;
    paint(message(error.code === 'hh_unavailable' ? 'hh не отвечает' : 'не получилось', error.message,
      `<button type="button" class="btn" data-act="retry">${ICON.retry} повторить</button>`));
  }

  const routes = [
    [/^\/$/, home, null],
    [/^\/connect$/, connect, '#/'],
    [/^\/rules$/, rules, null],
    [/^\/rules\/new$/, ruleForm, '#/rules'],
    [/^\/rules\/(\d+)$/, ruleForm, '#/rules'],
    [/^\/rules\/(\d+)\/preview$/, preview, null],
    [/^\/letters$/, letters, null],
    [/^\/letters\/new$/, letterForm, '#/letters'],
    [/^\/letters\/(\d+)$/, letterForm, '#/letters'],
    [/^\/stats$/, stats, null],
  ];

  function route() {
    closeSheet();
    const path = location.hash.replace(/^#/, '') || '/';
    const seq = ++renderSeq;
    actions = {};
    for (const [pattern, screen, back] of routes) {
      const match = path.match(pattern);
      if (match) {
        backHref = back || (pattern.source.includes('preview') ? `#/rules/${match[1]}` : null);
        if (tg && tg.BackButton) { backHref ? tg.BackButton.show() : tg.BackButton.hide(); }
        window.scrollTo(0, 0);
        screen(seq, match[1]).catch((e) => { if (!stale(seq)) fatal(e, route); });
        return;
      }
    }
    location.hash = '#/';
  }

  app.addEventListener('click', (event) => {
    const el = event.target.closest('[data-act]');
    if (!el || el.disabled) return;
    const handler = actions[el.dataset.act];
    if (handler) { event.preventDefault(); handler(el, event); }
  });

  // ---------- главная

  async function home(seq) {
    paint(`<div class="brand"><b>hh</b><span>hopehunter</span></div><div class="stack">${skeleton(2)}</div>`, { nav: 'home' });
    const [me, letterList, statsData] = await Promise.all([api('GET', '/me'), api('GET', '/letters'), api('GET', '/stats')]);
    if (stale(seq)) return;

    const connected = me.account.state === 'ACTIVE';
    const expired = me.account.state === 'EXPIRED';
    const brand = '<div class="brand"><b>hh</b><span>hopehunter</span></div>';

    // Первый запуск: три шага и одна кнопка к следующему. Правило - последний шаг, после него
    // показываем обычную главную, даже если письма человек так и не написал.
    if (me.rulesTotal === 0 && !expired) {
      const done = [connected, letterList.length > 0, false];
      const current = done.indexOf(false);
      const titles = ['подключить hh', 'написать письмо', 'создать правило'];
      const steps = titles.map((title, i) =>
        `${i ? '<div class="hr inset"></div>' : ''}<div class="step${i === current ? ' current' : ''}${done[i] ? ' done' : ''}"><i>${done[i] ? '✓' : i + 1}</i><span>${title}</span></div>`).join('');
      const next = [['подключить hh', '#/connect'], ['написать письмо', '#/letters/new'], ['создать правило', '#/rules/new']][current];
      paint(`${brand}<div class="center"><div class="shell"><div class="block">${steps}</div>
        <a class="btn" href="${next[1]}">${next[0]}</a>
        ${current === 1 ? '<a class="btn second" href="#/rules/new">создать правило без письма</a>' : ''}</div></div>`, { nav: 'home' });
      return;
    }

    const status = expired ? 'остановлен' : me.paused ? 'пауза' : me.stopped ? 'до завтра' : me.rulesEnabled === 0 ? 'выключен' : 'работаю';
    const percent = me.dailyLimit ? Math.min(100, Math.round((me.sentToday / me.dailyLimit) * 100)) : 0;
    const dim = expired ? ' dim' : '';

    paint(`${brand}<div class="stack"><div class="shell">
      ${expired ? `<div class="alert"><div><h2>hh отключился</h2><p>сессия истекла</p></div><a class="btn small" href="#/connect">переподключить</a></div>` : ''}
      ${me.stopped && !expired ? `<div class="alert"><div><h2>стоп до завтра</h2><p>капча или суточный лимит hh</p></div></div>` : ''}
      <div class="status"><span><small>статус</small><strong>${status}</strong></span>${switchBtn(!me.paused && !expired, 'Работа бота', 'pause', expired ? ' disabled' : '')}</div>
      <div class="block">
        <a class="kv${dim}" href="#/connect"><span>аккаунт</span><span>${esc(me.account.ownerName || (connected ? 'подключён' : 'не подключён'))}</span></a>
        <div class="hr"></div>
        <a class="kv${dim}" href="#/rules"><span>правила</span><span>${me.rulesEnabled} из ${me.rulesTotal} включено</span></a>
        <div class="hr"></div>
        <div class="kv${dim}"><span>отклики сегодня</span><span>${me.sentToday}${me.dailyLimit ? ` из ${me.dailyLimit}` : ''}</span></div>
        <div class="progress-pad"><div class="progress"><i style="width:${percent}%"></i></div></div>
      </div>
      ${statsData.recent.length ? `<div class="section-head"><span>последние отклики</span><a href="#/stats">все</a></div><div class="block">${logRows(statsData.recent.slice(0, 5))}</div>` : ''}
    </div></div>`, { nav: 'home' });

    actions.pause = async (el) => {
      const paused = el.getAttribute('aria-checked') === 'true';
      el.setAttribute('aria-checked', String(!paused));
      haptic();
      try { await api('PUT', '/me/pause', { paused }); } catch (e) { toast(e.message, true); }
      if (!stale(seq)) home(seq).catch(() => {});
    };
  }

  // ---------- подключение hh

  const BROWSERS = {
    chrome: ['Chrome', ['войдите на hh.ru', 'F12 → Application → Cookies', 'скопируйте значение <code>hhtoken</code>']],
    firefox: ['Firefox', ['войдите на hh.ru', 'F12 → Хранилище → Куки', 'скопируйте значение <code>hhtoken</code>']],
    edge: ['Edge', ['войдите на hh.ru', 'F12 → Приложение → Cookies', 'скопируйте значение <code>hhtoken</code>']],
    safari: ['Safari', ['войдите на hh.ru', '⌥⌘I → Хранилище → Cookies', 'скопируйте значение <code>hhtoken</code>']],
  };

  async function connect(seq) {
    paint(`${backBar('подключение hh', '#/')}<div class="stack">${skeleton(2)}</div>`);
    const account = await api('GET', '/account');
    if (stale(seq)) return;
    let browser = 'chrome';
    let error = null;
    let busy = false;
    let forceForm = account.state !== 'ACTIVE';

    const done = () => {
      paint(`${backBar('подключение hh', '#/')}<div class="stack">
        <span class="label">аккаунт</span>
        <div class="item"><span class="item-title" style="font-size:17px;font-weight:600;letter-spacing:-0.02em">${esc(account.ownerName || 'аккаунт hh')}</span></div>
        <div class="block">
          <div class="kv"><span>cookies</span><span>обновлены ${whenLong(account.updatedAt)}</span></div>
          <div class="hr"></div>
          <div class="kv"><span>резюме</span><span id="resume-count" class="muted">…</span></div>
        </div>
        <div style="padding-top:6px;display:flex;flex-direction:column;gap:10px">
          <button type="button" class="btn second" data-act="renew">обновить cookies</button>
          <button type="button" class="btn danger" data-act="disconnect">отключить</button>
        </div></div>`);
      api('GET', '/account/resumes').then((list) => {
        const el = document.getElementById('resume-count');
        if (el && !stale(seq)) { el.textContent = list.length; el.classList.remove('muted'); }
      }).catch((e) => {
        if (stale(seq)) return;
        if (e.code === 'hh_session_expired') { route(); return; }
        const el = document.getElementById('resume-count');
        if (el) el.textContent = 'hh не отвечает';
      });
    };

    const form = () => {
      const value = (document.getElementById('cookies') || {}).value || '';
      const [, steps] = BROWSERS[browser];
      paint(`${backBar('подключение hh', '#/')}<div class="stack">
        <span class="label">браузер</span>
        <div class="pills">${Object.keys(BROWSERS).map((key) =>
          `<button type="button" class="pill" data-act="browser" data-key="${key}"${key === browser ? ' aria-current="true"' : ''}>${BROWSERS[key][0]}</button>`).join('')}</div>
        <span class="label">шаги</span>
        <div class="block">${steps.map((text, i) =>
          `${i ? '<div class="hr inset"></div>' : ''}<div class="step"><i>${i + 1}</i><span>${text}</span></div>`).join('')}</div>
        ${browser === 'safari' ? '<span class="item-sub" style="padding:0 4px">меню разработчика: Настройки → Дополнения</span>' : ''}
        <label class="sr" for="cookies">cookies</label>
        <textarea id="cookies" class="field${error ? ' invalid' : ''}" placeholder="вставьте сюда" style="height:150px" autocomplete="off" autocapitalize="off" spellcheck="false">${esc(value)}</textarea>
        ${error ? `<span class="field-error">${esc(error)}</span>` : '<span class="item-sub" style="padding:0 4px">хранится зашифрованным</span>'}
        <div style="padding-top:6px"><button type="button" class="btn" data-act="submit"${busy ? ' disabled' : ''}>${busy ? 'проверяю на hh…' : 'подключить'}</button></div>
      </div>`);
    };

    const draw = () => (forceForm ? form() : done());

    actions.browser = (el) => { browser = el.dataset.key; form(); };
    actions.renew = () => { forceForm = true; draw(); };
    actions.submit = async () => {
      const value = document.getElementById('cookies').value.trim();
      if (!value) { error = 'Вставьте значение hhtoken.'; return form(); }
      busy = true; error = null; form();
      try {
        Object.assign(account, await api('PUT', '/account/cookies', { value }));
        busy = false; forceForm = false;
        haptic('medium');
        if (!stale(seq)) done();
      } catch (e) {
        busy = false;
        error = e.code === 'hh_session_expired'
          ? 'hh это значение не принял - оно от закрытой сессии. Обновите страницу hh.ru и скопируйте заново.'
          : e.message;
        if (!stale(seq)) form();
      }
    };
    actions.disconnect = () => openSheet({
      title: 'отключить hh?',
      text: 'правила и письма останутся',
      confirm: { label: 'отключить', danger: true, run: async () => {
        try { await api('DELETE', '/account'); location.hash = '#/'; } catch (e) { toast(e.message, true); }
      } },
    });

    draw();
  }

  // ---------- правила

  async function rules(seq) {
    const head = (withNew) => `<div class="title-bar"><h1>правила</h1>${withNew ? '<a href="#/rules/new">новое</a>' : ''}</div>`;
    paint(`${head(false)}<div class="stack">${skeleton(3)}</div>`, { nav: 'rules' });
    const list = await api('GET', '/rules');
    if (stale(seq)) return;

    if (!list.length) {
      return paint(`${head(false)}<div class="empty"><h2>правил нет</h2><a class="btn inline" href="#/rules/new">новое правило</a></div>`, { nav: 'rules' });
    }
    paint(`${head(true)}<div class="stack">${list.map((rule) => {
      const percent = Math.min(100, Math.round((rule.sentToday / rule.dailyLimit) * 100));
      return `<div class="item"><div class="item-head"><a class="item-title" href="#/rules/${rule.id}">${esc(rule.name)}</a>${switchBtn(rule.enabled, rule.name, 'toggle', ` data-id="${rule.id}"`)}</div>
        <div class="item-meta"><span class="item-sub">${rule.sentToday} из ${rule.dailyLimit} сегодня</span><div class="progress"><i style="width:${percent}%"></i></div></div></div>`;
    }).join('')}</div>`, { nav: 'rules' });

    actions.toggle = async (el) => {
      const enabled = el.getAttribute('aria-checked') !== 'true';
      el.setAttribute('aria-checked', String(enabled));
      haptic();
      try {
        await api('PUT', `/rules/${el.dataset.id}/enabled`, { enabled });
      } catch (e) {
        el.setAttribute('aria-checked', String(!enabled));
        toast(e.message, true);
      }
    };
  }

  const EMPTY_RULE = {
    name: '', keywords: '', skills: '', minusWords: '', titleOnly: true, areaId: null, areaName: null, salaryFrom: null,
    onlyWithSalary: true, experience: null, workFormats: [], employmentForms: [], labels: [], periodDays: null,
    companyBlacklist: '', resumeHash: null,
    resumeTitle: null, letterTemplateId: null, mode: 'CONFIRM', dailyLimit: 30, intervalMinutes: 15, skipWithTest: true,
  };
  const RULE_FIELDS = ['name', 'keywords', 'skills', 'minusWords', 'titleOnly', 'areaId', 'areaName', 'salaryFrom', 'onlyWithSalary',
    'experience', 'workFormats', 'employmentForms', 'labels', 'periodDays', 'companyBlacklist', 'resumeHash', 'letterTemplateId', 'mode', 'dailyLimit', 'intervalMinutes', 'skipWithTest'];
  const payload = (rule) => Object.fromEntries(RULE_FIELDS.map((key) => [key, rule[key] === '' ? null : rule[key]]));

  async function ruleForm(seq, id) {
    paint(`${backBar('правило', '#/rules')}<div class="stack tight">${skeleton(3)}</div>`);
    const [info, letterList, loaded] = await Promise.all([meta(), api('GET', '/letters'), id ? api('GET', `/rules/${id}`) : null]);
    if (stale(seq)) return;

    const rule = Object.assign({}, EMPTY_RULE, loaded || { dailyLimit: info.defaultDailyLimit });
    for (const key of ['minusWords', 'companyBlacklist', 'keywords', 'skills']) rule[key] = rule[key] || '';
    let saved = JSON.stringify(payload(rule));
    let invalid = null;   // {field, message}
    let busy = false;
    let resumes = null;

    const dirty = () => JSON.stringify(payload(rule)) !== saved;
    const letterName = () => (letterList.find((l) => l.id === rule.letterTemplateId) || {}).name || 'без письма';
    const experienceName = () => (info.experience.find((o) => o.code === rule.experience) || {}).label || 'любой';
    /** Подписи выбранных кодов через запятую; если ничего не выбрано - слово «любой». */
    const chosen = (options, codes, none) =>
      options.filter((o) => codes.includes(o.code)).map((o) => o.label).join(', ') || none;
    const period = (days) => (!days ? 'любая' : days === 1 ? 'за сутки' : days === 7 ? 'за неделю' : days === 30 ? 'за месяц' : `за ${days} дня`);
    const multiPick = (title, text, options, field) => openSheet({
      title, text, multi: true,
      options: options.map((o) => ({ label: o.label, value: o.code, checked: rule[field].includes(o.code) })),
      onDone: (codes) => { rule[field] = codes; draw(); },
    });
    const bad = (field) => (invalid && invalid.field === field ? ' invalid' : '');
    const errorLine = (field) => (invalid && invalid.field === field ? `<span class="field-error">${esc(invalid.message)}</span>` : '');
    const input = (field, placeholder, extra) =>
      `<label class="sr" for="f-${field}">${placeholder}</label><input id="f-${field}" class="field${bad(field)}" type="text" placeholder="${placeholder}" value="${esc(rule[field] ?? '')}" data-field="${field}" autocomplete="off"${extra || ''}>${errorLine(field)}`;
    const pick = (field, label, value, act) =>
      `<button type="button" class="pick${bad(field)}" data-act="${act}"><span>${label}</span><span><b>${esc(value)}</b>${ICON.chevron}</span></button>${errorLine(field)}`;
    const toggle = (field, label) =>
      `<div class="toggle-row"><span>${label}</span>${switchBtn(rule[field], label, 'flip', ` data-field="${field}"`)}</div>`;

    const draw = () => {
      const scroll = window.scrollY;
      const primary = !id ? 'создать' : dirty() ? 'сохранить' : rule.enabled ? 'выключить' : 'включить';
      paint(`${backBar('правило', '#/rules', id ? `<button type="button" class="icon-btn trail" data-act="remove" aria-label="Удалить">${ICON.trash}</button>` : '')}
        <div class="stack tight">
          <span class="label">что искать</span>
          ${input('name', 'название правила (для себя)')}
          ${input('keywords', rule.titleOnly ? 'ключевые слова в названии вакансии' : 'ключевые слова')}
          <span class="hint">например: <code>devops OR sre</code></span>
          <div class="row">
            <button type="button" class="field${rule.areaId ? '' : ' placeholder'}" data-act="area">${esc(rule.areaName || (rule.areaId ? `регион ${rule.areaId}` : 'город'))}</button>
            <div><label class="sr" for="f-salaryFrom">зарплата от</label><input id="f-salaryFrom" class="field${bad('salaryFrom')}" type="text" inputmode="numeric" placeholder="зарплата от" value="${rule.salaryFrom ? money(rule.salaryFrom) : ''}" data-field="salaryFrom" autocomplete="off"></div>
          </div>
          ${errorLine('salaryFrom')}${errorLine('areaId')}
          ${toggle('onlyWithSalary', 'только с зарплатой')}
          ${toggle('titleOnly', 'только в названии')}
          ${input('skills', 'ключевые слова в описании')}
          <span class="hint">например: <code>kubernetes OR k8s</code></span>
          ${pick('workFormats', 'формат работы', chosen(info.workFormats, rule.workFormats, 'любой'), 'formats')}
          ${pick('employmentForms', 'занятость', chosen(info.employmentForms, rule.employmentForms, 'любая'), 'employment')}
          ${pick('experience', 'опыт', experienceName(), 'experience')}
          ${pick('periodDays', 'свежесть', period(rule.periodDays), 'period')}
          ${pick('labels', 'ещё фильтры', rule.labels.length ? `выбрано: ${rule.labels.length}` : 'нет', 'labels')}
          ${input('minusWords', 'минус-слова')}
          <span class="hint">например: <code>senior, lead</code></span>
          ${input('companyBlacklist', 'стоп-лист компаний')}
          <span class="hint">например: <code>аутстафф, агентство</code></span>
          <span class="label">чем откликаться</span>
          ${pick('resumeHash', 'резюме', rule.resumeTitle || 'не выбрано', 'resume')}
          ${pick('letterTemplateId', 'письмо', letterName(), 'letter')}
          <span class="label">как откликаться</span>
          ${pick('mode', 'режим', rule.mode === 'AUTO' ? 'откликаться самому' : 'спрашивать меня', 'mode')}
          ${pick('dailyLimit', 'лимит в день', String(rule.dailyLimit), 'limit')}
          ${pick('intervalMinutes', 'интервал', interval(rule.intervalMinutes), 'interval')}
          ${toggle('skipWithTest', 'пропускать с тестом')}
          ${invalid && !invalid.field ? `<span class="field-error">${esc(invalid.message)}</span>` : ''}
        </div>
        <div class="actions">
          <button type="button" class="btn" data-act="primary"${busy ? ' disabled' : ''}>${busy ? 'сохраняю…' : primary}</button>
          ${id ? `<div class="row"><button type="button" class="btn second mid" data-act="check">${ICON.play} проверить</button><button type="button" class="btn ghost-danger mid fixed" data-act="remove">удалить</button></div>` : ''}
        </div>`);
      window.scrollTo(0, scroll);
    };

    // Ввод в поле не перерисовывает экран - иначе на каждой букве терялся бы фокус.
    app.oninput = (event) => {
      const field = event.target.dataset && event.target.dataset.field;
      if (!field) return;
      if (field === 'salaryFrom') {
        const digits = event.target.value.replace(/\D/g, '').slice(0, 9);
        rule.salaryFrom = digits ? Number(digits) : null;
        event.target.value = digits ? money(digits) : '';
      } else {
        rule[field] = event.target.value;
      }
      const button = app.querySelector('[data-act="primary"]');
      if (button && id && !busy) button.textContent = dirty() ? 'сохранить' : rule.enabled ? 'выключить' : 'включить';
    };

    const fail = (e) => {
      busy = false;
      if (e.code === 'validation') { invalid = { field: e.field, message: e.message }; draw(); }
      else if (e.code === 'hh_session_expired') { toast('Сначала подключите аккаунт hh.', true); draw(); }
      else { toast(e.message, true); draw(); }
    };

    /** Сохраняет форму; возвращает id правила или null, если сервер не принял. */
    const save = async () => {
      busy = true; invalid = null; draw();
      try {
        const result = id ? await api('PUT', `/rules/${id}`, payload(rule)) : await api('POST', '/rules', payload(rule));
        Object.assign(rule, result);
        for (const key of ['minusWords', 'companyBlacklist', 'keywords', 'skills']) rule[key] = rule[key] || '';
        saved = JSON.stringify(payload(rule));
        busy = false;
        return result.id;
      } catch (e) { fail(e); return null; }
    };

    actions.flip = (el) => { rule[el.dataset.field] = !rule[el.dataset.field]; haptic(); draw(); };
    actions.primary = async () => {
      if (!id) {
        const newId = await save();
        if (newId) { haptic('medium'); location.replace(`#/rules/${newId}`); }
        return;
      }
      if (dirty()) {
        if (await save()) { toast('сохранено'); draw(); }
        return;
      }
      busy = true; invalid = null; draw();
      try {
        Object.assign(rule, await api('PUT', `/rules/${id}/enabled`, { enabled: !rule.enabled }));
        busy = false; haptic('medium'); draw();
      } catch (e) { fail(e); }
    };
    actions.check = async () => {
      if (dirty() && !(await save())) return;
      location.hash = `#/rules/${id}/preview`;
    };
    actions.remove = () => openSheet({
      title: 'удалить правило?',
      text: rule.name,
      confirm: { label: 'удалить', danger: true, run: async () => {
        try { await api('DELETE', `/rules/${id}`); location.hash = '#/rules'; } catch (e) { toast(e.message, true); }
      } },
    });
    actions.area = () => openSheet({
      title: 'город',
      options: [{ label: 'любой город', value: null, checked: !rule.areaId }]
        .concat(info.areas.map((a) => ({ label: a.name, value: a, checked: rule.areaId === a.id }))),
      search: { placeholder: 'начните вводить название', load: async (query) => {
        if (query.length < 2) {
          return [{ label: 'любой город', value: null }].concat(info.areas.map((a) => ({ label: a.name, value: a })));
        }
        const found = await api('GET', '/areas?q=' + encodeURIComponent(query));
        return found.length ? found.map((a) => ({ label: a.name, sub: a.parent, value: a })) : [{ label: 'ничего не нашлось', value: undefined }];
      } },
      onPick: (option) => {
        if (option.value === undefined) return;
        rule.areaId = option.value ? option.value.id : null;
        rule.areaName = option.value ? option.value.name : null;
        draw();
      },
    });
    actions.formats = () => multiPick('формат работы', 'можно несколько', info.workFormats, 'workFormats');
    actions.employment = () => multiPick('занятость', 'можно несколько', info.employmentForms, 'employmentForms');
    actions.labels = () => multiPick('ещё фильтры', null, info.labels, 'labels');
    actions.period = () => openSheet({
      title: 'свежесть вакансий',
      options: [{ label: 'любая', value: null, checked: !rule.periodDays }]
        .concat(info.periodsDays.map((d) => ({ label: period(d), value: d, checked: rule.periodDays === d }))),
      onPick: (option) => { rule.periodDays = option.value; draw(); },
    });
    actions.experience = () => openSheet({
      title: 'опыт в вакансии',
      options: [{ label: 'любой', value: null, checked: !rule.experience }]
        .concat(info.experience.map((o) => ({ label: o.label, value: o.code, checked: rule.experience === o.code }))),
      onPick: (option) => { rule.experience = option.value; draw(); },
    });
    actions.letter = () => openSheet({
      title: 'письмо',
      text: letterList.length ? null : 'писем нет - вкладка «письма»',
      options: [{ label: 'без письма', value: null, checked: !rule.letterTemplateId }]
        .concat(letterList.map((l) => ({ label: l.name, value: l.id, checked: rule.letterTemplateId === l.id }))),
      onPick: (option) => { rule.letterTemplateId = option.value; draw(); },
    });
    actions.mode = () => openSheet({
      title: 'режим',
      options: [
        { label: 'спрашивать меня', sub: 'отклик по кнопке', value: 'CONFIRM', checked: rule.mode === 'CONFIRM' },
        { label: 'откликаться самому', sub: 'отклик сразу', value: 'AUTO', checked: rule.mode === 'AUTO' },
      ],
      onPick: (option) => { rule.mode = option.value; draw(); },
    });
    actions.limit = () => openSheet({
      title: 'лимит в день',
      text: 'максимум hh - 200 в сутки',
      options: [10, 20, 30, 50, 100, 200].filter((n) => n <= info.maxDailyLimit)
        .map((n) => ({ label: String(n), value: n, checked: rule.dailyLimit === n })),
      onPick: (option) => { rule.dailyLimit = option.value; draw(); },
    });
    actions.interval = () => openSheet({
      title: 'интервал проверки',
      options: info.intervalsMinutes.map((m) => ({ label: interval(m), value: m, checked: rule.intervalMinutes === m })),
      onPick: (option) => { rule.intervalMinutes = option.value; draw(); },
    });
    actions.resume = async () => {
      try {
        resumes = resumes || await api('GET', '/account/resumes');
      } catch (e) {
        return toast(e.code === 'hh_session_expired' ? 'Сначала подключите аккаунт hh.' : e.message, true);
      }
      if (stale(seq)) return;
      openSheet({
        title: 'резюме',
        text: resumes.length ? null : 'на hh нет резюме',
        options: resumes.map((r) => ({ label: r.title, value: r, checked: rule.resumeHash === r.hash })),
        onPick: (option) => { rule.resumeHash = option.value.hash; rule.resumeTitle = option.value.title; draw(); },
      });
    };

    draw();
  }

  // ---------- проверка правила

  async function preview(seq, id) {
    const back = `#/rules/${id}`;
    paint(`${backBar('проверка', back)}<div class="count-bar"><h2 class="muted">проверяю…</h2></div><div class="stack">${skeleton(5)}</div>`);
    let list;
    try {
      list = await api('GET', `/rules/${id}/preview`);
    } catch (e) {
      if (stale(seq) || e.code === 'unauthorized' || e.code === 'forbidden') throw e;
      actions.retry = route;
      const expired = e.code === 'hh_session_expired';
      return paint(`${backBar('проверка', back)}${message(expired ? 'hh отключён' : e.code === 'hh_unavailable' ? 'hh не отвечает' : 'не получилось', e.message,
        expired ? '<a class="btn" href="#/connect">подключить</a>' : `<button type="button" class="btn" data-act="retry">${ICON.retry} повторить</button>`)}`);
    }
    if (stale(seq)) return;

    paint(`${backBar('проверка', back)}
      <div class="count-bar"><h2>${list.length ? `${list.length} ${plural(list.length, 'вакансия', 'вакансии', 'вакансий')}` : 'ничего не нашлось'}</h2><button type="button" data-act="retry">обновить</button></div>
      <div class="stack">${list.length ? list.map((v, i) =>
        `<a class="item link" href="${esc(v.url)}" data-act="open" data-i="${i}"><span class="item-title wrap">${esc(v.name)}</span><span class="item-sub">${esc([v.company, v.city].filter(Boolean).join(' · '))}</span>
          <div class="salary"><span>${esc(v.salary || 'не указана')}</span>${v.hasTest ? '<span class="badge">с тестом</span>' : ''}</div></a>`).join('')
        : '<span class="item-sub" style="padding:0 4px">ослабьте фильтры</span>'}</div>`);
    actions.retry = route;
    // Ссылки на hh открываем снаружи: внутри мини-приложения чужой сайт заменил бы его собой.
    actions.open = (el) => {
      const url = list[Number(el.dataset.i)].url;
      if (tg && tg.openLink) tg.openLink(url); else window.open(url, '_blank', 'noopener');
    };
  }

  // ---------- письма

  async function letters(seq) {
    const head = (withNew) => `<div class="title-bar"><h1>письма</h1>${withNew ? '<a href="#/letters/new">новое</a>' : ''}</div>`;
    paint(`${head(false)}<div class="stack">${skeleton(3)}</div>`, { nav: 'letters' });
    const list = await api('GET', '/letters');
    if (stale(seq)) return;
    if (!list.length) {
      return paint(`${head(false)}<div class="empty"><h2>писем нет</h2><a class="btn inline" href="#/letters/new">новое письмо</a></div>`, { nav: 'letters' });
    }
    paint(`${head(true)}<div class="stack">${list.map((l) =>
      `<a class="item link" href="#/letters/${l.id}"><span class="item-title">${esc(l.name)}</span><span class="clamp">${esc(l.body)}</span></a>`).join('')}</div>`, { nav: 'letters' });
  }

  async function letterForm(seq, id) {
    paint(`${backBar('письмо', '#/letters')}<div class="stack tight">${skeleton(2)}</div>`);
    const [info, list, account] = await Promise.all([meta(), id ? api('GET', '/letters') : [], api('GET', '/account')]);
    if (stale(seq)) return;
    const existing = id ? list.find((l) => String(l.id) === String(id)) : null;
    if (id && !existing) { location.replace('#/letters'); return; }

    const letter = { name: existing ? existing.name : '', body: existing ? existing.body : '' };
    let invalid = null;
    let busy = false;
    let sample = null;   // вакансия, на которой показан предпросмотр

    // Подстановку для предпросмотра делаем здесь же, чтобы подсветить вставленное. Вакансию
    // и проверку алиасов даёт сервер - он же в итоге и соберёт настоящее письмо.
    const highlighted = () => {
      if (!sample) return '';
      const values = { company_name: sample.company, vacancy_name: sample.name, salary: sample.salary, city: sample.city, my_name: account.ownerName || '' };
      return esc(letter.body).replace(/\[([a-z_]+)\]/g, (whole, alias) =>
        (alias in values ? `<mark>${esc(values[alias])}</mark>` : whole));
    };

    const draw = () => {
      paint(`${backBar('письмо', '#/letters', id ? `<button type="button" class="icon-btn trail" data-act="remove" aria-label="Удалить">${ICON.trash}</button>` : '')}
        <div class="stack tight">
          <label class="sr" for="f-name">название</label>
          <input id="f-name" class="field${invalid && invalid.field === 'name' ? ' invalid' : ''}" type="text" placeholder="название" value="${esc(letter.name)}" data-field="name" autocomplete="off">
          ${invalid && invalid.field === 'name' ? `<span class="field-error">${esc(invalid.message)}</span>` : ''}
          <label class="sr" for="f-body">текст письма</label>
          <textarea id="f-body" class="field${invalid && invalid.field === 'body' ? ' invalid' : ''}" style="height:236px" placeholder="текст письма" data-field="body">${esc(letter.body)}</textarea>
          <span id="body-error" class="field-error"${invalid && invalid.field === 'body' ? '' : ' hidden'}>${invalid && invalid.field === 'body' ? esc(invalid.message) : ''}</span>
          <div class="chips">${info.aliases.map((a) =>
            `<button type="button" class="chip" data-act="alias" data-name="${a.name}" title="${esc(a.description)}">[${a.name}]</button>`).join('')}</div>
          <span class="label">предпросмотр</span>
          <div id="preview" class="preview">${highlighted() || '<span>—</span>'}</div>
          <div style="padding-top:10px"><button type="button" class="btn" data-act="save"${busy ? ' disabled' : ''}>${busy ? 'сохраняю…' : 'сохранить'}</button></div>
        </div>`);
    };

    let timer, previewSeq = 0;
    const refresh = () => {
      clearTimeout(timer);
      timer = setTimeout(async () => {
        const mine = ++previewSeq;
        const errorEl = document.getElementById('body-error');
        const previewEl = document.getElementById('preview');
        if (!letter.body.trim()) { if (previewEl) previewEl.innerHTML = '<span>—</span>'; return; }
        try {
          const result = await api('POST', '/letters/preview', { body: letter.body });
          if (mine !== previewSeq || stale(seq)) return;
          sample = result.vacancy;
          if (previewEl) previewEl.innerHTML = highlighted();
          if (errorEl) errorEl.hidden = true;
        } catch (e) {
          if (mine !== previewSeq || stale(seq) || !errorEl) return;
          errorEl.textContent = e.message;
          errorEl.hidden = false;
        }
      }, 450);
    };

    app.oninput = (event) => {
      const field = event.target.dataset && event.target.dataset.field;
      if (!field) return;
      letter[field] = event.target.value;
      if (field === 'body') refresh();
    };

    actions.alias = (el) => {
      const area = document.getElementById('f-body');
      const token = `[${el.dataset.name}]`;
      const start = area.selectionStart ?? area.value.length, end = area.selectionEnd ?? area.value.length;
      area.value = area.value.slice(0, start) + token + area.value.slice(end);
      area.focus();
      area.setSelectionRange(start + token.length, start + token.length);
      letter.body = area.value;
      haptic();
      refresh();
    };
    actions.save = async () => {
      busy = true; invalid = null; draw();
      try {
        await (id ? api('PUT', `/letters/${id}`, letter) : api('POST', '/letters', letter));
        haptic('medium');
        location.hash = '#/letters';
      } catch (e) {
        busy = false;
        if (e.code === 'validation') invalid = { field: e.field, message: e.message }; else toast(e.message, true);
        if (!stale(seq)) draw();
      }
    };
    actions.remove = () => openSheet({
      title: 'удалить письмо?',
      text: letter.name,
      confirm: { label: 'удалить', danger: true, run: async () => {
        try { await api('DELETE', `/letters/${id}`); location.hash = '#/letters'; } catch (e) { toast(e.message, true); }
      } },
    });

    draw();
    if (letter.body) refresh();
  }

  // ---------- статистика

  async function stats(seq) {
    const head = '<div class="title-bar"><h1>статистика</h1></div>';
    paint(`${head}<div class="stack">${skeleton(2)}</div>`, { nav: 'stats' });
    const data = await api('GET', '/stats');
    if (stale(seq)) return;
    const tile = (value, label) => `<div class="tile"><b>${money(value)}</b><span>${label}</span></div>`;
    const tiles = `<div class="tiles">${tile(data.sentTotal, 'откликов всего')}${tile(data.sentWeek, 'за 7 дней')}${tile(data.manual, 'пришли ссылкой')}${tile(data.skipped, 'пропущено')}</div>`;
    if (!data.recent.length) {
      return paint(`${head}<div class="stack">${tiles}</div><div class="empty"><h2>откликов ещё не было</h2><a class="btn inline" href="#/rules">к правилам</a></div>`, { nav: 'stats' });
    }
    paint(`${head}<div class="stack">${tiles}<span class="label">последние</span><div class="block">${logRows(data.recent)}</div></div>`, { nav: 'stats' });
  }

  // ---------- запуск

  if (tg) {
    try {
      tg.ready();
      tg.expand();
      if (tg.setHeaderColor) tg.setHeaderColor('#000000');
      if (tg.setBackgroundColor) tg.setBackgroundColor('#000000');
      if (tg.BackButton) tg.BackButton.onClick(() => { if (backHref) location.hash = backHref; });
    } catch (_) { /* старый клиент без части методов */ }
  }

  if (!initData && !LOCAL) {
    paint(message('откройте в Telegram', 'Это мини-приложение бота: оно работает только внутри Telegram.'));
  } else {
    // oninput вешают экраны с формами; при уходе с экрана его нужно снять.
    window.addEventListener('hashchange', () => { app.oninput = null; route(); });
    route();
  }
})();
