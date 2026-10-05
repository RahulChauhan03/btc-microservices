/** SUBMITTED and the legacy PENDING await review; APPROVED and REJECTED are final. */
export type ClaimStatus = 'PENDING' | 'SUBMITTED' | 'APPROVED' | 'REJECTED';
export type TripStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
/** Must match the backend Role enum (user-service). */
export type UserRole = 'ADMIN' | 'EMPLOYEE';

export interface DashboardSummary {
  activeTrips: number;
  pendingClaims: number;
  monthlyExpenses: number;
  approvalRate: number;
}

export interface User {
  id: number;
  name: string;
  email: string;
  phone: string;
  role: UserRole;
  createdAt: string;
}

export interface UserPayload {
  name: string;
  email: string;
  phone: string;
  /** Applied by the backend only when an administrator edits another user. */
  role: UserRole;
  password?: string;
}

export interface Trip {
  id: number;
  tripCode: string;
  destination: string;
  startDate: string;
  endDate: string;
  budget: number;
  status: TripStatus;
  /** Null for records created before ownership existed (visible to administrators only). */
  ownerId: number | null;
}

export interface TripPayload {
  tripCode: string;
  destination: string;
  startDate: string;
  endDate: string;
  budget: number;
  status: TripStatus;
}

export interface Expense {
  id: number;
  title: string;
  category: 'TRAVEL' | 'HOTEL' | 'MEAL' | 'TRANSPORT' | 'OTHER';
  amount: number;
  expenseDate: string;
  description: string;
  createdAt: string;
  tripId: number | null;
  ownerId: number | null;
  /** Claim currently covering this expense; while set, the backend refuses edits and deletes. */
  claimId: number | null;
}

export interface ExpensePayload {
  title: string;
  category: string;
  amount: number;
  expenseDate: string;
  description: string;
  tripId?: number | null;
}

export interface Claim {
  id: number;
  claimNumber: string;
  title: string;
  description?: string;
  /** Calculated by the backend from the linked expenses. */
  claimAmount: number;
  submittedAt: string;
  status: ClaimStatus;
  ownerId: number | null;
  tripId: number | null;
  expenseIds: number[];
  reviewedBy: number | null;
  reviewedAt: string | null;
}

/** Amount, status, owner and reviewer are decided by the backend and are not sent. */
export interface ClaimPayload {
  claimNumber: string;
  title: string;
  description?: string;
  expenseIds: number[];
}

/** One page of a list endpoint plus the total from the X-Total-Count header. */
export interface PagedResult<T> {
  items: T[];
  total: number;
}

export interface StatusCount {
  status: string;
  count: number;
}

export interface TripSummary {
  total: number;
  upcoming: number;
  byStatus: StatusCount[];
}

export interface CategoryTotal {
  category: string;
  count: number;
  total: number;
}

export interface MonthTotal {
  year: number;
  month: number;
  count: number;
  total: number;
}

/** Computed by expense-service (database aggregation) for the caller's scope. */
export interface ExpenseSummary {
  from: string;
  to: string;
  count: number;
  total: number;
  byCategory: CategoryTotal[];
  byMonth: MonthTotal[];
}

export interface ClaimStatusTotal {
  status: ClaimStatus;
  count: number;
  total: number;
}

export interface ClaimSummary {
  total: number;
  totalAmount: number;
  awaitingReview: number;
  byStatus: ClaimStatusTotal[];
}

export interface UserStats {
  total: number;
  admins: number;
  employees: number;
  joinedLast30Days: number;
}

export interface AppNotification {
  id: number;
  type: string;
  title: string;
  message: string;
  /** In-app route, e.g. /claims. */
  link: string | null;
  createdAt: string;
  read: boolean;
}

export interface TripSpend {
  tripId: number;
  count: number;
  total: number;
}

export interface ClaimStatusReport {
  from: string;
  to: string;
  total: number;
  totalAmount: number;
  byStatus: ClaimStatusTotal[];
}

export type ExpenseCategory = 'TRAVEL' | 'HOTEL' | 'MEAL' | 'TRANSPORT' | 'OTHER';

export interface TravelPolicy {
  id: number;
  name: string;
  currency: string;
  tripLimit: number | null;
  effectiveFrom: string;
  effectiveTo: string | null;
  categoryLimits: Partial<Record<ExpenseCategory, number>>;
  updatedAt: string;
  active: boolean;
}

export interface TravelPolicyPayload {
  name: string;
  currency: string;
  tripLimit: number | null;
  effectiveFrom: string;
  effectiveTo: string | null;
  categoryLimits: Partial<Record<ExpenseCategory, number>>;
}

export type ReimbursementStatus = 'PENDING' | 'PROCESSING' | 'FAILED' | 'PAID';

export interface Reimbursement {
  id: number;
  claimId: number;
  claimNumber: string | null;
  claimTitle: string | null;
  ownerId: number | null;
  status: ReimbursementStatus;
  amount: number;
  paymentDate: string | null;
  paymentReference: string | null;
  updatedAt: string;
  /** Statuses the backend allows next (empty when PAID). */
  allowedNext: ReimbursementStatus[];
}

export interface ReimbursementUpdate {
  status: ReimbursementStatus;
  paymentDate: string | null;
  paymentReference: string | null;
}

export type AuditSource = 'users' | 'claims' | 'expenses';

export interface AuditEntry {
  id: number;
  source: AuditSource;
  actorId: number;
  action: string;
  targetType: string;
  targetId: number;
  summary: string;
  createdAt: string;
}
