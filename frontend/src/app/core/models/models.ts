export interface LoginRequest { email: string; password: string; }
export interface LoginResponse { token: string; email: string; role: string; tenantId: number; }

export interface Branch { id: number; tenantId: number; name: string; address: string; city: string; phone: string; }
export interface Category { id: number; tenantId: number; name: string; }
export interface Product { id: number; tenantId: number; sku: string; name: string; categoryId: number; unit: string; vatRate: number; }
export interface BranchStock { id: number; branchId: number; productId: number; quantity: number; reorderThreshold: number; updatedAt: string; }
export interface Supplier { id: number; tenantId: number; name: string; phone: string; leadTimeDays: number; }

export interface Sale { id: number; branchId: number; soldAt: string; totalAmount: number; vatAmount: number; }
export interface PurchaseOrder {
  id: number; tenantId: number; branchId: number; supplierId: number;
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'SENT' | 'RECEIVED';
  createdByAgent: boolean; approvedBy: number | null; createdAt: string;
}
export interface FestivalEvent { id: number; name: string; type: string; gregorianDate: string; hijriDate: string | null; }

// --- Phase 3: Festival Demand Agent ---
export interface ForecastRecommendation {
  productId: number;
  sku: string | null;
  productName: string | null;
  festivalName: string;
  festivalDate: string | null;
  currentStock: number | null;
  baselineDailyUnits: number | null;
  expectedDailyUnits: number | null;
  recommendedStockUpUnits: number | null;
  upliftPct: number | null;
  reasoning: string;
}
export interface FestivalForecastResponse {
  branchId: number;
  branchName: string;
  horizonDays: number;
  generatedAt: string;
  decisionId: number;
  summary: string;
  recommendations: ForecastRecommendation[];
}
export interface AgentStatus { enabled: boolean; }

// --- Phase 4: Smart Reorder Agent ---
export interface PoLineView {
  productId: number;
  sku: string | null;
  productName: string | null;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}
export interface PurchaseOrderView {
  id: number;
  branchId: number;
  supplierId: number;
  supplierName: string | null;
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'SENT' | 'RECEIVED';
  createdByAgent: boolean;
  approvedBy: number | null;
  createdAt: string;
  totalAmount: number;
  lines: PoLineView[];
}
export interface ReorderScanResponse { jobId: string; status: string; branchId: number; }

// --- Phase 5: Insight Agent (Text-to-SQL) ---
export interface QueryResultView {
  columns: string[];
  rows: (string | number | boolean | null)[][];
  rowCount: number;
  truncated: boolean;
}
export interface InsightResponse {
  question: string;
  answer: string;
  sql: string | null;
  result: QueryResultView;
  decisionId: number;
  generatedAt: string;
}
