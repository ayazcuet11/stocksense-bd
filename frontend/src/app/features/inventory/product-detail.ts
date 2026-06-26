import { Component, ChangeDetectionStrategy, OnInit, signal, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { BranchStock, Product } from '../../core/models/models';

@Component({
  selector: 'app-product-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatCardModule, MatButtonModule, MatIconModule, MatDividerModule,
            MatProgressBarModule, MatChipsModule, DecimalPipe],
  template: `
    <div class="back-bar">
      <a mat-button routerLink="/inventory">
        <mat-icon>arrow_back</mat-icon> Back to Inventory
      </a>
    </div>

    @if (product()) {
      <div class="detail-grid">
        <mat-card class="info-card">
          <mat-card-header>
            <mat-card-title>{{ product()!.name }}</mat-card-title>
            <mat-card-subtitle>
              <code class="sku">{{ product()!.sku }}</code>
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <mat-divider></mat-divider>
            <div class="field-row">
              <span class="label">Unit</span>
              <span>{{ product()!.unit }}</span>
            </div>
            <div class="field-row">
              <span class="label">VAT Rate</span>
              <span>{{ product()!.vatRate | number:'1.2-2' }}%</span>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Stock across branches -->
        <mat-card class="stock-card">
          <mat-card-header>
            <mat-icon mat-card-avatar>store</mat-icon>
            <mat-card-title>Stock by Branch</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @if (stockEntries().length === 0) {
              <p class="empty">No stock data found for this product.</p>
            }
            @for (s of stockEntries(); track s.id) {
              <div class="stock-row">
                <div class="stock-info">
                  <span class="branch-name">{{ branchName(s.branchId) }}</span>
                  <span class="stock-detail">
                    {{ s.quantity }} {{ product()!.unit }} — threshold {{ s.reorderThreshold }}
                  </span>
                </div>
                <mat-chip [color]="stockChipColor(s)" [highlighted]="true">
                  {{ s.quantity <= s.reorderThreshold ? 'Low' : 'OK' }}
                </mat-chip>
              </div>
              <mat-progress-bar
                [value]="stockPct(s)"
                [color]="s.quantity <= s.reorderThreshold ? 'warn' : 'primary'"
                class="stock-bar">
              </mat-progress-bar>
            }
          </mat-card-content>
        </mat-card>
      </div>
    }
  `,
  styles: [`
    .back-bar { margin-bottom:16px; }
    .detail-grid { display:grid; grid-template-columns:320px 1fr; gap:20px; }
    @media(max-width:768px) { .detail-grid { grid-template-columns:1fr; } }
    .info-card, .stock-card { border-radius:12px!important; }
    .sku { background:#e8eaf6; color:#3949ab; padding:2px 8px; border-radius:4px; font-size:0.9rem; }
    mat-divider { margin:16px 0; }
    .field-row { display:flex; justify-content:space-between; padding:10px 0; border-bottom:1px solid #eee; }
    .label { color:#666; font-size:0.9rem; }
    .stock-row { display:flex; align-items:center; justify-content:space-between; padding:10px 0; }
    .stock-info { display:flex; flex-direction:column; }
    .branch-name { font-weight:500; }
    .stock-detail { font-size:0.8rem; color:#666; }
    .stock-bar { margin-bottom:12px; }
    .empty { color:#999; font-style:italic; padding:16px 0; }
  `]
})
export class ProductDetailComponent implements OnInit {
  product = signal<Product | null>(null);
  stockEntries = signal<BranchStock[]>([]);
  private branchMap = signal<Map<number, string>>(new Map());

  private route = inject(ActivatedRoute);
  private api = inject(ApiService);

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.getProduct(id).subscribe(p => this.product.set(p));

    this.api.getBranches().subscribe(branches => {
      this.branchMap.set(new Map(branches.map(b => [b.id, b.name])));
      branches.forEach(b => {
        this.api.getStock(b.id).subscribe(stocks => {
          const entry = stocks.find(s => s.productId === id);
          if (entry) this.stockEntries.update(arr => [...arr, entry]);
        });
      });
    });
  }

  branchName(id: number) { return this.branchMap().get(id) ?? `Branch ${id}`; }
  stockPct(s: BranchStock) { return Math.min(100, Math.round((s.quantity / (s.reorderThreshold * 3)) * 100)); }
  stockChipColor(s: BranchStock): 'warn' | 'primary' {
    return s.quantity <= s.reorderThreshold ? 'warn' : 'primary';
  }
}
