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
