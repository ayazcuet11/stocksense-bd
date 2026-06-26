import { Component, ChangeDetectionStrategy, inject, linkedSignal, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ApiService } from '../../core/services/api.service';
import { FestivalForecastResponse } from '../../core/models/models';

@Component({
  selector: 'app-forecast',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, DatePipe, DecimalPipe, MatCardModule, MatIconModule, MatButtonModule,
            MatProgressSpinnerModule, MatFormFieldModule, MatInputModule],
  template: `
    <div class="header-row">
      <h2 class="page-title">Festival Demand Forecast</h2>
      <div class="controls">
        <mat-form-field appearance="outline" class="horizon-field">
          <mat-label>Horizon (days)</mat-label>
          <input matInput type="number" [(ngModel)]="horizonDays" min="7" max="365">
        </mat-form-field>
        <button mat-flat-button color="primary" (click)="run()"
                [disabled]="loading() || branchId() == null">
          <mat-icon>auto_graph</mat-icon>
          {{ loading() ? 'Analyzing…' : 'Run agent' }}
        </button>
      </div>
    </div>

    @if (!agentEnabled()) {
      <mat-card class="banner warn">
        <mat-icon>key_off</mat-icon>
        <span>The AI agent is not configured. Set the <code>ANTHROPIC_API_KEY</code> environment
          variable on the backend and restart to enable live forecasts.</span>
      </mat-card>
    }

    @if (error()) {
      <mat-card class="banner error">
        <mat-icon>error_outline</mat-icon>
        <span>{{ error() }}</span>
      </mat-card>
    }

    @if (loading()) {
      <mat-card class="loading-card">
        <mat-spinner diameter="40"></mat-spinner>
        <div>
          <strong>The Festival Demand Agent is working…</strong>
          <p>Reading upcoming festivals, last year's sales spikes and current stock. This can take
            20–40 seconds while the agent calls its tools.</p>
        </div>
      </mat-card>
    }

    @if (result(); as r) {
      <mat-card class="summary-card">
        <div class="summary-head">
          <mat-icon>insights</mat-icon>
          <div>
            <h3>{{ r.branchName }} · next {{ r.horizonDays }} days</h3>
            <span class="muted">Decision #{{ r.decisionId }} · {{ r.generatedAt | date:'medium' }}</span>
          </div>
        </div>
        <p class="summary-text">{{ r.summary }}</p>
      </mat-card>

      @if (r.recommendations.length === 0) {
        <mat-card class="banner"><mat-icon>info</mat-icon>
          <span>No strong festival demand signal in the selected horizon.</span></mat-card>
      }

      <div class="rec-grid">
        @for (rec of r.recommendations; track rec.productId + rec.festivalName) {
          <mat-card class="rec-card">
            <div class="rec-top">
              <div>
                <h4>{{ rec.productName || ('Product #' + rec.productId) }}</h4>
                <span class="sku">{{ rec.sku }}</span>
              </div>
              @if (rec.upliftPct != null) {
                <span class="uplift">↑ {{ rec.upliftPct | number:'1.0-0' }}%</span>
              }
            </div>

            <div class="festival-chip">
              <mat-icon>celebration</mat-icon>
              {{ rec.festivalName }}@if (rec.festivalDate) { · {{ rec.festivalDate }} }
            </div>

            <div class="metrics">
              <div class="metric">
                <span class="m-label">Stock up</span>
                <span class="m-value strong">+{{ rec.recommendedStockUpUnits ?? '—' }}</span>
              </div>
              <div class="metric">
                <span class="m-label">On hand</span>
                <span class="m-value">{{ rec.currentStock ?? '—' }}</span>
              </div>
              <div class="metric">
                <span class="m-label">Daily</span>
                <span class="m-value">{{ rec.baselineDailyUnits ?? '—' }} → {{ rec.expectedDailyUnits ?? '—' }}</span>
              </div>
            </div>

            <p class="reasoning">{{ rec.reasoning }}</p>
          </mat-card>
        }
      </div>
    } @else if (!loading() && agentEnabled()) {
      <mat-card class="placeholder-card">
        <div class="placeholder-body">
          <mat-icon class="big-icon">auto_graph</mat-icon>
          <p>Pick a horizon and run the agent. It analyzes last year's festival spikes for
            <strong>{{ branchName() || 'this branch' }}</strong> and recommends stock-up quantities
            with reasoning.</p>
        </div>
      </mat-card>
    }
  `,
  styles: [`
    .header-row { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; flex-wrap:wrap; margin-bottom:16px; }
    .page-title { margin:0; font-size:1.5rem; font-weight:500; }
    .controls { display:flex; gap:12px; align-items:center; }
    .horizon-field { width:140px; }
    .horizon-field ::ng-deep .mat-mdc-form-field-subscript-wrapper { display:none; }
    .banner { display:flex; align-items:center; gap:12px; padding:14px 18px!important; margin-bottom:16px; border-radius:10px!important; }
    .banner.warn { background:#fff7e6; color:#8a6100; }
    .banner.error { background:#fdecea; color:#b3261e; }
    .banner code { background:rgba(0,0,0,0.06); padding:1px 6px; border-radius:4px; }
    .loading-card { display:flex; gap:18px; align-items:center; padding:20px 24px!important; border-radius:12px!important; margin-bottom:16px; }
    .loading-card p { margin:4px 0 0; color:#667085; }
    .summary-card { border-radius:12px!important; margin-bottom:20px; padding:20px 24px!important; }
    .summary-head { display:flex; gap:12px; align-items:center; }
    .summary-head mat-icon { color:#465fff; }
    .summary-head h3 { margin:0; font-size:1.15rem; }
    .summary-text { margin:14px 0 0; line-height:1.6; color:#344054; }
    .muted { color:#98a2b3; font-size:0.8rem; }
    .rec-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(320px,1fr)); gap:16px; }
    .rec-card { border-radius:12px!important; padding:18px!important; display:flex; flex-direction:column; gap:12px; }
    .rec-top { display:flex; justify-content:space-between; align-items:flex-start; }
    .rec-top h4 { margin:0; font-size:1.05rem; }
    .sku { font-size:0.75rem; color:#98a2b3; }
    .uplift { background:#ecfdf3; color:#027a48; font-weight:700; padding:4px 10px; border-radius:16px; font-size:0.85rem; white-space:nowrap; }
    .festival-chip { display:inline-flex; align-items:center; gap:6px; background:#ecf0ff; color:#3538cd; padding:4px 10px; border-radius:8px; font-size:0.82rem; width:fit-content; }
    .festival-chip mat-icon { font-size:16px; width:16px; height:16px; }
    .metrics { display:flex; gap:18px; border-top:1px solid #f0f1f4; border-bottom:1px solid #f0f1f4; padding:10px 0; }
    .metric { display:flex; flex-direction:column; }
    .m-label { font-size:0.7rem; color:#98a2b3; text-transform:uppercase; letter-spacing:0.04em; }
    .m-value { font-size:0.95rem; color:#344054; }
    .m-value.strong { font-weight:700; color:#465fff; }
    .reasoning { margin:0; color:#475467; line-height:1.55; font-size:0.9rem; }
    .placeholder-card { border-radius:12px!important; }
    .placeholder-body { display:flex; flex-direction:column; align-items:center; padding:48px 24px; text-align:center; }
    .big-icon { font-size:64px; width:64px; height:64px; color:#465fff; opacity:0.35; margin-bottom:12px; }
    .placeholder-body p { max-width:520px; color:#667085; line-height:1.6; }
  `]
})
export class ForecastComponent {
  private api = inject(ApiService);

  horizonDays = 120;
  branchId = this.api.activeBranchId;
  branchName = signal<string | null>(null);

  agentEnabled = signal(true);
  loading = signal(false);
  // Reset to null whenever the active branch changes; can still be set manually via .set()
  error = linkedSignal({ source: this.branchId, computation: (): string | null => null });
  result = linkedSignal({ source: this.branchId, computation: (): FestivalForecastResponse | null => null });

  constructor() {
    this.api.getAgentStatus().subscribe({
      next: s => this.agentEnabled.set(s.enabled),
      error: () => this.agentEnabled.set(false),
    });
  }

  run() {
    const id = this.branchId();
    if (id == null) return;
    this.loading.set(true);
    this.error.set(null);
    this.result.set(null);
    this.api.getFestivalForecast(id, this.horizonDays).subscribe({
      next: r => { this.result.set(r); this.branchName.set(r.branchName); this.loading.set(false); },
      error: err => {
        this.error.set(err?.error?.detail || 'The agent run failed. Please try again.');
        this.loading.set(false);
      },
    });
  }
}
