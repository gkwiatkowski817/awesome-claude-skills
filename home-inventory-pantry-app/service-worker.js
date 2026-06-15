// HomeVault service worker — offline-first cache of the app shell.
const CACHE = 'homevault-v1';
const ASSETS = [
  './',
  './index.html',
  './styles.css',
  './app.js',
  './data/recipes.json',
  './manifest.webmanifest',
  './icons/icon.svg',
  './icons/maskable.svg'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  // Never cache cross-origin product/recipe lookups; just pass through.
  if (url.origin !== self.location.origin) return;

  // App shell: cache-first with background refresh.
  event.respondWith(
    caches.match(request).then((cached) => {
      const network = fetch(request)
        .then((resp) => {
          if (resp && resp.status === 200) {
            const copy = resp.clone();
            caches.open(CACHE).then((c) => c.put(request, copy));
          }
          return resp;
        })
        .catch(() => cached);
      return cached || network;
    })
  );
});

// Allow the page to ask the SW to show expiry notifications.
self.addEventListener('message', (event) => {
  const data = event.data || {};
  if (data.type === 'notify' && self.registration.showNotification) {
    self.registration.showNotification(data.title || 'HomeVault', {
      body: data.body || '',
      tag: data.tag || 'homevault-expiry',
      icon: './icons/icon.svg',
      badge: './icons/icon.svg'
    });
  }
});
