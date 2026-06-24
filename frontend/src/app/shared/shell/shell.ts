import { Component, OnInit, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { ApiService } from '../../core/services/api.service';
import { Branch } from '../../core/models/models';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule,
            MatToolbarModule, MatButtonModule, MatIconModule, MatSelectModule,
            MatSidenavModule, MatListModule, MatMenuModule],
  template: `
    <mat-sidenav-container class="sidenav-container">
      <mat-sidenav #sidenav mode="side" opened class="sidenav">
        <div class="brand">
          <span class="brand-logo"><mat-icon>inventory_2</mat-icon></span>
          <span class="brand-name">StockSense BD</span>
        </div>
        <div class="nav-section-label">MENU</div>
        <mat-nav-list>
          <a mat-list-item routerLink="/dashboard" routerLinkActive="active-link">
            <mat-icon matListItemIcon>dashboard</mat-icon>
            <span matListItemTitle>Dashboard</span>
          </a>
          <a mat-list-item routerLink="/inventory" routerLinkActive="active-link">
            <mat-icon matListItemIcon>inventory</mat-icon>
            <span matListItemTitle>Inventory</span>
          </a>
          <a mat-list-item routerLink="/orders" routerLinkActive="active-link">
            <mat-icon matListItemIcon>shopping_cart</mat-icon>
            <span matListItemTitle>Purchase Orders</span>
          </a>
          <a mat-list-item routerLink="/forecast" routerLinkActive="active-link">
            <mat-icon matListItemIcon>auto_graph</mat-icon>
            <span matListItemTitle>Festival Forecast</span>
          </a>
          <a mat-list-item routerLink="/ask-ai" routerLinkActive="active-link">
            <mat-icon matListItemIcon>smart_toy</mat-icon>
            <span matListItemTitle>Ask AI</span>
          </a>
        </mat-nav-list>
      </mat-sidenav>

      <mat-sidenav-content>
        <mat-toolbar class="toolbar">
          <span class="spacer"></span>

          <!-- Branch switcher -->
          @if (branches().length > 0) {
            <mat-select [(ngModel)]="selectedBranchId" (ngModelChange)="onBranchChange($event)"
                        class="branch-select" panelWidth="auto">
              @for (b of branches(); track b.id) {
                <mat-option [value]="b.id">{{ b.name }}</mat-option>
              }
            </mat-select>
          }

          <button mat-icon-button [matMenuTriggerFor]="userMenu">
            <mat-icon>account_circle</mat-icon>
          </button>
          <mat-menu #userMenu="matMenu">
            <span mat-menu-item disabled class="user-info">{{ auth.currentEmail() }}</span>
            <span mat-menu-item disabled class="user-info">{{ auth.currentRole() }}</span>
            <button mat-menu-item (click)="auth.logout()">
              <mat-icon>logout</mat-icon> Sign out
            </button>
          </mat-menu>
        </mat-toolbar>

        <div class="content">
          <router-outlet></router-outlet>
        </div>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .sidenav-container { height:100vh; background:#f9fafb; }
    .sidenav { width:240px; background:#fff; color:#344054; border-right:1px solid #e4e7ec; }
    .brand { display:flex; align-items:center; gap:10px; padding:20px 20px; font-size:1.15rem; font-weight:700; color:#101828; }
    .brand-logo { display:flex; align-items:center; justify-content:center; width:34px; height:34px; border-radius:8px; background:#465fff; color:#fff; }
    .brand-logo mat-icon { font-size:20px; width:20px; height:20px; }
    .nav-section-label { padding:14px 24px 6px; font-size:0.7rem; font-weight:600; letter-spacing:0.06em; color:#98a2b3; }
    mat-nav-list { padding-top:0; }
    mat-nav-list a { color:#475467; margin:2px 12px; border-radius:8px; font-weight:500; }
    mat-nav-list a mat-icon { color:#667085; }
    mat-nav-list a:hover { background:#f2f4f7; color:#101828; }
    mat-nav-list a:hover mat-icon { color:#101828; }
    mat-nav-list a.active-link { background:#ecf0ff; color:#465fff; }
    mat-nav-list a.active-link mat-icon { color:#465fff; }
    .toolbar { position:sticky; top:0; z-index:100; background:#fff; color:#344054; border-bottom:1px solid #e4e7ec; }
    .spacer { flex:1; }
    .branch-select { color:#344054; margin-right:8px; min-width:160px; }
    .content { padding:24px; }
    .user-info { font-size:0.8rem; opacity:0.7; }
  `]
})
export class ShellComponent implements OnInit {
  branches = signal<Branch[]>([]);
  selectedBranchId: number | null = null;

  constructor(readonly auth: AuthService, private api: ApiService) {}

  ngOnInit() {
    this.api.getBranches().subscribe(bs => {
      this.branches.set(bs);
      if (bs.length > 0) {
        this.selectedBranchId = bs[0].id;
        this.api.activeBranchId.set(bs[0].id);
      }
    });
  }

  onBranchChange(id: number) {
    this.api.activeBranchId.set(id);
  }
}
