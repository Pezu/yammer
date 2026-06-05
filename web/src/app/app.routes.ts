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
        path: 'order-points',
        loadComponent: () =>
          import('./features/backoffice/pages/order-points/order-points-page').then(
            (m) => m.OrderPointsPage,
          ),
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
        path: 'reports',
        children: [
          {
            path: 'orders',
            loadComponent: () =>
              import('./features/backoffice/pages/reports/orders-report-page').then(
                (m) => m.OrdersReportPage,
              ),
          },
          {
            path: 'products',
            loadComponent: () =>
              import('./features/backoffice/pages/reports/products-report-page').then(
                (m) => m.ProductsReportPage,
              ),
          },
          {
            path: 'sales',
            loadComponent: () =>
              import('./features/backoffice/pages/reports/sales-report-page').then(
                (m) => m.SalesReportPage,
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
        loadComponent: () =>
          import('./features/waiter/orders/waiter-orders-page').then((m) => m.WaiterOrdersPage),
      },
      {
        path: 'statistics',
        loadComponent: () =>
          import('./features/waiter/statistics/waiter-statistics-page').then(
            (m) => m.WaiterStatisticsPage,
          ),
      },
      {
        path: 'payments',
        loadComponent: () =>
          import('./features/waiter/payments/waiter-payments-page').then(
            (m) => m.WaiterPaymentsPage,
          ),
      },
      { path: '', redirectTo: 'tables', pathMatch: 'full' },
    ],
  },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' },
];
