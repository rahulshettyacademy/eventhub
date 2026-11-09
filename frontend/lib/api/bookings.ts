import { apiClient } from './client';
import type {
  ApiResponse,
  PaginatedResponse,
  Booking,
  CreateBookingPayload,
} from '@/types';

function buildQuery(filters?: Record<string, unknown>): string {
  if (!filters) return '';

  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value));
    }
  });

  const query = params.toString();
  return query ? `?${query}` : '';
}

export const bookingsApi = {
  getAll: (pageOrFilters: number | Record<string, unknown> = 1, limit = 10) => {
    const filters = typeof pageOrFilters === 'number'
      ? { page: pageOrFilters, limit }
      : (pageOrFilters ?? {});

    return apiClient.get<PaginatedResponse<Booking>>(`/bookings${buildQuery(filters)}`);
  },

  getBookings: (filters: Record<string, unknown> = {}) =>
    apiClient.get<PaginatedResponse<Booking>>(`/bookings${buildQuery(filters)}`),

  getById: (id: string | number) =>
    apiClient.get<ApiResponse<Booking>>(`/bookings/${id}`),

  getBookingById: (id: string | number) =>
    apiClient.get<ApiResponse<Booking>>(`/bookings/${id}`),

  getByRef: (ref: string) =>
    apiClient.get<ApiResponse<Booking>>(`/bookings/ref/${ref}`),

  getBookingByRef: (ref: string) =>
    apiClient.get<ApiResponse<Booking>>(`/bookings/ref/${ref}`),

  create: (payload: CreateBookingPayload) =>
    apiClient.post<ApiResponse<Booking>>('/bookings', payload),

  createBooking: (payload: CreateBookingPayload) =>
    apiClient.post<ApiResponse<Booking>>('/bookings', payload),

  cancel: (id: string | number) =>
    apiClient.delete<ApiResponse<Booking>>(`/bookings/${id}`),

  cancelBooking: (id: string | number) =>
    apiClient.delete<ApiResponse<Booking>>(`/bookings/${id}`),

  clearAll: () =>
    apiClient.delete<ApiResponse<null>>('/bookings'),

  clearAllBookings: () =>
    apiClient.delete<ApiResponse<null>>('/bookings'),
};
