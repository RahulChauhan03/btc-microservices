import { Claim, Expense, Trip, User } from '../models/domain.models';

export const TOAST_MESSAGES = {
  auth: {
    signedIn: 'Signed in successfully.',
  },
  trips: {
    created: 'Trip created successfully.',
    updated: 'Trip updated successfully.',
    deleted: 'Trip deleted successfully.',
    viewed: (trip: Trip) => `${trip.tripCode} to ${trip.destination} from ${trip.startDate} to ${trip.endDate}.`,
  },
  expenses: {
    created: 'Expense added successfully.',
    updated: 'Expense updated successfully.',
    deleted: 'Expense deleted successfully.',
    viewed: (expense: Expense) => `${expense.title}: ${expense.amount} on ${expense.expenseDate}.`,
  },
  claims: {
    created: 'Claim submitted successfully.',
    updated: 'Claim updated successfully.',
    deleted: 'Claim deleted successfully.',
    viewed: (claim: Claim) => `${claim.claimNumber}: ${claim.status} for ${claim.claimAmount}.`,
  },
  users: {
    created: 'User created successfully.',
    updated: 'User updated successfully.',
    deleted: 'User deleted successfully.',
    viewed: (user: User) => `${user.name}: ${user.email} | ${user.phone}.`,
  },
  errors: {
    gatewayUnavailable: 'Unable to reach API Gateway. Please verify the backend services.',
    requestFailed: 'Request failed.',
  },
} as const;
