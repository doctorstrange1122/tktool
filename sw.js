const CACHE_NAME = 'tktool-v2';
const ASSETS = [
  '/tktool/manifest.json',
  '/tktool/icon-192.png',
  '/tktool/icon-512.png'
];

self.addEventListener('install', (e) => {
  // 清除所有旧缓存
  e.waitUntil(
    caches.keys().then(keys => Promise.all(keys.map(k => caches.delete(k))))
  );
  self.skipWaiting();
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then(keys => Promise.all(keys.map(k => caches.delete(k))))
  );
  self.clients.claim();
});

// 不缓存 index.html，始终从网络获取
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (url.pathname.endsWith('.html')) {
    // HTML 文件永远从网络获取，不缓存
    e.respondWith(fetch(e.request));
    return;
  }
  e.respondWith(
    caches.match(e.request).then(r => r || fetch(e.request))
  );
});
