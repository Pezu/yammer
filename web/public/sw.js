// Minimal service worker for the waiter PWA: Web Push only (no fetch/caching, so it never
// interferes with the other apps). Shows an OS notification even when the app is closed.

self.addEventListener('push', (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch (e) {
    data = { body: event.data ? event.data.text() : '' };
  }
  const title = data.title || 'Yammer';
  const options = {
    body: data.body || '',
    icon: '/assets/images/logo-abbr.png',
    badge: '/assets/images/logo-abbr.png',
    data: { url: data.url || '/waiter' },
  };
  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || '/waiter';
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((wins) => {
      for (const w of wins) {
        if ('focus' in w) {
          w.navigate(url);
          return w.focus();
        }
      }
      return self.clients.openWindow(url);
    }),
  );
});
