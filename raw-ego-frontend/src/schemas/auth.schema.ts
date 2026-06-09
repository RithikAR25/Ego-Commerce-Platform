/**
 * auth.schema.ts
 *
 * Zod validation schemas for auth forms.
 *
 * Rules mirror the backend @Valid constraints in:
 *   com.ego.raw_ego.auth.dto.request.RegisterRequest
 *   com.ego.raw_ego.auth.dto.request.LoginRequest
 *
 * These schemas serve two purposes:
 *   1. Client-side validation via React Hook Form's zodResolver
 *   2. TypeScript type inference via z.infer<typeof Schema>
 *
 * When the backend returns field-level errors (ApiErrorResponse.errors),
 * the field names must match these schema keys exactly.
 */

import { z } from 'zod';

// ── Login ─────────────────────────────────────────────────────────────────────

export const LoginSchema = z.object({
  email: z
    .string()
    .min(1, 'Email is required')
    .email('Please enter a valid email address'),

  password: z
    .string()
    .min(1, 'Password is required'),
});

export type LoginFormData = z.infer<typeof LoginSchema>;

// ── Register ──────────────────────────────────────────────────────────────────

export const RegisterSchema = z.object({
  firstName: z
    .string()
    .min(1, 'First name is required')
    .max(100, 'First name is too long'),

  lastName: z
    .string()
    .min(1, 'Last name is required')
    .max(100, 'Last name is too long'),

  email: z
    .string()
    .min(1, 'Email is required')
    .email('Please enter a valid email address')
    .max(254, 'Email is too long'),   // RFC 5321 max

  password: z
    .string()
    .min(8,  'Password must be at least 8 characters')
    .max(72, 'Password must be 72 characters or fewer')   // BCrypt hard limit
    .regex(/[A-Z]/,         'Must contain at least one uppercase letter')
    .regex(/[0-9]/,         'Must contain at least one number')
    .regex(/[^A-Za-z0-9]/, 'Must contain at least one special character'),

  phone: z
    .string()
    .regex(/^\+?[1-9]\d{9,14}$/, 'Please enter a valid phone number')
    .optional()
    .or(z.literal('')),
});

export type RegisterFormData = z.infer<typeof RegisterSchema>;
