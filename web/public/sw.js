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

  // WhatsApp-style stacking: pushes sharing a `tag` collapse into one notification, keyed by
  // orderNo. A 'ready' push appends/updates that order's line; a 'remove' push (sent when the
  // order is delivered) drops its line — and closes the notification once nothing's left.
  const tag = data.tag || 'waiter-orders';
  const url = data.url || '/waiter';
  const orderNo = data.orderNo;
  const remove = data.type === 'remove';

  event.waitUntil(
    self.registration.getNotifications({ tag }).then((existing) => {
      let items = [];
      for (const n of existing) {
        if (n.data && Array.isArray(n.data.items)) {
          items = items.concat(n.data.items);
        }
      }
      // Drop any existing entry for this order (dedupe on re-ready, or remove on delivered).
      if (orderNo != null) {
        items = items.filter((it) => it.orderNo !== orderNo);
      }
      if (!remove) {
        items.push({ orderNo, line: data.body || '' });
      }
      items = items.slice(-MAX_LINES);

      if (items.length === 0) {
        for (const n of existing) n.close(); // delivered the last one — clear the stack
        return;
      }
      const title = items.length > 1 ? `${items.length} orders ready` : data.title || 'Order ready';
      return self.registration.showNotification(title, {
        body: items.map((it) => it.line).join('\n'),
        icon: '/assets/images/logo-abbr.png',
        badge: '/assets/images/logo-abbr.png',
        tag, // same tag → OS replaces the previous notification instead of adding a new one
        renotify: !remove, // buzz on a new order, stay quiet when one is just cleared
        data: { url, items },
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
