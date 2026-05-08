export type ClaimStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUBMITTED';
export type TripStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

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
  createdAt: string;
}

export interface UserPayload {
  name: string;
  email: string;
  phone: string;
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
}

export interface ExpensePayload {
  title: string;
  category: string;
  amount: number;
  expenseDate: string;
  description: string;
}

export interface Claim {
  id: number;
  claimNumber: string;
  title: string;
  description?: string;
  claimAmount: number;
  submittedAt: string;
  status: ClaimStatus;
}

export interface ClaimPayload {
  claimNumber: string;
  title: string;
  description?: string;
  claimAmount: number;
  status: ClaimStatus;
}
