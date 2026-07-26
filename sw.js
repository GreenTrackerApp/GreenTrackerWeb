const CACHE_NAME = 'greentracker-v3';
const ASSETS = [
  './',
  './index.html',
  './composeApp.js',
  './composeApp.uninstantiated.wasm',
  './skiko.js',
  './skiko.wasm',
  './manifest.json'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(ASSETS);
    })
  );
});

self.addEventListener('fetch', (event) => {
  event.respondWith(
    caches.match(event.request).then((response) => {
      return response || fetch(event.request);
    })
  );
});
