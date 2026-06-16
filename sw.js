const CACHE_NAME = 'cyber-tools-v1';
const urlsToCache = [
  '/',
  '/index.html',
  '/image/logo.png',
  'https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css'
];

// ইনস্টল করার সময় ক্যাশে রাখা
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(urlsToCache))
  );
});

// নেটওয়ার্ক রিকোয়েস্ট ইন্টারসেপ্ট করা (অফলাইনে ক্যাশ থেকে দেখানো)
self.addEventListener('fetch', event => {
  event.respondWith(
    caches.match(event.request)
      .then(response => response || fetch(event.request))
  );
});

// পুরনো ক্যাশ ডিলিট করা (আপডেটের সময়)
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(cacheNames => {
      return Promise.all(
        cacheNames.map(cache => {
          if (cache !== CACHE_NAME) {
            return caches.delete(cache);
          }
        })
      );
    })
  );
});