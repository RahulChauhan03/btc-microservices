export type ClaimStatus = 'DRAFT' | 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED';
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
  role: 'ADMIN' | 'EMPLOYEE' | 'MANAGER';
  department: string;
  status: 'ACTIVE' | 'INACTIVE';
}

export interface Trip {
  id: number;
  destination: string;
  purpose: string;
  startDate: string;
  endDate: string;
  estimatedBudget: number;
  status: TripStatus;
  travelerName?: string;
}

export interface TripPayload {
  destination: string;
  purpose: string;
  startDate: string;
  endDate: string;
  estimatedBudget: number;
  status: TripStatus;
}

export interface Expense {
  id: number;
  tripId: number;
  category: 'TRAVEL' | 'HOTEL' | 'MEAL' | 'TRANSPORT' | 'OTHER';
  amount: number;
  expenseDate: string;
  merchant: string;
  description: string;
  reimbursable: boolean;
}

export interface ExpensePayload {
  tripId: number;
  category: string;
  amount: number;
  expenseDate: string;
  merchant: string;
  description: string;
  reimbursable: boolean;
}

export interface Claim {
  id: number;
  tripId: number;
  claimNumber: string;
  totalAmount: number;
  submittedOn: string;
  status: ClaimStatus;
  approver?: string;
  comment?: string;
}

export interface ClaimPayload {
  tripId: number;
  comment: string;
}
