/* =====================================================================
   HomeVault — offline home inventory (insurance) + smart pantry
   Vanilla ES module. All data lives in IndexedDB on the device.
   ===================================================================== */

/* ----------------------------- IndexedDB ----------------------------- */
const DB_NAME = 'homevault';
const DB_VERSION = 1;

function openDB() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = (e) => {
      const db = e.target.result;
      if (!db.objectStoreNames.contains('items')) db.createObjectStore('items', { keyPath: 'id' });
      if (!db.objectStoreNames.contains('groceries')) db.createObjectStore('groceries', { keyPath: 'id' });
      if (!db.objectStoreNames.contains('meta')) db.createObjectStore('meta', { keyPath: 'key' });
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

let _db;
async function db() { return (_db ||= await openDB()); }

function tx(store, mode = 'readonly') {
  return db().then((d) => d.transaction(store, mode).objectStore(store));
}
function reqp(request) {
  return new Promise((res, rej) => { request.onsuccess = () => res(request.result); request.onerror = () => rej(request.error); });
}
async function dbGetAll(store) { return reqp((await tx(store)).getAll()); }
async function dbPut(store, value) { return reqp((await tx(store, 'readwrite')).put(value)); }
async function dbDelete(store, key) { return reqp((await tx(store, 'readwrite')).delete(key)); }
async function metaGet(key, fallback) {
  const r = await reqp((await tx('meta')).get(key));
  return r ? r.value : fallback;
}
async function metaSet(key, value) { return dbPut('meta', { key, value }); }

/* ----------------------------- Utilities ----------------------------- */
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const uid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const state = {
  currency: 'USD',
  notifyDays: 3,
  pantryLocation: 'fridge',
  items: [],
  groceries: [],
  recipes: [],
  recipeFilter: 'all',
  invRoom: 'all',
  search: '',
  view: 'inventory'
};

const LOCATIONS = [
  { id: 'fridge', label: 'Fridge', emoji: '🧊' },
  { id: 'freezer', label: 'Freezer', emoji: '❄️' },
  { id: 'pantry', label: 'Pantry', emoji: '🧺' },
  { id: 'longterm', label: 'Long-term', emoji: '📦' }
];

function money(n) {
  if (n == null || n === '' || isNaN(n)) return '—';
  try { return new Intl.NumberFormat(undefined, { style: 'currency', currency: state.currency }).format(Number(n)); }
  catch { return `${state.currency} ${Number(n).toFixed(2)}`; }
}
function fmtDate(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}
function daysUntil(iso) {
  if (!iso) return null;
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const t = new Date(iso); t.setHours(0, 0, 0, 0);
  return Math.round((t - today) / 86400000);
}

function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.remove('hidden');
  clearTimeout(toast._t);
  toast._t = setTimeout(() => t.classList.add('hidden'), 2600);
}

/* Compress an image File to a small JPEG data URL so it fits in IndexedDB. */
function compressImage(file, maxDim = 1280, quality = 0.7) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    const url = URL.createObjectURL(file);
    img.onload = () => {
      URL.revokeObjectURL(url);
      let { width, height } = img;
      if (width > height && width > maxDim) { height = Math.round(height * maxDim / width); width = maxDim; }
      else if (height > maxDim) { width = Math.round(width * maxDim / height); height = maxDim; }
      const canvas = document.createElement('canvas');
      canvas.width = width; canvas.height = height;
      canvas.getContext('2d').drawImage(img, 0, 0, width, height);
      resolve(canvas.toDataURL('image/jpeg', quality));
    };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Could not read image')); };
    img.src = url;
  });
}

function pickImage({ multiple = false } = {}) {
  return new Promise((resolve) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.capture = 'environment';
    if (multiple) input.multiple = true;
    input.onchange = () => resolve(multiple ? [...input.files] : input.files[0]);
    input.click();
  });
}

/* ------------------------------- Modal ------------------------------- */
function openModal(html) {
  const root = $('#modal-root');
  root.innerHTML = `<div class="modal"><div class="grabber"></div>${html}</div>`;
  root.classList.remove('hidden');
  root.onclick = (e) => { if (e.target === root) closeModal(); };
  return root.querySelector('.modal');
}
function closeModal() {
  const root = $('#modal-root');
  root.classList.add('hidden');
  root.innerHTML = '';
}

/* ============================ INVENTORY ============================== */
function inventoryRooms() {
  const rooms = new Set();
  state.items.forEach((i) => { if (i.room) rooms.add(i.room); });
  return [...rooms].sort();
}

function renderInventory() {
  const items = state.items
    .filter((i) => state.invRoom === 'all' || i.room === state.invRoom)
    .filter((i) => !state.search || (i.name + ' ' + (i.category || '') + ' ' + (i.room || '')).toLowerCase().includes(state.search))
    .sort((a, b) => (b.price || 0) - (a.price || 0));

  const total = state.items.reduce((s, i) => s + (Number(i.price) || 0), 0);
  $('#inv-count').textContent = state.items.length;
  $('#inv-total').textContent = money(total);
  $('#inv-rooms').textContent = inventoryRooms().length;

  // Room filter chips
  const filter = $('#inv-room-filter');
  const rooms = inventoryRooms();
  filter.innerHTML = [`<button class="chip ${state.invRoom === 'all' ? 'active' : ''}" data-room="all">All rooms</button>`]
    .concat(rooms.map((r) => `<button class="chip ${state.invRoom === r ? 'active' : ''}" data-room="${esc(r)}">${esc(r)}</button>`))
    .join('');
  $$('#inv-room-filter .chip').forEach((c) => c.onclick = () => { state.invRoom = c.dataset.room; renderInventory(); });

  const list = $('#inv-list');
  $('#inv-empty').classList.toggle('hidden', state.items.length !== 0);
  list.innerHTML = items.map((i) => `
    <li class="row" data-id="${i.id}">
      <div class="row__thumb" ${i.photos?.[0] ? `style="background-image:url('${i.photos[0]}')"` : ''}>${i.photos?.[0] ? '' : categoryEmoji(i.category)}</div>
      <div class="row__main">
        <div class="row__title"><span class="trunc">${esc(i.name)}</span></div>
        <div class="row__sub">${esc(i.room || 'Unassigned')}${i.category ? ' · ' + esc(i.category) : ''}${i.invoices?.length ? ' · 📄' + i.invoices.length : ''}</div>
      </div>
      <div class="row__side"><div class="amount">${money(i.price)}</div></div>
    </li>`).join('');
  $$('#inv-list .row').forEach((r) => r.onclick = () => showItemDetail(r.dataset.id));
}

function categoryEmoji(cat) {
  const m = { Furniture: '🛋️', Electronics: '💻', Appliances: '🔌', Kitchen: '🍽️', Clothing: '👕', Jewelry: '💍', Tools: '🛠️', Art: '🖼️', Other: '📦' };
  return m[cat] || '📦';
}

const CATEGORIES = ['Furniture', 'Electronics', 'Appliances', 'Kitchen', 'Clothing', 'Jewelry', 'Tools', 'Art', 'Other'];

function itemFormModal(existing) {
  const it = existing || { id: uid(), photos: [], invoices: [] };
  const rooms = inventoryRooms();
  const modal = openModal(`
    <h2>${existing ? 'Edit item' : 'Add item'}</h2>
    <form id="item-form">
      <label class="field">Name
        <input name="name" required value="${esc(it.name || '')}" placeholder="e.g. Samsung 55&quot; QLED TV" />
      </label>
      <div class="field-2">
        <label class="field">Category
          <select name="category">${CATEGORIES.map((c) => `<option ${it.category === c ? 'selected' : ''}>${c}</option>`).join('')}</select>
        </label>
        <label class="field">Room
          <input name="room" list="room-list" value="${esc(it.room || '')}" placeholder="Living room" />
          <datalist id="room-list">${rooms.map((r) => `<option value="${esc(r)}">`).join('')}</datalist>
        </label>
      </div>
      <div class="field-2">
        <label class="field">Price (${state.currency})
          <input name="price" type="number" step="0.01" inputmode="decimal" value="${it.price ?? ''}" placeholder="0.00" />
        </label>
        <label class="field">Purchase date
          <input name="purchased" type="date" value="${it.purchased || ''}" />
        </label>
      </div>
      <div class="field-2">
        <label class="field">Brand / model
          <input name="model" value="${esc(it.model || '')}" />
        </label>
        <label class="field">Serial number
          <input name="serial" value="${esc(it.serial || '')}" />
        </label>
      </div>
      <label class="field">Notes
        <textarea name="notes" rows="2" placeholder="Warranty, condition…">${esc(it.notes || '')}</textarea>
      </label>

      <label class="field">Item photos
        <div class="photo-strip" id="item-photos"></div>
      </label>
      <label class="field">Invoices / receipts
        <div class="photo-strip" id="item-invoices"></div>
      </label>

      <div class="btn-row" style="margin-top:8px">
        <button type="submit" class="btn">Save item</button>
        ${existing ? '<button type="button" class="btn danger" id="item-delete">Delete</button>' : ''}
      </div>
    </form>
  `);

  const photos = [...(it.photos || [])];
  const invoices = [...(it.invoices || [])];

  function drawStrip(elId, arr) {
    const el = $('#' + elId, modal);
    el.innerHTML = arr.map((src, idx) => `<div class="ph" style="background-image:url('${src}')"><button type="button" data-idx="${idx}">✕</button></div>`).join('')
      + `<button type="button" class="photo-add">＋</button>`;
    $('.photo-add', el).onclick = async () => {
      const files = await pickImage({ multiple: true });
      for (const f of [].concat(files).filter(Boolean)) arr.push(await compressImage(f));
      drawStrip(elId, arr);
    };
    $$('.ph button', el).forEach((b) => b.onclick = () => { arr.splice(+b.dataset.idx, 1); drawStrip(elId, arr); });
  }
  drawStrip('item-photos', photos);
  drawStrip('item-invoices', invoices);

  $('#item-form', modal).onsubmit = async (e) => {
    e.preventDefault();
    const f = e.target;
    Object.assign(it, {
      name: f.name.value.trim(),
      category: f.category.value,
      room: f.room.value.trim(),
      price: f.price.value ? Number(f.price.value) : null,
      purchased: f.purchased.value || null,
      model: f.model.value.trim(),
      serial: f.serial.value.trim(),
      notes: f.notes.value.trim(),
      photos, invoices,
      updated: Date.now()
    });
    await dbPut('items', it);
    state.items = await dbGetAll('items');
    closeModal();
    renderInventory();
    if (state.view === 'map') renderMap();
    toast('Item saved');
  };

  if (existing) $('#item-delete', modal).onclick = async () => {
    if (!confirm('Delete this item?')) return;
    await dbDelete('items', it.id);
    state.items = await dbGetAll('items');
    closeModal(); renderInventory();
    if (state.view === 'map') renderMap();
    toast('Item deleted');
  };
}

function showItemDetail(id) {
  const it = state.items.find((i) => i.id === id);
  if (!it) return;
  const imgs = [...(it.photos || []), ...(it.invoices || [])];
  const modal = openModal(`
    <h2>${esc(it.name)}</h2>
    ${imgs.map((s) => `<img class="detail-img" src="${s}" alt="" />`).join('')}
    <dl class="detail-grid">
      <dt>Value</dt><dd>${money(it.price)}</dd>
      <dt>Category</dt><dd>${esc(it.category || '—')}</dd>
      <dt>Room</dt><dd>${esc(it.room || '—')}</dd>
      <dt>Purchased</dt><dd>${it.purchased ? fmtDate(it.purchased) : '—'}</dd>
      ${it.model ? `<dt>Model</dt><dd>${esc(it.model)}</dd>` : ''}
      ${it.serial ? `<dt>Serial</dt><dd>${esc(it.serial)}</dd>` : ''}
      ${it.notes ? `<dt>Notes</dt><dd>${esc(it.notes)}</dd>` : ''}
    </dl>
    <div class="btn-row">
      <button class="btn" id="edit-item">Edit</button>
    </div>
  `);
  $('#edit-item', modal).onclick = () => { closeModal(); itemFormModal(it); };
}

/* ============================== MAP ================================= */
/* Rooms are rectangles drawn on a canvas. Each room has a name; items are
   linked to a room by matching item.room === room.name. */
const map = { rooms: [], mode: 'idle', drag: null, longTimer: null };

async function loadMap() { map.rooms = await metaGet('mapRooms', []); }
async function saveMap() { await metaSet('mapRooms', map.rooms); }

function canvasPoint(canvas, ev) {
  const r = canvas.getBoundingClientRect();
  return {
    x: (ev.clientX - r.left) * (canvas.width / r.width),
    y: (ev.clientY - r.top) * (canvas.height / r.height)
  };
}
function roomAt(p) {
  for (let i = map.rooms.length - 1; i >= 0; i--) {
    const r = map.rooms[i];
    if (p.x >= r.x && p.x <= r.x + r.w && p.y >= r.y && p.y <= r.y + r.h) return r;
  }
  return null;
}
function itemsInRoom(name) { return state.items.filter((i) => i.room === name); }

function renderMap() {
  const canvas = $('#map-canvas');
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  map.rooms.forEach((r) => {
    const items = itemsInRoom(r.name);
    const value = items.reduce((s, i) => s + (Number(i.price) || 0), 0);
    ctx.fillStyle = 'rgba(79,140,255,0.14)';
    ctx.strokeStyle = '#4f8cff';
    ctx.lineWidth = 2;
    ctx.fillRect(r.x, r.y, r.w, r.h);
    ctx.strokeRect(r.x, r.y, r.w, r.h);
    ctx.fillStyle = '#e8ecf3';
    ctx.font = 'bold 22px system-ui, sans-serif';
    ctx.fillText(r.name, r.x + 12, r.y + 30);
    ctx.fillStyle = '#9aa4b2';
    ctx.font = '18px system-ui, sans-serif';
    ctx.fillText(`${items.length} items · ${money(value)}`, r.x + 12, r.y + 54);
  });

  const legend = $('#map-legend');
  const placed = state.items.filter((i) => i.room && map.rooms.some((r) => r.name === i.room)).length;
  legend.innerHTML = `<span class="lg"><span class="dot"></span>${map.rooms.length} rooms · ${placed}/${state.items.length} items placed</span>`;
}

function setupMapInteraction() {
  const canvas = $('#map-canvas');

  canvas.addEventListener('pointerdown', (e) => {
    canvas.setPointerCapture(e.pointerId);
    const p = canvasPoint(canvas, e);

    if (map.mode === 'addRoom') {
      const name = prompt('Room name?');
      if (name) {
        map.rooms.push({ id: uid(), name: name.trim(), x: Math.max(0, p.x - 90), y: Math.max(0, p.y - 60), w: 180, h: 120 });
        saveMap(); renderMap();
      }
      map.mode = 'idle';
      $('#map-add-room').classList.remove('active');
      return;
    }

    const room = roomAt(p);
    if (room) {
      map.drag = { room, offX: p.x - room.x, offY: p.y - room.y, moved: false, start: p };
      map.longTimer = setTimeout(() => { map.drag = null; roomMenu(room); }, 550);
    }
  });

  canvas.addEventListener('pointermove', (e) => {
    if (!map.drag) return;
    const p = canvasPoint(canvas, e);
    if (Math.hypot(p.x - map.drag.start.x, p.y - map.drag.start.y) > 6) {
      map.drag.moved = true;
      clearTimeout(map.longTimer);
    }
    map.drag.room.x = Math.max(0, Math.min(canvas.width - map.drag.room.w, p.x - map.drag.offX));
    map.drag.room.y = Math.max(0, Math.min(canvas.height - map.drag.room.h, p.y - map.drag.offY));
    renderMap();
  });

  canvas.addEventListener('pointerup', (e) => {
    clearTimeout(map.longTimer);
    if (map.drag) {
      if (map.drag.moved) saveMap();
      else roomItemsModal(map.drag.room);
      map.drag = null;
    }
  });

  $('#map-add-room').onclick = (e) => {
    map.mode = map.mode === 'addRoom' ? 'idle' : 'addRoom';
    e.currentTarget.classList.toggle('active', map.mode === 'addRoom');
    toast(map.mode === 'addRoom' ? 'Tap the map to drop a room' : 'Cancelled');
  };
  $('#map-place').onclick = placeItemFlow;
  $('#map-clear').onclick = () => { renderMap(); toast('View refreshed'); };
}

function roomMenu(room) {
  const modal = openModal(`
    <h2>${esc(room.name)}</h2>
    <div class="btn-row" style="flex-direction:column">
      <button class="btn ghost" id="rn">Rename room</button>
      <button class="btn ghost" id="vw">View items (${itemsInRoom(room.name).length})</button>
      <button class="btn danger" id="rm">Remove room</button>
    </div>
  `);
  $('#rn', modal).onclick = () => {
    const name = prompt('New room name?', room.name);
    if (name) {
      const old = room.name; room.name = name.trim();
      // keep item links in sync
      state.items.filter((i) => i.room === old).forEach(async (i) => { i.room = room.name; await dbPut('items', i); });
      saveMap(); closeModal(); renderMap(); renderInventory();
    }
  };
  $('#vw', modal).onclick = () => { closeModal(); roomItemsModal(room); };
  $('#rm', modal).onclick = () => {
    if (!confirm(`Remove "${room.name}" from the map? Items stay in your inventory.`)) return;
    map.rooms = map.rooms.filter((r) => r.id !== room.id);
    saveMap(); closeModal(); renderMap();
  };
}

function roomItemsModal(room) {
  const items = itemsInRoom(room.name);
  const value = items.reduce((s, i) => s + (Number(i.price) || 0), 0);
  const modal = openModal(`
    <h2>${esc(room.name)} · ${money(value)}</h2>
    <ul class="list">
      ${items.length ? items.map((i) => `
        <li class="row" data-id="${i.id}">
          <div class="row__thumb" ${i.photos?.[0] ? `style="background-image:url('${i.photos[0]}')"` : ''}>${i.photos?.[0] ? '' : categoryEmoji(i.category)}</div>
          <div class="row__main"><div class="row__title"><span class="trunc">${esc(i.name)}</span></div><div class="row__sub">${esc(i.category || '')}</div></div>
          <div class="row__side"><div class="amount">${money(i.price)}</div></div>
        </li>`).join('') : '<p class="empty">No items here yet. Use “Place item”.</p>'}
    </ul>
  `);
  $$('.row', modal).forEach((r) => r.onclick = () => { closeModal(); showItemDetail(r.dataset.id); });
}

function placeItemFlow() {
  if (!map.rooms.length) { toast('Add a room first'); return; }
  if (!state.items.length) { toast('Add an item first'); return; }
  const modal = openModal(`
    <h2>Place item in a room</h2>
    <label class="field">Item
      <select id="pi-item">${state.items.map((i) => `<option value="${i.id}">${esc(i.name)}${i.room ? ' (now: ' + esc(i.room) + ')' : ''}</option>`).join('')}</select>
    </label>
    <label class="field">Room
      <select id="pi-room">${map.rooms.map((r) => `<option value="${esc(r.name)}">${esc(r.name)}</option>`).join('')}</select>
    </label>
    <button class="btn" id="pi-save">Place</button>
  `);
  $('#pi-save', modal).onclick = async () => {
    const it = state.items.find((i) => i.id === $('#pi-item', modal).value);
    it.room = $('#pi-room', modal).value;
    await dbPut('items', it);
    state.items = await dbGetAll('items');
    closeModal(); renderMap(); renderInventory();
    toast(`Placed in ${it.room}`);
  };
}

/* ============================== PANTRY ============================== */
function expiryBadge(g) {
  const d = daysUntil(g.expires);
  if (d == null) return '<span class="badge muted">no date</span>';
  if (d < 0) return `<span class="badge bad">expired ${-d}d ago</span>`;
  if (d === 0) return '<span class="badge bad">today</span>';
  if (d <= state.notifyDays) return `<span class="badge warn">${d}d left</span>`;
  return `<span class="badge good">${d}d</span>`;
}

function renderPantrySeg() {
  const seg = $('#pantry-locations');
  seg.innerHTML = LOCATIONS.map((l) => {
    const count = state.groceries.filter((g) => g.location === l.id).length;
    return `<button class="${state.pantryLocation === l.id ? 'active' : ''}" data-loc="${l.id}">
      <span class="seg-emoji">${l.emoji}</span>${l.label}<span class="seg-count">${count}</span></button>`;
  }).join('');
  $$('#pantry-locations button').forEach((b) => b.onclick = () => { state.pantryLocation = b.dataset.loc; renderPantry(); });
}

function renderPantry() {
  renderPantrySeg();
  const list = state.groceries
    .filter((g) => g.location === state.pantryLocation)
    .filter((g) => !state.search || g.name.toLowerCase().includes(state.search))
    .sort((a, b) => (daysUntil(a.expires) ?? 9e9) - (daysUntil(b.expires) ?? 9e9));

  $('#pantry-empty').classList.toggle('hidden', list.length !== 0);
  $('#pantry-list').innerHTML = list.map((g) => `
    <li class="row" data-id="${g.id}">
      <div class="row__thumb" ${g.photo ? `style="background-image:url('${g.photo}')"` : ''}>${g.photo ? '' : '🛒'}</div>
      <div class="row__main">
        <div class="row__title"><span class="trunc">${esc(g.name)}</span></div>
        <div class="row__sub">${g.qty ? esc(g.qty) + ' · ' : ''}${g.expires ? 'exp ' + fmtDate(g.expires) : 'no expiry'}</div>
      </div>
      <div class="row__side">${expiryBadge(g)}</div>
    </li>`).join('');
  $$('#pantry-list .row').forEach((r) => r.onclick = () => groceryFormModal(state.groceries.find((g) => g.id === r.dataset.id)));
}

function groceryFormModal(existing, prefill = {}) {
  const g = existing || { id: uid(), location: state.pantryLocation, ...prefill };
  const modal = openModal(`
    <h2>${existing ? 'Edit product' : 'Add product'}</h2>
    <form id="g-form">
      <label class="field">Name
        <input name="name" required value="${esc(g.name || '')}" placeholder="e.g. Greek yogurt" />
      </label>
      <div class="field-2">
        <label class="field">Quantity
          <input name="qty" value="${esc(g.qty || '')}" placeholder="1 pack, 500g…" />
        </label>
        <label class="field">Location
          <select name="location">${LOCATIONS.map((l) => `<option value="${l.id}" ${g.location === l.id ? 'selected' : ''}>${l.emoji} ${l.label}</option>`).join('')}</select>
        </label>
      </div>
      <label class="field">Expiration date
        <input name="expires" type="date" value="${g.expires || ''}" />
      </label>
      <label class="field">Photo
        <div class="photo-strip" id="g-photo"></div>
      </label>
      <div class="btn-row" style="margin-top:8px">
        <button type="submit" class="btn">Save</button>
        ${existing ? '<button type="button" class="btn danger" id="g-del">Delete</button>' : ''}
      </div>
    </form>
  `);

  let photo = g.photo || null;
  function drawPhoto() {
    const el = $('#g-photo', modal);
    el.innerHTML = (photo ? `<div class="ph" style="background-image:url('${photo}')"><button type="button" id="g-photo-x">✕</button></div>` : '')
      + `<button type="button" class="photo-add">📷</button>`;
    $('.photo-add', el).onclick = async () => { const f = await pickImage(); if (f) { photo = await compressImage(f); drawPhoto(); } };
    if (photo) $('#g-photo-x', el).onclick = () => { photo = null; drawPhoto(); };
  }
  drawPhoto();

  $('#g-form', modal).onsubmit = async (e) => {
    e.preventDefault();
    const f = e.target;
    Object.assign(g, {
      name: f.name.value.trim(), qty: f.qty.value.trim(),
      location: f.location.value, expires: f.expires.value || null,
      photo, updated: Date.now()
    });
    await dbPut('groceries', g);
    state.groceries = await dbGetAll('groceries');
    state.pantryLocation = g.location;
    closeModal(); renderPantry(); renderRecipes();
    toast('Product saved');
  };
  if (existing) $('#g-del', modal).onclick = async () => {
    if (!confirm('Delete this product?')) return;
    await dbDelete('groceries', g.id);
    state.groceries = await dbGetAll('groceries');
    closeModal(); renderPantry(); renderRecipes();
    toast('Product deleted');
  };
}

function showExpiring() {
  const soon = state.groceries
    .filter((g) => { const d = daysUntil(g.expires); return d != null && d <= state.notifyDays; })
    .sort((a, b) => (daysUntil(a.expires) ?? 0) - (daysUntil(b.expires) ?? 0));
  openModal(`
    <h2>Expiring soon (${soon.length})</h2>
    <ul class="list">
      ${soon.length ? soon.map((g) => `
        <li class="row">
          <div class="row__thumb" ${g.photo ? `style="background-image:url('${g.photo}')"` : ''}>${g.photo ? '' : '🛒'}</div>
          <div class="row__main"><div class="row__title"><span class="trunc">${esc(g.name)}</span></div>
          <div class="row__sub">${LOCATIONS.find((l) => l.id === g.location)?.label || ''}</div></div>
          <div class="row__side">${expiryBadge(g)}</div>
        </li>`).join('') : '<p class="empty">Nothing expiring within ' + state.notifyDays + ' days. 🎉</p>'}
    </ul>
  `);
}

/* --------------------------- Barcode scan --------------------------- */
async function scanProduct() {
  // Path 1: native BarcodeDetector + live camera (Chrome on Android/S21).
  if ('BarcodeDetector' in window) {
    try {
      const formats = await window.BarcodeDetector.getSupportedFormats();
      return liveScan(new window.BarcodeDetector({ formats }));
    } catch { /* fall through */ }
  }
  // Path 2: no detector — take a photo and fill the name manually.
  toast('Live scanner unavailable — add a photo + name');
  const f = await pickImage();
  groceryFormModal(null, f ? { photo: await compressImage(f) } : {});
}

function liveScan(detector) {
  const modal = openModal(`
    <h2>Scan barcode</h2>
    <div class="scanner">
      <video id="scan-video" playsinline muted></video>
      <p class="scanner-status" id="scan-status">Point the camera at a barcode…</p>
      <div class="btn-row">
        <button class="btn ghost" id="scan-manual">Enter manually</button>
        <button class="btn ghost" id="scan-cancel">Cancel</button>
      </div>
    </div>
  `);
  const video = $('#scan-video', modal);
  let stream, raf, stopped = false;

  function stop() {
    stopped = true;
    cancelAnimationFrame(raf);
    if (stream) stream.getTracks().forEach((t) => t.stop());
  }
  $('#scan-cancel', modal).onclick = () => { stop(); closeModal(); };
  $('#scan-manual', modal).onclick = () => { stop(); closeModal(); groceryFormModal(null); };

  navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
    .then((s) => { stream = s; video.srcObject = s; return video.play(); })
    .then(() => loop())
    .catch(() => { $('#scan-status', modal).textContent = 'Camera blocked. Use “Enter manually”.'; });

  async function loop() {
    if (stopped) return;
    try {
      const codes = await detector.detect(video);
      if (codes.length) {
        const code = codes[0].rawValue;
        stop();
        $('#scan-status', modal) && ($('#scan-status', modal).textContent = 'Found: ' + code);
        const info = await lookupBarcode(code);
        closeModal();
        groceryFormModal(null, { name: info?.name || '', barcode: code });
        if (!info?.name) toast('Code ' + code + ' — type the name');
        return;
      }
    } catch { /* keep trying */ }
    raf = requestAnimationFrame(loop);
  }
}

/* Optional online name lookup via Open Food Facts (no key). Fails silently offline. */
async function lookupBarcode(code) {
  try {
    const r = await fetch(`https://world.openfoodfacts.org/api/v0/product/${encodeURIComponent(code)}.json`, { signal: AbortSignal.timeout(4000) });
    const j = await r.json();
    if (j.status === 1) {
      const p = j.product;
      return { name: p.product_name || p.generic_name || '', qty: p.quantity || '' };
    }
  } catch { /* offline or not found */ }
  return null;
}

/* ============================== RECIPES ============================= */
function pantryIngredients() {
  // Normalised set of words from grocery names, for loose matching.
  const set = new Set();
  state.groceries.forEach((g) => {
    g.name.toLowerCase().split(/[^a-z]+/).forEach((w) => { if (w.length > 2) set.add(w); });
  });
  return set;
}

function recipeMatch(recipe, have) {
  const matched = recipe.ingredients.filter((ing) =>
    ing.toLowerCase().split(/[^a-z]+/).some((w) => w.length > 2 && [...have].some((h) => h.includes(w) || w.includes(h)))
  );
  return { have: matched.length, total: recipe.ingredients.length, missing: recipe.ingredients.filter((i) => !matched.includes(i)) };
}

function renderRecipes() {
  const have = pantryIngredients();
  const scored = state.recipes.map((r) => ({ r, m: recipeMatch(r, have) }))
    .map((x) => ({ ...x, pct: x.m.total ? x.m.have / x.m.total : 0 }))
    .sort((a, b) => b.pct - a.pct || a.r.time - b.r.time);

  const filtered = scored.filter(({ pct, m }) => {
    if (state.recipeFilter === 'ready') return m.have === m.total;
    if (state.recipeFilter === 'almost') return m.missing.length > 0 && m.missing.length <= 2 && pct > 0;
    return pct > 0 || state.recipeFilter === 'all';
  });

  $('#recipe-list').innerHTML = filtered.map(({ r, m, pct }) => {
    const cls = m.have === m.total ? 'good' : m.missing.length <= 2 ? 'warn' : 'muted';
    return `
    <li class="row" data-id="${r.id}">
      <div class="row__thumb">🍳</div>
      <div class="row__main">
        <div class="row__title"><span class="trunc">${esc(r.name)}</span></div>
        <div class="row__sub">${m.have}/${m.total} ingredients · ${r.time} min</div>
        <div class="progress"><i style="width:${Math.round(pct * 100)}%"></i></div>
      </div>
      <div class="row__side"><span class="badge ${cls}">${m.have === m.total ? 'ready' : 'need ' + m.missing.length}</span></div>
    </li>`;
  }).join('') || '<p class="empty">Add some groceries and recipes you can make will show up here.</p>';

  $$('#recipe-list .row').forEach((el) => el.onclick = () => {
    const r = state.recipes.find((x) => x.id === el.dataset.id);
    const m = recipeMatch(r, have);
    openModal(`
      <h2>${esc(r.name)}</h2>
      <p class="hint">${r.time} min · you have ${m.have} of ${m.total} ingredients</p>
      <ul class="list">
        ${r.ingredients.map((ing) => {
          const has = !m.missing.includes(ing);
          return `<li class="row"><div class="row__main"><div class="row__title">${has ? '✅' : '🛒'} ${esc(ing)}</div></div></li>`;
        }).join('')}
      </ul>
    `);
  });

  $$('#recipe-filter .chip').forEach((c) => c.onclick = () => {
    state.recipeFilter = c.dataset.recipeFilter;
    $$('#recipe-filter .chip').forEach((x) => x.classList.toggle('active', x === c));
    renderRecipes();
  });
}

/* ========================= NOTIFICATIONS =========================== */
async function checkExpiries(announce = false) {
  const due = state.groceries.filter((g) => { const d = daysUntil(g.expires); return d != null && d <= state.notifyDays; });
  if (!due.length) return;
  if (announce && Notification.permission === 'granted' && navigator.serviceWorker?.controller) {
    const names = due.slice(0, 4).map((g) => g.name).join(', ');
    navigator.serviceWorker.controller.postMessage({
      type: 'notify',
      title: `${due.length} item(s) expiring soon`,
      body: names + (due.length > 4 ? '…' : ''),
      tag: 'homevault-expiry-' + new Date().toDateString()
    });
  }
}

/* =========================== SETTINGS ============================== */
const CURRENCIES = ['USD', 'EUR', 'GBP', 'PLN', 'CAD', 'AUD', 'JPY', 'CHF', 'SEK', 'INR', 'BRL', 'MXN'];

function settingsModal() {
  const modal = openModal(`
    <h2>Settings</h2>
    <div class="field-2">
      <label class="field">Currency
        <select id="set-currency">${CURRENCIES.map((c) => `<option ${state.currency === c ? 'selected' : ''}>${c}</option>`).join('')}</select>
      </label>
      <label class="field">Alert me (days before)
        <input id="set-days" type="number" min="0" max="60" value="${state.notifyDays}" />
      </label>
    </div>
    <label class="field">Expiry notifications
      <button class="btn ghost" id="set-notify">${Notification.permission === 'granted' ? 'Enabled ✓ — send test' : 'Enable notifications'}</button>
    </label>
    <hr style="border-color:var(--line);margin:14px 0" />
    <div class="btn-row" style="flex-direction:column">
      <button class="btn ghost" id="set-export">⬇️ Export backup (.json)</button>
      <button class="btn ghost" id="set-import">⬆️ Import backup</button>
      <button class="btn ghost" id="set-csv">📊 Export inventory (.csv)</button>
      ${deferredInstall ? '<button class="btn" id="set-install">📲 Install app on phone</button>' : ''}
    </div>
    <p class="hint" style="margin-top:14px">All data is stored only on this device. Export a backup before clearing browser data or switching phones.</p>
  `);

  $('#set-currency', modal).onchange = async (e) => { state.currency = e.target.value; await metaSet('currency', state.currency); renderInventory(); };
  $('#set-days', modal).onchange = async (e) => { state.notifyDays = Math.max(0, +e.target.value || 0); await metaSet('notifyDays', state.notifyDays); renderPantry(); };
  $('#set-notify', modal).onclick = async () => {
    const perm = await Notification.requestPermission();
    if (perm === 'granted') { await checkExpiries(true); toast('Notifications on'); closeModal(); }
    else toast('Permission denied');
  };
  $('#set-export', modal).onclick = exportBackup;
  $('#set-import', modal).onclick = importBackup;
  $('#set-csv', modal).onclick = exportCSV;
  if (deferredInstall) $('#set-install', modal).onclick = async () => { deferredInstall.prompt(); deferredInstall = null; closeModal(); };
}

async function exportBackup() {
  const data = { version: 1, exported: new Date().toISOString(), items: state.items, groceries: state.groceries, mapRooms: map.rooms, currency: state.currency, notifyDays: state.notifyDays };
  download(`homevault-backup-${new Date().toISOString().slice(0, 10)}.json`, JSON.stringify(data, null, 2), 'application/json');
  toast('Backup downloaded');
}

function importBackup() {
  const input = document.createElement('input');
  input.type = 'file'; input.accept = 'application/json';
  input.onchange = async () => {
    const file = input.files[0]; if (!file) return;
    try {
      const data = JSON.parse(await file.text());
      if (!confirm('Import will merge into your current data. Continue?')) return;
      for (const it of data.items || []) await dbPut('items', it);
      for (const g of data.groceries || []) await dbPut('groceries', g);
      if (data.mapRooms) { map.rooms = data.mapRooms; await saveMap(); }
      if (data.currency) { state.currency = data.currency; await metaSet('currency', state.currency); }
      state.items = await dbGetAll('items');
      state.groceries = await dbGetAll('groceries');
      closeModal(); renderAll();
      toast('Backup imported');
    } catch { toast('Could not read that file'); }
  };
  input.click();
}

function exportCSV() {
  const head = ['Name', 'Category', 'Room', 'Price', 'Currency', 'Purchased', 'Model', 'Serial', 'Notes'];
  const rows = state.items.map((i) => [i.name, i.category, i.room, i.price, state.currency, i.purchased, i.model, i.serial, i.notes]
    .map((v) => `"${String(v ?? '').replace(/"/g, '""')}"`).join(','));
  download(`inventory-${new Date().toISOString().slice(0, 10)}.csv`, [head.join(','), ...rows].join('\n'), 'text/csv');
  toast('CSV downloaded');
}

function download(filename, content, type) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = filename; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/* ============================ ROUTING ============================== */
const TITLES = { inventory: 'Inventory', map: 'Apartment map', pantry: 'Pantry', recipes: 'Recipes' };

function switchView(view) {
  state.view = view;
  $('#view-title').textContent = TITLES[view];
  $$('.view').forEach((v) => v.classList.toggle('hidden', v.dataset.view !== view));
  $$('.tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === view));
  $('#btn-add').style.visibility = view === 'recipes' ? 'hidden' : 'visible';
  if (view === 'map') renderMap();
  if (view === 'recipes') renderRecipes();
}

function onAdd() {
  if (state.view === 'inventory') itemFormModal(null);
  else if (state.view === 'pantry') groceryFormModal(null);
  else if (state.view === 'map') { map.mode = 'addRoom'; $('#map-add-room').classList.add('active'); toast('Tap the map to drop a room'); }
}

function renderAll() {
  renderInventory();
  renderPantry();
  renderMap();
  renderRecipes();
}

/* ============================== INIT =============================== */
let deferredInstall = null;
window.addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); deferredInstall = e; });

async function init() {
  state.currency = await metaGet('currency', 'USD');
  state.notifyDays = await metaGet('notifyDays', 3);
  state.items = await dbGetAll('items');
  state.groceries = await dbGetAll('groceries');
  await loadMap();

  try { state.recipes = await (await fetch('./data/recipes.json')).json(); }
  catch { state.recipes = []; }

  // Tabs
  $$('.tab').forEach((t) => t.onclick = () => switchView(t.dataset.tab));
  // App bar
  $('#btn-add').onclick = onAdd;
  $('#btn-settings').onclick = settingsModal;
  $('#btn-search').onclick = () => { $('#search-bar').classList.toggle('hidden'); $('#search-input').focus(); };
  $('#search-close').onclick = () => { $('#search-bar').classList.add('hidden'); $('#search-input').value = ''; state.search = ''; renderAll(); };
  $('#search-input').oninput = (e) => { state.search = e.target.value.trim().toLowerCase(); renderInventory(); renderPantry(); };
  // Pantry actions
  $('#btn-scan').onclick = scanProduct;
  $('#btn-show-expiring').onclick = showExpiring;

  setupMapInteraction();
  renderAll();
  switchView('inventory');

  // Service worker + expiry check
  if ('serviceWorker' in navigator) {
    try { await navigator.serviceWorker.register('./service-worker.js'); } catch { /* ignore */ }
  }
  setTimeout(() => checkExpiries(true), 1500);
}

init();
