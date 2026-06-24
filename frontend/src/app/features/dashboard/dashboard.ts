import { Component, OnInit, signal, computed, effect } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatTableModule } from '@angular/material/table';
import { MatBadgeModule } from '@angular/material/badge';
import { RouterLink } from '@angular/router';
import { DecimalPipe, DatePipe } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { BranchStock, FestivalEvent, Product, PurchaseOrder } from '../../core/models/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [MatCardModule, MatIconModule, MatProgressBarModule, MatButtonModule,
            MatChipsModule, MatTableModule, MatBadgeModule, RouterLink, DatePipe],
  template: `
    <h2 class="page-title">Dashboard</h2>

    <!-- Metric cards -->
    <div class="metric-grid">
      <mat-card class="metric-card">
        <mat-icon class="metric-icon blue">inventory_2</mat-icon>
        <div class="metric-value">{{ products().length }}</div>
        <div class="metric-label">Total Products</div>
      </mat-card>
      <mat-card class="metric-card warn">
        <mat-icon class="metric-icon orange">warning</mat-icon>
        <div class="metric-value">{{ lowStock().length }}</div>
        <div class="metric-label">Low Stock Items</div>
      </mat-card>
      <mat-card class="metric-card">
        <mat-icon class="metric-icon purple">pending_actions</mat-icon>
        <div class="metric-value">{{ pendingOrders().length }}</div>
        <div class="metric-label">Pending Approvals</div>
      </mat-card>
      <mat-card class="metric-card success">
        <mat-icon class="metric-icon green">celebration</mat-icon>
        <div class="metric-value">{{ upcomingFestivals().length }}</div>
        <div class="metric-label">Upcoming Festivals</div>
      </mat-card>
    </div>

    <div class="panel-row">
      <!-- Festival alert panel -->
      <mat-card class="panel festival-panel">
        <mat-card-header>
          <mat-icon mat-card-avatar>celebration</mat-icon>
          <mat-card-title>Upcoming Festivals</mat-card-title>
          <mat-card-subtitle>Next 90 days</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          @if (upcomingFestivals().length === 0) {
            <p class="empty">No festivals in the next 90 days.</p>
          }
          @for (f of upcomingFestivals(); track f.id) {
            <div class="festival-item">
              <mat-icon class="festival-icon">{{ festivalIcon(f.type) }}</mat-icon>
              <div class="festival-info">
                <div class="festival-name">{{ f.name }}</div>
                <div class="festival-date">{{ f.gregorianDate | date:'mediumDate' }}</div>
              </div>
              <mat-chip [highlighted]="true" [color]="festivalColor(f.type)">
                {{ daysUntil(f.gregorianDate) }}d
              </mat-chip>
            </div>
          }
          <!-- AI placeholder -->
          <div class="ai-placeholder">
            <mat-icon>smart_toy</mat-icon>
            <span>Festival Forecast AI — available in Phase 3</span>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Reorder queue -->
      <mat-card class="panel reorder-panel">
        <mat-card-header>
          <mat-icon mat-card-avatar color="warn">shopping_cart</mat-icon>
          <mat-card-title>Reorder Queue</mat-card-title>
          <mat-card-subtitle>Items below reorder threshold</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          @if (!activeBranchId()) {
            <p class="empty">Select a branch to view stock.</p>
          } @else if (lowStock().length === 0) {
            <p class="empty">All items are well-stocked.</p>
          }
          @for (s of lowStock().slice(0, 8); track s.id) {
            <div class="reorder-item">
              <div class="reorder-product">{{ productName(s.productId) }}</div>
              <div class="reorder-qty">
                <span class="qty-current" [class.critical]="s.quantity === 0">{{ s.quantity }}</span>
                <span class="qty-sep">/</span>
                <span class="qty-threshold">{{ s.reorderThreshold }}</span>
              </div>
              <mat-progress-bar mode="determinate"
                [value]="stockPercent(s)"
                [color]="s.quantity === 0 ? 'warn' : 'accent'"
                class="stock-bar">
              </mat-progress-bar>
            </div>
          }
          @if (pendingOrders().length > 0) {
            <div class="ai-placeholder warn-placeholder">
              <mat-icon>pending_actions</mat-icon>
              <span>{{ pendingOrders().length }} order(s) awaiting approval</span>
              <a mat-button color="warn" routerLink="/orders">Review</a>
            </div>
          }
        </mat-card-content>
      </mat-card>
    </div>

    <!-- Stock health bars -->
    <mat-card class="panel stock-health">
      <mat-card-header>
        <mat-icon mat-card-avatar>health_and_safety</mat-icon>
        <mat-card-title>Stock Health</mat-card-title>
        <mat-card-subtitle>Current branch — top 10 products by stock level</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        @if (!activeBranchId()) {
          <p class="empty">Select a branch above.</p>
        }
        @for (s of allStock().slice(0, 10); track s.id) {
          <div class="health-row">
            <span class="health-label">{{ productName(s.productId) }}</span>
            <mat-progress-bar mode="determinate"
              [value]="stockPercent(s)"
              [color]="stockColor(s)"
              class="health-bar">
            </mat-progress-bar>
            <span class="health-qty">{{ s.quantity }}</span>
          </div>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .page-title { margin:0 0 20px; font-size:1.5rem; font-weight:500; }
    .metric-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:16px; margin-bottom:24px; }
    .metric-card { padding:20px; text-align:center; border-radius:12px!important; }
    .metric-icon { font-size:2.5rem; width:2.5rem; height:2.5rem; margin-bottom:8px; }
    .blue { color:#1976d2; } .orange { color:#e65100; } .purple { color:#7b1fa2; } .green { color:#388e3c; }
    .metric-value { font-size:2rem; font-weight:700; }
    .metric-label { font-size:0.85rem; color:#666; margin-top:4px; }
    .panel-row { display:grid; grid-template-columns:1fr 1fr; gap:16px; margin-bottom:24px; }
    @media(max-width:768px) { .panel-row { grid-template-columns:1fr; } }
    .panel { border-radius:12px!important; }
    .festival-item { display:flex; align-items:center; gap:12px; padding:10px 0; border-bottom:1px solid #eee; }
    .festival-icon { color:#f57c00; }
    .festival-info { flex:1; }
    .festival-name { font-weight:500; }
    .festival-date { font-size:0.8rem; color:#666; }
    .reorder-item { padding:8px 0; border-bottom:1px solid #eee; }
    .reorder-product { font-size:0.9rem; font-weight:500; margin-bottom:4px; }
    .reorder-qty { font-size:0.8rem; color:#666; margin-bottom:4px; }
    .qty-current { font-weight:700; }
    .qty-current.critical { color:#f44336; }
    .qty-sep, .qty-threshold { opacity:0.6; }
    .stock-bar { height:6px; border-radius:3px; margin-top:4px; }
    .health-row { display:flex; align-items:center; gap:12px; padding:6px 0; }
    .health-label { width:180px; font-size:0.85rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .health-bar { flex:1; height:8px; border-radius:4px; }
    .health-qty { width:50px; text-align:right; font-size:0.85rem; font-weight:600; }
    .ai-placeholder { display:flex; align-items:center; gap:8px; margin-top:16px; padding:12px; background:#e3f2fd; border-radius:8px; font-size:0.85rem; color:#1565c0; }
    .warn-placeholder { background:#fff3e0; color:#e65100; }
    .empty { color:#999; font-style:italic; padding:16px 0; }
  `]
})
export class DashboardComponent implements OnInit {
  products = signal<Product[]>([]);
  lowStock = signal<BranchStock[]>([]);
  allStock = signal<BranchStock[]>([]);
  upcomingFestivals = signal<FestivalEvent[]>([]);
  pendingOrders = signal<PurchaseOrder[]>([]);
  activeBranchId = computed(() => this.api.activeBranchId());

  private productMap = signal<Map<number, string>>(new Map());

  constructor(private api: ApiService) {
    effect(() => {
      const branchId = this.activeBranchId();
      if (branchId) {
        this.api.getLowStock(branchId).subscribe(s => this.lowStock.set(s));
        this.api.getStock(branchId).subscribe(s => this.allStock.set(s));
      }
    });
  }

  ngOnInit() {
    this.api.getProducts().subscribe(p => {
      this.products.set(p);
      this.productMap.set(new Map(p.map(x => [x.id, x.name])));
    });
    this.api.getUpcomingFestivals().subscribe(f => this.upcomingFestivals.set(f));
    this.api.getPendingOrders().subscribe(o => this.pendingOrders.set(o));
  }

  productName(id: number) { return this.productMap().get(id) ?? `Product ${id}`; }

  stockPercent(s: BranchStock) {
    return Math.min(100, Math.round((s.quantity / (s.reorderThreshold * 3)) * 100));
  }

  stockColor(s: BranchStock): 'primary' | 'accent' | 'warn' {
    const pct = this.stockPercent(s);
    if (pct < 20) return 'warn';
    if (pct < 50) return 'accent';
    return 'primary';
  }

  daysUntil(dateStr: string) {
    const diff = new Date(dateStr).getTime() - Date.now();
    return Math.ceil(diff / 86_400_000);
  }

  festivalIcon(type: string) {
    const icons: Record<string, string> = {
      EID_UL_FITR: 'crescent_moon', EID_UL_ADHA: 'crescent_moon',
      DURGA_PUJA: 'brightness_5', POHELA_BOISHAKH: 'celebration',
      MANGO_SEASON: 'nature', HILSA_SEASON: 'water', MONSOON: 'water_drop'
    };
    return icons[type] ?? 'event';
  }

  festivalColor(type: string): 'primary' | 'accent' | 'warn' {
    if (type.startsWith('EID')) return 'accent';
    if (type === 'DURGA_PUJA') return 'warn';
    return 'primary';
  }
}
