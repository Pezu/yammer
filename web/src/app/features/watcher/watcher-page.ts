import { Component } from '@angular/core';
import { DashboardPage } from '../backoffice/pages/reports/dashboard-page';

/** Watcher landing: the same widgets as the backoffice dashboard. */
@Component({
  selector: 'app-watcher-page',
  imports: [DashboardPage],
  template: `<app-dashboard-page class="watcher" />`,
})
export class WatcherPage {}
