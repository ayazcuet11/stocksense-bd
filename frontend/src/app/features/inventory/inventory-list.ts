import { Component, ChangeDetectionStrategy, OnInit, signal, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { Category, Product } from '../../core/models/models';

@Component({
  selector: 'app-inventory-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatCardModule, MatTableModule, MatInputModule, MatSelectModule, MatButtonModule,
            MatIconModule, MatFormFieldModule, MatChipsModule, MatProgressSpinnerModule,
            FormsModule, DecimalPipe],
  template: `
    <div class="page-header">
      <h2 class="page-title">Inventory</h2>
    </div>

    <!-- Filters -->
    <mat-card class="filter-card">
      <mat-form-field appearance="outline" class="search-field">
        <mat-label>Search products</mat-label>
        <input matInput [ngModel]="searchQuery()" (ngModelChange)="searchQuery.set($event)"
               placeholder="Name or SKU…">
        <mat-icon matSuffix>search</mat-icon>
      </mat-form-field>

      <mat-form-field appearance="outline" class="cat-field">
        <mat-label>Category</mat-label>
        <mat-select [ngModel]="selectedCategoryId()" (ngModelChange)="selectedCategoryId.set($event)">
          <mat-option [value]="null">All categories</mat-option>
          @for (c of categories(); track c.id) {
            <mat-option [value]="c.id">{{ c.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </mat-card>

    @if (loading()) {
      <div class="spinner-wrap"><mat-spinner></mat-spinner></div>
    } @else {
      <mat-card class="table-card">
        <table mat-table [dataSource]="filtered()" class="product-table">

          <ng-container matColumnDef="sku">
            <th mat-header-cell *matHeaderCellDef>SKU</th>
            <td mat-cell *matCellDef="let p">
              <code class="sku">{{ p.sku }}</code>
            </td>
          </ng-container>

          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef>Product</th>
            <td mat-cell *matCellDef="let p">{{ p.name }}</td>
          </ng-container>

          <ng-container matColumnDef="category">
            <th mat-header-cell *matHeaderCellDef>Category</th>
            <td mat-cell *matCellDef="let p">
              <mat-chip>{{ categoryMap().get(p.categoryId) ?? '—' }}</mat-chip>
            </td>
          </ng-container>

          <ng-container matColumnDef="unit">
            <th mat-header-cell *matHeaderCellDef>Unit</th>
            <td mat-cell *matCellDef="let p">{{ p.unit }}</td>
          </ng-container>

          <ng-container matColumnDef="vat">
            <th mat-header-cell *matHeaderCellDef>VAT %</th>
            <td mat-cell *matCellDef="let p">{{ p.vatRate | number:'1.0-2' }}%</td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let p">
              <button mat-icon-button (click)="viewDetail(p.id)" title="View detail">
                <mat-icon>chevron_right</mat-icon>
              </button>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns" class="product-row"
              (click)="viewDetail(row.id)"></tr>
        </table>

        <div class="table-footer">
          Showing {{ filtered().length }} of {{ products().length }} products
        </div>
      </mat-card>
    }
  `,
  styles: [`
    .page-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:20px; }
    .page-title { margin:0; font-size:1.5rem; font-weight:500; }
    .filter-card { padding:16px; margin-bottom:16px; display:flex; gap:16px; flex-wrap:wrap; align-items:center; }
    .search-field { flex:1; min-width:200px; margin:0; }
    .cat-field { min-width:180px; margin:0; }
    .table-card { border-radius:12px!important; overflow:hidden; }
    .product-table { width:100%; }
    .product-row { cursor:pointer; }
    .product-row:hover { background:#f5f5f5; }
    .sku { background:#e8eaf6; color:#3949ab; padding:2px 6px; border-radius:4px; font-size:0.85rem; }
    .table-footer { padding:12px 16px; font-size:0.85rem; color:#666; border-top:1px solid #eee; }
    .spinner-wrap { display:flex; justify-content:center; padding:60px; }
  `]
})
export class InventoryListComponent implements OnInit {
  products = signal<Product[]>([]);
  categories = signal<Category[]>([]);
  loading = signal(true);
  searchQuery = signal('');
  selectedCategoryId = signal<number | null>(null);

  columns = ['sku', 'name', 'category', 'unit', 'vat', 'actions'];

  protected readonly categoryMap = signal<Map<number, string>>(new Map());

  filtered = computed(() => {
    let list = this.products();
    const catId = this.selectedCategoryId();
    if (catId) list = list.filter(p => p.categoryId === catId);
    const q = this.searchQuery().toLowerCase();
    if (q) list = list.filter(p => p.name.toLowerCase().includes(q) || p.sku.toLowerCase().includes(q));
    return list;
  });

  private api = inject(ApiService);
  private router = inject(Router);

  ngOnInit() {
    this.api.getCategories().subscribe(c => {
      this.categories.set(c);
      this.categoryMap.set(new Map(c.map(x => [x.id, x.name])));
    });
    this.api.getProducts().subscribe(p => {
      this.products.set(p);
      this.loading.set(false);
    });
  }

  viewDetail(id: number) { this.router.navigate(['/inventory', id]); }
}
