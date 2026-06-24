// Minimal service worker for the waiter PWA: Web Push only (no fetch/caching, so it never
// interferes with the other apps). Shows an OS notification even when the app is closed.

// How many recent lines to keep in a stacked notification before older ones drop off.
const MAX_LINES = 6;

self.addEventListener('push', (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch (e) {
    data = { body: event.data ? event.data.text() : '' };
  }

  // WhatsApp-style stacking: pushes sharing a `tag` collapse into one notification. We read the
  // lines already shown for that tag, append this one, and re-show a single combined notification.
  const tag = data.tag || 'waiter-orders';
  const url = data.url || '/waiter';
  const line = data.body || '';

  event.waitUntil(
    self.registration.getNotifications({ tag }).then((existing) => {
      let lines = [];
      for (const n of existing) {
        if (n.data && Array.isArray(n.data.lines)) {
          lines = lines.concat(n.data.lines);
        }
      }
      if (line) lines.push(line);
      lines = lines.slice(-MAX_LINES);

      const title = lines.length > 1 ? `${lines.length} orders ready` : data.title || 'Yammer';
      return self.registration.showNotification(title, {
        body: lines.join('\n'),
        icon: '/assets/images/logo-abbr.png',
        badge: '/assets/images/logo-abbr.png',
        tag, // same tag → OS replaces the previous notification instead of adding a new one
        renotify: true, // still buzz/alert on each new message
        data: { url, lines },
      });
    }),
  );
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
