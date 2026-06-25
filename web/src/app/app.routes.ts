import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';
import { superGuard } from './core/super.guard';

export const routes: Routes = [
  { path: 'login', component: Login },
  {
    path: 'service',
    loadComponent: () => import('./features/service/service-page').then((m) => m.ServicePage),
  },
  {
    path: 'watcher',
    loadComponent: () => import('./features/watcher/watcher-page').then((m) => m.WatcherPage),
  },
  {
    path: 'client',
    loadComponent: () => import('./features/client/client-page').then((m) => m.ClientPage),
  },
  {
    path: 'backoffice',
    loadComponent: () =>
      import('./features/backoffice/backoffice-layout').then((m) => m.BackofficeLayout),
    children: [
      {
        path: 'clients',
        canActivate: [superGuard],
        loadComponent: () =>
          import('./features/backoffice/pages/clients/clients-page').then((m) => m.ClientsPage),
      },
      {
        path: 'users',
        loadComponent: () =>
          import('./features/backoffice/pages/users/users-page').then((m) => m.UsersPage),
      },
      {
        path: 'locations',
        loadComponent: () =>
          import('./features/backoffice/pages/locations/locations-page').then((m) => m.LocationsPage),
      },
      {
        path: 'menu',
        loadComponent: () =>
          import('./features/backoffice/pages/menu/menu-page').then((m) => m.MenuPage),
      },
      {
        path: 'recipes',
        loadComponent: () =>
          import('./features/backoffice/pages/recipes/recipes-page').then((m) => m.RecipesPage),
      },
      {
        path: 'order-points',
        loadComponent: () =>
          import('./features/backoffice/pages/order-points/order-points-page').then(
            (m) => m.OrderPointsPage,
          ),
      },
      {
        path: 'events',
        loadComponent: () =>
          import('./features/backoffice/pages/events/events-page').then((m) => m.EventsPage),
      },
      {
        path: 'assign',
        loadComponent: () =>
          import('./features/backoffice/pages/assign/assign-page').then((m) => m.AssignPage),
      },
      {
        path: 'integrations',
        loadComponent: () =>
          import('./features/backoffice/pages/integrations/integrations-page').then(
            (m) => m.IntegrationsPage,
          ),
      },
      {
        path: 'customers',
        loadComponent: () =>
          import('./features/backoffice/pages/customers/customers-page').then(
            (m) => m.CustomersPage,
          ),
      },
      {
        path: 'reports',
        children: [
          {
            path: 'dashboard',
            loadComponent: () =>
              import('./features/backoffice/pages/reports/dashboard-page').then(
                (m) => m.DashboardPage,
              ),
          },
          {
            path: 'orders',
            loadComponent: () =>
              import('./features/backoffice/pages/reports/orders-report-page').then(
                (m) => m.OrdersReportPage,
              ),
          },
          {
            path: 'payments',
            loadComponent: () =>
              import('./features/backoffice/pages/reports/payments-report-page').then(
                (m) => m.PaymentsReportPage,
              ),
          },
          { path: '', redirectTo: 'orders', pathMatch: 'full' },
        ],
      },
      {
        path: 'roles',
        canActivate: [superGuard],
        loadComponent: () =>
          import('./features/backoffice/pages/roles/roles-page').then((m) => m.RolesPage),
      },
      {
        path: 'vat',
        canActivate: [superGuard],
        loadComponent: () =>
          import('./features/backoffice/pages/vat/vat-page').then((m) => m.VatPage),
      },
      { path: '', redirectTo: 'users', pathMatch: 'full' },
    ],
  },
  {
    path: 'waiter',
    loadComponent: () => import('./features/waiter/waiter-layout').then((m) => m.WaiterLayout),
    children: [
      {
        path: 'tables',
        title: 'Tables',
        loadComponent: () =>
          import('./features/waiter/tables/waiter-tables-page').then((m) => m.WaiterTablesPage),
      },
      {
        path: 'tables/:id',
        loadComponent: () =>
          import('./features/waiter/tables/waiter-table-detail-page').then(
            (m) => m.WaiterTableDetailPage,
          ),
      },
      {
        path: 'tables/:id/order',
        loadComponent: () =>
          import('./features/waiter/tables/waiter-order-page').then((m) => m.WaiterOrderPage),
      },
      {
        path: 'orders',
        title: 'Orders',
        loadComponent: () =>
          import('./features/waiter/orders/waiter-orders-page').then((m) => m.WaiterOrdersPage),
      },
      {
        path: 'statistics',
        title: 'Statistics',
        loadComponent: () =>
          import('./features/waiter/statistics/waiter-statistics-page').then(
            (m) => m.WaiterStatisticsPage,
          ),
      },
      {
        path: 'payments',
        title: 'Payments',
        loadComponent: () =>
          import('./features/waiter/payments/waiter-payments-page').then(
            (m) => m.WaiterPaymentsPage,
          ),
      },
      { path: '', redirectTo: 'tables', pathMatch: 'full' },
    ],
  },
  {
    path: 'customer/order-point/:opId',
    data: { view: 'menu' },
    loadComponent: () =>
      import('./features/customer/customer-order-point-page').then(
        (m) => m.CustomerOrderPointPage,
      ),
  },
  {
    path: 'customer/order-point/:opId/orders',
    data: { view: 'orders' },
    loadComponent: () =>
      import('./features/customer/customer-order-point-page').then(
        (m) => m.CustomerOrderPointPage,
      ),
  },
  {
    path: 'customer/order-point/:opId/payment-return',
    loadComponent: () =>
      import('./features/customer/payment-return-page').then(
        (m) => m.PaymentReturnPage,
      ),
  },
  {
    path: 'legal/:doc',
    loadComponent: () => import('./features/customer/legal-page').then((m) => m.LegalPage),
  },
  {
    path: '',
    loadComponent: () => import('./features/customer/landing-page').then((m) => m.LandingPage),
  },
  { path: '**', redirectTo: '' },
];
