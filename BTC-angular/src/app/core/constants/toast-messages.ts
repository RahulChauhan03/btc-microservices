import { Claim, Expense, Trip, User } from '../models/domain.models';

export const TOAST_MESSAGES = {
  auth: {
    signedIn: 'Signed in successfully.',
  },
  trips: {
    created: 'Trip created successfully.',
    updated: 'Trip updated successfully.',
    deleted: 'Trip deleted successfully.',
    viewed: (trip: Trip) => `${trip.destination}: ${trip.purpose} from ${trip.startDate} to ${trip.endDate}.`,
  },
  expenses: {
    created: 'Expense added successfully.',
    updated: 'Expense updated successfully.',
    deleted: 'Expense deleted successfully.',
    viewed: (expense: Expense, tripName: string) =>
      `${expense.merchant}: ${expense.amount} on ${expense.expenseDate} for ${tripName}.`,
  },
  claims: {
    created: 'Claim submitted successfully.',
    updated: 'Claim updated successfully.',
    deleted: 'Claim deleted successfully.',
    viewed: (claim: Claim, tripName: string) => `${claim.claimNumber}: ${claim.status} for ${tripName}.`,
  },
  users: {
    created: 'User created successfully.',
    updated: 'User updated successfully.',
    deleted: 'User deleted successfully.',
    roleUpdated: 'User role updated successfully.',
    viewed: (user: User) => `${user.name}: ${user.role} in ${user.department}.`,
  },
  errors: {
    gatewayUnavailable: 'Unable to reach API Gateway. Please verify the backend services.',
    requestFailed: 'Request failed.',
  },
} as const;
