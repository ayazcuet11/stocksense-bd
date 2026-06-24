import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login').then(m => m.LoginComponent)
  },
  {
    path: '',
    loadComponent: () => import('./shared/shell/shell').then(m => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then(m => m.DashboardComponent)
      },
      {
        path: 'inventory',
        loadComponent: () => import('./features/inventory/inventory-list').then(m => m.InventoryListComponent)
      },
      {
        path: 'inventory/:id',
        loadComponent: () => import('./features/inventory/product-detail').then(m => m.ProductDetailComponent)
      },
      {
        path: 'orders',
        loadComponent: () => import('./features/orders/orders').then(m => m.OrdersComponent)
      },
      {
        path: 'forecast',
        loadComponent: () => import('./features/forecast/forecast').then(m => m.ForecastComponent)
      },
      {
        path: 'ask-ai',
        loadComponent: () => import('./features/ask-ai/ask-ai').then(m => m.AskAiComponent)
      }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
