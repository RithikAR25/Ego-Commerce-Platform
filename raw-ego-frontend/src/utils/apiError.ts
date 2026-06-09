/**
 * apiError.ts
 *
 * Extracts structured errors from Axios error responses.
 *
 * Backend sends ApiErrorResponse:
 *   { success: false, message: "...", errors: { field: "msg", ... }, timestamp: "..." }
 *
 * Usage in mutations:
 *   onError: (error) => {
 *     const { message, fieldErrors } = extractApiError(error);
 *     if (fieldErrors) {
 *       Object.entries(fieldErrors).forEach(([field, msg]) =>
 *         form.setError(field as keyof FormType, { message: msg })
 *       );
 *     } else {
 *       toast.error(message);
 *     }
 *   }
 */

import axios from 'axios';
import type { ApiErrorResponse } from '@/types/api.types';

export interface ExtractedApiError {
  message: string;
  fieldErrors?: Record<string, string>;
  statusCode?: number;
}

export const extractApiError = (error: unknown): ExtractedApiError => {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as ApiErrorResponse | undefined;
    
    // Map backend List<ApiError> format: [{field: 'email', message: '...'}] to Record<string, string>
    let fieldErrors: Record<string, string> | undefined;
    
    if (Array.isArray(data?.errors)) {
      fieldErrors = {};
      data.errors.forEach((err) => {
        if (err.field && err.message) {
          fieldErrors![err.field] = err.message;
        }
      });
    } else if (data?.errors && typeof data.errors === 'object') {
      // Fallback just in case it ever returns a map
      fieldErrors = data.errors as Record<string, string>;
    }

    return {
      message:     data?.message ?? 'An unexpected error occurred.',
      fieldErrors,
      statusCode:  error.response?.status,
    };
  }

  if (error instanceof Error) {
    return { message: error.message };
  }

  return { message: 'An unexpected error occurred.' };
};
