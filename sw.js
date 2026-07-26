const CACHE_NAME = 'tktool-v1';
const ASSETS = [
  '/tktool/',
  '/tktool/index.html',
  '/tktool/manifest.json'
];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE_NAME).then(c => c.addAll(ASSETS)));
});

self.addEventListener('fetch', (e) => {
  e.respondWith(
    caches.match(e.request).then(r => r || fetch(e.request))
  );
});
