import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AgentStatus, Branch, BranchStock, Category, FestivalEvent, FestivalForecastResponse, InsightResponse, Product, PurchaseOrder, PurchaseOrderView, ReorderScanResponse, Supplier } from '../models/models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  readonly activeBranchId = signal<number | null>(null);

  constructor(private http: HttpClient) {}

  // Branches
  getBranches() { return this.http.get<Branch[]>('/api/branches'); }

  // Products
  getProducts(categoryId?: number) {
    if (categoryId != null) {
      return this.http.get<Product[]>('/api/products', { params: { categoryId: String(categoryId) } });
    }
    return this.http.get<Product[]>('/api/products');
  }
  getProduct(id: number) { return this.http.get<Product>(`/api/products/${id}`); }

  // Categories
  getCategories() { return this.http.get<Category[]>('/api/categories'); }

  // Stock
  getStock(branchId: number) { return this.http.get<BranchStock[]>(`/api/branches/${branchId}/stock`); }
  getLowStock(branchId: number) { return this.http.get<BranchStock[]>(`/api/branches/${branchId}/stock/low`); }

  // Suppliers
  getSuppliers() { return this.http.get<Supplier[]>('/api/suppliers'); }

  // Purchase orders
  getPurchaseOrders() { return this.http.get<PurchaseOrder[]>('/api/purchase-orders'); }
  getPurchaseOrdersDetailed() { return this.http.get<PurchaseOrderView[]>('/api/purchase-orders/detailed'); }
  getPendingOrders() { return this.http.get<PurchaseOrder[]>('/api/purchase-orders/pending'); }
  approvePurchaseOrder(id: number) { return this.http.post<PurchaseOrder>(`/api/purchase-orders/${id}/approve`, {}); }
  sendPurchaseOrder(id: number) { return this.http.post<PurchaseOrder>(`/api/purchase-orders/${id}/send`, {}); }

  // Festivals
  getUpcomingFestivals(withinDays = 90) {
    return this.http.get<FestivalEvent[]>('/api/festivals/upcoming', { params: { withinDays: String(withinDays) } });
  }

  // Festival Demand Agent (Phase 3)
  getAgentStatus() { return this.http.get<AgentStatus>('/api/agents/status'); }
  getFestivalForecast(branchId: number, horizonDays = 120) {
    return this.http.get<FestivalForecastResponse>('/api/agents/festival-forecast', {
      params: { branchId: String(branchId), horizonDays: String(horizonDays) },
    });
  }
  runReorderScan(branchId: number, horizonDays = 120) {
    return this.http.post<ReorderScanResponse>('/api/agents/reorder-scan', null, {
      params: { branchId: String(branchId), horizonDays: String(horizonDays) },
    });
  }

  // Insight Agent (Phase 5 — Text-to-SQL)
  askInsight(question: string) {
    return this.http.post<InsightResponse>('/api/agents/insight', { question });
  }
}
