import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-ask-ai',
  standalone: true,
  imports: [MatCardModule, MatIconModule],
  template: `
    <h2 class="page-title">Ask AI</h2>
    <mat-card class="placeholder-card">
      <div class="placeholder-body">
        <mat-icon class="big-icon">smart_toy</mat-icon>
        <h3>Coming in Phase 5</h3>
        <p>
          The Insight Agent lets you query your inventory data in plain English or Bangla.
          Type a question, and the agent generates safe, read-only SQL against your database
          and returns a formatted result or chart.
        </p>
        <p class="example">
          <mat-icon>chat</mat-icon>
          <em>"Which products sold the most in Mirpur branch during Eid last year?"</em>
        </p>
      </div>
    </mat-card>
  `,
  styles: [`
    .page-title { margin:0 0 20px; font-size:1.5rem; font-weight:500; }
    .placeholder-card { border-radius:12px!important; }
    .placeholder-body { display:flex; flex-direction:column; align-items:center; padding:48px 24px; text-align:center; }
    .big-icon { font-size:72px; width:72px; height:72px; color:#7b1fa2; opacity:0.4; margin-bottom:16px; }
    h3 { font-size:1.5rem; margin:0 0 16px; }
    p { max-width:540px; color:#555; line-height:1.6; }
    .example { background:#f3e5f5; border-radius:8px; padding:16px; color:#6a1b9a; display:flex; gap:8px; align-items:flex-start; text-align:left; }
  `]
})
export class AskAiComponent {}
