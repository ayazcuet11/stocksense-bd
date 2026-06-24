import { Component, OnDestroy, OnInit, effect, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { PurchaseOrderView } from '../../core/models/models';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [DatePipe, DecimalPipe, MatCardModule, MatButtonModule, MatIconModule,
            MatChipsModule, MatProgressSpinnerModule, MatProgressBarModule],
  template: `
    <div class="header-row">
      <h2 class="page-title">Purchase Orders</h2>
      @if (canManage()) {
        <button mat-flat-button color="primary" (click)="runScan()"
                [disabled]="scanning() || branchId() == null || !agentEnabled()">
          <mat-icon>smart_toy</mat-icon>
          {{ scanning() ? 'Scanning…' : 'Run AI reorder scan' }}
        </button>
      }
    </div>

    @if (!agentEnabled()) {
      <mat-card class="banner warn">
        <mat-icon>key_off</mat-icon>
        <span>The AI agent is not configured — set <code>ANTHROPIC_API_KEY</code> to enable reorder scans.</span>
      </mat-card>
    }

    @if (scanning()) {
      <mat-card class="scan-card">
        <mat-progress-bar mode="indeterminate"></mat-progress-bar>
        <div class="scan-body">
          <mat-icon>autorenew</mat-icon>
          <span>The Smart Reorder Agent is checking low stock, festival demand and supplier prices,
            then drafting orders for your approval. New drafts will appear below.</span>
        </div>
      </mat-card>
    }

    @if (message()) {
      <mat-card class="banner info"><mat-icon>info</mat-icon><span>{{ message() }}</span></mat-card>
    }

    @if (loading()) {
      <div class="spinner-wrap"><mat-spinner></mat-spinner></div>
    } @else {
      @if (pendingCount() > 0) {
        <mat-card class="alert-card">
          <mat-icon>pending_actions</mat-icon>
          <span>{{ pendingCount() }} order(s) awaiting your approval</span>
        </mat-card>
      }

      @if (orders().length === 0) {
        <mat-card class="empty-card">
          <mat-icon>inbox</mat-icon>
          <p>No purchase orders yet. Run an AI reorder scan to draft replenishment orders.</p>
        </mat-card>
      }

      <div class="po-list">
        @for (po of orders(); track po.id) {
          <mat-card class="po-card" [class.pending]="po.status === 'PENDING_APPROVAL'">
            <div class="po-head">
              <div class="po-title">
                <span class="po-id">PO #{{ po.id }}</span>
                <mat-chip [class]="'status ' + po.status.toLowerCase()">{{ po.status }}</mat-chip>
                @if (po.createdByAgent) {
                  <span class="ai-badge"><mat-icon>smart_toy</mat-icon> AI drafted</span>
                }
              </div>
              <div class="po-meta">
                <span>{{ po.supplierName || ('Supplier #' + po.supplierId) }}</span>
                <span class="muted">{{ po.createdAt | date:'medium' }}</span>
              </div>
            </div>

            <div class="lines">
              @for (l of po.lines; track l.productId) {
                <div class="line">
                  <span class="l-name">{{ l.productName || ('Product #' + l.productId) }}
                    <span class="l-sku">{{ l.sku }}</span></span>
                  <span class="l-qty">{{ l.quantity }} × ৳{{ l.unitPrice | number:'1.2-2' }}</span>
                  <span class="l-total">৳{{ l.lineTotal | number:'1.2-2' }}</span>
                </div>
              }
            </div>

            <div class="po-foot">
              <span class="total">Total: <strong>৳{{ po.totalAmount | number:'1.2-2' }}</strong></span>
              <div class="actions">
                @if (po.status === 'PENDING_APPROVAL' && canManage()) {
                  <button mat-stroked-button color="warn" (click)="approve(po)">
                    <mat-icon>check</mat-icon> Approve
                  </button>
                }
                @if (po.status === 'APPROVED' && canManage()) {
                  <button mat-stroked-button (click)="send(po)">
                    <mat-icon>send</mat-icon> Mark sent
                  </button>
                }
              </div>
            </div>
          </mat-card>
        }
      </div>
    }
  `,
  styles: [`
    .header-row { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; flex-wrap:wrap; gap:12px; }
    .page-title { margin:0; font-size:1.5rem; font-weight:500; }
    .banner { display:flex; align-items:center; gap:10px; padding:12px 16px!important; margin-bottom:16px; border-radius:10px!important; }
    .banner.warn { background:#fff7e6; color:#8a6100; }
    .banner.info { background:#eef4ff; color:#1849a9; }
    .banner code { background:rgba(0,0,0,0.06); padding:1px 6px; border-radius:4px; }
    .scan-card { margin-bottom:16px; border-radius:12px!important; overflow:hidden; padding:0!important; }
    .scan-body { display:flex; align-items:center; gap:10px; padding:14px 18px; color:#475467; }
    .alert-card { display:flex; align-items:center; gap:8px; padding:14px 16px!important; margin-bottom:16px; background:#fff3e0; color:#e65100; border-radius:12px!important; font-weight:500; }
    .empty-card { display:flex; flex-direction:column; align-items:center; gap:8px; padding:48px!important; color:#98a2b3; text-align:center; border-radius:12px!important; }
    .empty-card mat-icon { font-size:48px; width:48px; height:48px; opacity:0.4; }
    .spinner-wrap { display:flex; justify-content:center; padding:60px; }
    .po-list { display:flex; flex-direction:column; gap:14px; }
    .po-card { border-radius:12px!important; padding:18px!important; }
    .po-card.pending { border-left:4px solid #f79009; }
    .po-head { display:flex; justify-content:space-between; align-items:flex-start; flex-wrap:wrap; gap:8px; }
    .po-title { display:flex; align-items:center; gap:10px; }
    .po-id { font-weight:700; font-size:1.05rem; }
    .po-meta { display:flex; flex-direction:column; align-items:flex-end; font-size:0.85rem; color:#475467; }
    .muted { color:#98a2b3; font-size:0.78rem; }
    mat-chip.status { font-size:0.72rem!important; min-height:24px!important; font-weight:600; }
    mat-chip.status.pending_approval { background:#fef0c7!important; color:#b54708!important; }
    mat-chip.status.approved { background:#d1fadf!important; color:#027a48!important; }
    mat-chip.status.sent, mat-chip.status.received { background:#e0eaff!important; color:#3538cd!important; }
    mat-chip.status.draft { background:#f2f4f7!important; color:#475467!important; }
    .ai-badge { display:inline-flex; align-items:center; gap:4px; background:#ecf0ff; color:#3538cd; padding:2px 8px; border-radius:12px; font-size:0.72rem; font-weight:600; }
    .ai-badge mat-icon { font-size:14px; width:14px; height:14px; }
    .lines { margin:14px 0; border-top:1px solid #f0f1f4; }
    .line { display:grid; grid-template-columns:1fr auto auto; gap:16px; padding:8px 0; border-bottom:1px solid #f5f6f8; align-items:center; }
    .l-name { color:#344054; }
    .l-sku { color:#98a2b3; font-size:0.75rem; margin-left:6px; }
    .l-qty { color:#667085; font-size:0.85rem; }
    .l-total { font-weight:600; min-width:90px; text-align:right; }
    .po-foot { display:flex; justify-content:space-between; align-items:center; }
    .total { font-size:0.95rem; color:#344054; }
    .actions { display:flex; gap:8px; }
  `]
})
export class OrdersComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private auth = inject(AuthService);

  orders = signal<PurchaseOrderView[]>([]);
  loading = signal(true);
  scanning = signal(false);
  agentEnabled = signal(true);
  message = signal<string | null>(null);
  branchId = this.api.activeBranchId;

  private pollHandle: any = null;

  pendingCount = () => this.orders().filter(o => o.status === 'PENDING_APPROVAL').length;
  canManage = () => ['OWNER', 'MANAGER'].includes(this.auth.currentRole() ?? '');

  constructor() {
    this.api.getAgentStatus().subscribe({
      next: s => this.agentEnabled.set(s.enabled),
      error: () => this.agentEnabled.set(false),
    });
  }

  ngOnInit() { this.load(); }
  ngOnDestroy() { this.stopPolling(); }

  load() {
    this.api.getPurchaseOrdersDetailed().subscribe(o => {
      this.orders.set(o);
      this.loading.set(false);
    });
  }

  runScan() {
    const id = this.branchId();
    if (id == null) return;
    this.scanning.set(true);
    this.message.set(null);
    const before = this.orders().length;
    this.api.runReorderScan(id, 120).subscribe({
      next: () => this.pollForDrafts(before),
      error: err => {
        this.scanning.set(false);
        this.message.set(err?.error?.detail || 'Could not start the reorder scan.');
      },
    });
  }

  /** The scan is async — poll the PO list for ~40s until new drafts appear. */
  private pollForDrafts(before: number) {
    let tries = 0;
    this.stopPolling();
    this.pollHandle = setInterval(() => {
      tries++;
      this.api.getPurchaseOrdersDetailed().subscribe(o => {
        this.orders.set(o);
        if (o.length > before) {
          this.scanning.set(false);
          this.message.set(`Agent drafted ${o.length - before} new order(s) for approval.`);
          this.stopPolling();
        } else if (tries >= 20) {
          this.scanning.set(false);
          this.message.set('Scan finished — no new orders were needed (stock is sufficient).');
          this.stopPolling();
        }
      });
    }, 2000);
  }

  private stopPolling() {
    if (this.pollHandle) { clearInterval(this.pollHandle); this.pollHandle = null; }
  }

  approve(po: PurchaseOrderView) {
    this.api.approvePurchaseOrder(po.id).subscribe(() => this.load());
  }
  send(po: PurchaseOrderView) {
    this.api.sendPurchaseOrder(po.id).subscribe(() => this.load());
  }
}
