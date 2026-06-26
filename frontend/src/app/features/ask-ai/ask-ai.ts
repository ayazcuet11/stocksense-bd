import { Component, ChangeDetectionStrategy, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ApiService } from '../../core/services/api.service';
import { InsightResponse } from '../../core/models/models';

interface Turn {
  question: string;
  loading: boolean;
  response?: InsightResponse;
  error?: string;
  showSql?: boolean;
}

@Component({
  selector: 'app-ask-ai',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, DatePipe, MatCardModule, MatIconModule, MatButtonModule,
            MatProgressSpinnerModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 class="page-title">Ask AI</h2>

    @if (!agentEnabled()) {
      <mat-card class="banner warn">
        <mat-icon>key_off</mat-icon>
        <span>The AI agent is not configured. Set the <code>ANTHROPIC_API_KEY</code> environment
          variable on the backend and restart to ask questions about your data.</span>
      </mat-card>
    }

    @if (turns().length === 0) {
      <mat-card class="intro-card">
        <div class="intro-body">
          <mat-icon class="big-icon">smart_toy</mat-icon>
          <p>Ask about your inventory and sales in plain English or Bangla. The Insight Agent writes a
            safe, <strong>read-only</strong> SQL query, runs it, and shows the result.</p>
          <div class="examples">
            @for (ex of examples; track ex) {
              <button mat-stroked-button class="example-chip" (click)="ask(ex)" [disabled]="!agentEnabled()">
                {{ ex }}
              </button>
            }
          </div>
        </div>
      </mat-card>
    }

    <div class="conversation">
      @for (turn of turns(); track $index) {
        <div class="turn">
          <div class="bubble user">
            <mat-icon>person</mat-icon>
            <span>{{ turn.question }}</span>
          </div>

          @if (turn.loading) {
            <div class="bubble agent loading">
              <mat-spinner diameter="22"></mat-spinner>
              <span>Writing and running a read-only query…</span>
            </div>
          } @else if (turn.error) {
            <div class="bubble agent error">
              <mat-icon>error_outline</mat-icon>
              <span>{{ turn.error }}</span>
            </div>
          } @else if (turn.response; as r) {
            <div class="bubble agent">
              <mat-icon class="bot">smart_toy</mat-icon>
              <div class="agent-content">
                <p class="answer">{{ r.answer }}</p>

                @if (r.result.columns.length > 0) {
                  <div class="table-wrap">
                    <table>
                      <thead>
                        <tr>@for (c of r.result.columns; track c) { <th>{{ c }}</th> }</tr>
                      </thead>
                      <tbody>
                        @for (row of r.result.rows; track $index) {
                          <tr>@for (cell of row; track $index) { <td>{{ format(cell) }}</td> }</tr>
                        }
                      </tbody>
                    </table>
                  </div>
                  <div class="meta">
                    {{ r.result.rowCount }} row{{ r.result.rowCount === 1 ? '' : 's' }}
                    @if (r.result.truncated) { <span class="trunc">· truncated</span> }
                    · decision #{{ r.decisionId }} · {{ r.generatedAt | date:'shortTime' }}
                  </div>
                }

                @if (r.sql) {
                  <button mat-button class="sql-toggle" (click)="turn.showSql = !turn.showSql">
                    <mat-icon>code</mat-icon> {{ turn.showSql ? 'Hide' : 'Show' }} SQL
                  </button>
                  @if (turn.showSql) { <pre class="sql">{{ r.sql }}</pre> }
                }
              </div>
            </div>
          }
        </div>
      }
    </div>

    <form class="ask-bar" (ngSubmit)="ask(draft)">
      <mat-form-field appearance="outline" class="ask-field">
        <mat-label>Ask a question about your inventory or sales…</mat-label>
        <input matInput [(ngModel)]="draft" name="question" [disabled]="busy() || !agentEnabled()"
               autocomplete="off">
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit"
              [disabled]="busy() || !agentEnabled() || !draft.trim()">
        <mat-icon>send</mat-icon>
        Ask
      </button>
    </form>
  `,
  styles: [`
    .page-title { margin:0 0 16px; font-size:1.5rem; font-weight:500; }
    .banner { display:flex; align-items:center; gap:12px; padding:14px 18px!important; margin-bottom:16px; border-radius:10px!important; }
    .banner.warn { background:#fff7e6; color:#8a6100; }
    .banner code { background:rgba(0,0,0,0.06); padding:1px 6px; border-radius:4px; }
    .intro-card { border-radius:12px!important; margin-bottom:16px; }
    .intro-body { display:flex; flex-direction:column; align-items:center; padding:36px 24px; text-align:center; }
    .big-icon { font-size:64px; width:64px; height:64px; color:#7b1fa2; opacity:0.35; margin-bottom:12px; }
    .intro-body p { max-width:560px; color:#667085; line-height:1.6; }
    .examples { display:flex; flex-wrap:wrap; gap:10px; justify-content:center; margin-top:18px; }
    .example-chip { border-radius:20px!important; text-transform:none; font-weight:400; color:#6a1b9a; border-color:#e1bee7!important; }
    .conversation { display:flex; flex-direction:column; gap:20px; margin-bottom:96px; }
    .turn { display:flex; flex-direction:column; gap:10px; }
    .bubble { display:flex; gap:10px; padding:12px 16px; border-radius:12px; line-height:1.55; }
    .bubble mat-icon { flex-shrink:0; }
    .bubble.user { background:#ede7f6; color:#4527a0; align-self:flex-end; max-width:80%; }
    .bubble.agent { background:#fff; border:1px solid #eceef2; align-self:flex-start; max-width:95%; box-shadow:0 1px 2px rgba(16,24,40,0.04); }
    .bubble.agent .bot { color:#7b1fa2; }
    .bubble.loading { align-items:center; color:#667085; }
    .bubble.error { background:#fdecea; border-color:#f6c9c4; color:#b3261e; }
    .agent-content { display:flex; flex-direction:column; gap:10px; width:100%; }
    .answer { margin:0; color:#1d2939; white-space:pre-wrap; }
    .table-wrap { overflow-x:auto; border:1px solid #eceef2; border-radius:8px; }
    table { border-collapse:collapse; width:100%; font-size:0.85rem; }
    th, td { text-align:left; padding:8px 12px; border-bottom:1px solid #f0f1f4; white-space:nowrap; }
    th { background:#faf7fd; color:#5b3a8c; font-weight:600; position:sticky; top:0; }
    tbody tr:last-child td { border-bottom:none; }
    .meta { font-size:0.75rem; color:#98a2b3; }
    .meta .trunc { color:#b54708; }
    .sql-toggle { align-self:flex-start; text-transform:none; color:#7b1fa2; min-width:0; padding:0 8px!important; }
    .sql { margin:0; background:#1d2939; color:#e6e1f0; padding:12px 14px; border-radius:8px; overflow-x:auto; font-size:0.8rem; line-height:1.5; }
    .ask-bar { position:fixed; bottom:0; left:0; right:0; display:flex; gap:12px; align-items:center;
      padding:14px 24px; background:rgba(255,255,255,0.96); border-top:1px solid #eceef2; backdrop-filter:blur(6px); }
    .ask-field { flex:1; max-width:900px; margin:0 auto; }
    .ask-field ::ng-deep .mat-mdc-form-field-subscript-wrapper { display:none; }
    .ask-bar button { height:52px; }
  `]
})
export class AskAiComponent {
  private api = inject(ApiService);

  draft = '';
  agentEnabled = signal(true);
  busy = signal(false);
  turns = signal<Turn[]>([]);

  examples = [
    'Which products sold the most units last year?',
    'Total sales revenue by branch',
    'How many products are below their reorder threshold?',
    'মিরপুর শাখায় সবচেয়ে বেশি বিক্রি হওয়া ৫টি পণ্য',
  ];

  constructor() {
    this.api.getAgentStatus().subscribe({
      next: s => this.agentEnabled.set(s.enabled),
      error: () => this.agentEnabled.set(false),
    });
  }

  ask(question: string) {
    const q = question.trim();
    if (!q || this.busy() || !this.agentEnabled()) return;

    const turn: Turn = { question: q, loading: true };
    this.turns.update(t => [...t, turn]);
    this.draft = '';
    this.busy.set(true);

    this.api.askInsight(q).subscribe({
      next: r => { turn.response = r; turn.loading = false; this.busy.set(false); this.refresh(); },
      error: err => {
        turn.error = err?.error?.detail || 'The query failed. Try rephrasing your question.';
        turn.loading = false;
        this.busy.set(false);
        this.refresh();
      },
    });
  }

  format(cell: string | number | boolean | null): string {
    return cell === null || cell === undefined ? '—' : String(cell);
  }

  // Mutating a Turn object in place — nudge the signal so the view re-renders.
  private refresh() { this.turns.update(t => [...t]); }
}
