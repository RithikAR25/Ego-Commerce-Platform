/**
 * LoginForm.tsx
 *
 * Login form component.
 *
 * Stack:
 *   React Hook Form + zodResolver (validation)
 *   useLogin mutation (from useAuth.ts) for API call + auth store update
 *
 * Field errors come from two sources:
 *   1. Zod: client-side (before API call) — invalid email format, empty fields
 *   2. Backend: after API call — "Invalid email or password" mapped to root error
 *
 * Design:
 *   Clean, minimal — no decorative elements in the form itself.
 *   Prominent CTA with loading state.
 *   Link to register page.
 */

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { TextField, Button, Box, Typography, CircularProgress, Alert, IconButton, InputAdornment } from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import { Link, useSearchParams } from 'react-router-dom';
import { useState } from 'react';
import { motion } from 'framer-motion';
import { LoginSchema, type LoginFormData } from '@/schemas/auth.schema';
import { useLogin } from '@/hooks/useAuth';

interface LoginFormProps {
  onPasswordVisibilityChange?: (isVisible: boolean) => void;
}

const LoginForm = ({ onPasswordVisibilityChange }: LoginFormProps = {}) => {
  const [rootError, setRootError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [searchParams] = useSearchParams();
  const resetSuccess = searchParams.get('reset') === 'success';

  const { mutate: login, isPending } = useLogin({
    onFieldError: (field, msg) => {
      if (field === 'root' || field === 'email' || field === 'password') {
        // Map backend auth errors to a root-level message (not field-level for security)
        setRootError(msg);
      }
    },
  });

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormData>({
    resolver: zodResolver(LoginSchema),
    mode:     'onBlur',
  });

  const onSubmit = (data: LoginFormData) => {
    setRootError(null);
    login(data);
  };

  const handleTogglePassword = () => {
    const nextState = !showPassword;
    setShowPassword(nextState);
    if (onPasswordVisibilityChange) {
      onPasswordVisibilityChange(nextState);
    }
  };

  return (
    <Box
      component="form"
      onSubmit={handleSubmit(onSubmit)}
      noValidate
      sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}
    >
      {/* Password reset success banner */}
      {resetSuccess && (
        <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }}>
          <Alert severity="success" sx={{ borderRadius: 0, fontSize: '0.85rem' }}>
            Password updated successfully. Please log in with your new password.
          </Alert>
        </motion.div>
      )}

      {/* Root-level error (wrong credentials) */}
      {rootError && (
        <motion.div
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <Alert
            severity="error"
            sx={{ borderRadius: 0, fontSize: '0.85rem' }}
          >
            {rootError}
          </Alert>
        </motion.div>
      )}

      <TextField
        {...register('email')}
        id="login-email"
        label="Email Address"
        type="email"
        autoComplete="email"
        autoFocus
        fullWidth
        error={!!errors.email}
        helperText={errors.email?.message}
        slotProps={{ htmlInput: { 'aria-label': 'Email Address' } }}
      />

      <TextField
        {...register('password')}
        id="login-password"
        label="Password"
        type={showPassword ? 'text' : 'password'}
        autoComplete="current-password"
        fullWidth
        error={!!errors.password}
        helperText={errors.password?.message}
        slotProps={{
          input: {
            endAdornment: (
              <InputAdornment position="end">
                <IconButton
                  aria-label={showPassword ? 'Hide Password' : 'Show Password'}
                  onClick={handleTogglePassword}
                  onMouseDown={(e) => e.preventDefault()}
                  edge="end"
                >
                  {showPassword ? <VisibilityOff /> : <Visibility />}
                </IconButton>
              </InputAdornment>
            ),
          },
          htmlInput: { 'aria-label': 'Password' }
        }}
      />

      {/* Forgot password link */}
      <Box sx={{ textAlign: 'right', mt: -1 }}>
        <Link to="/auth/forgot-password" style={{ textDecoration: 'none' }}>
          <Typography
            variant="caption"
            sx={{ color: 'text.secondary', fontWeight: 500, '&:hover': { color: 'text.primary', textDecoration: 'underline' } }}
          >
            Forgot password?
          </Typography>
        </Link>
      </Box>

      <motion.div whileTap={{ scale: 0.98 }}>
        <Button
          type="submit"
          variant="contained"
          fullWidth
          disabled={isPending}
          sx={{ py: 1.75, mt: 1 }}
        >
          {isPending ? (
            <CircularProgress size={20} color="inherit" />
          ) : (
            'Login'
          )}
        </Button>
      </motion.div>

      <Box sx={{ textAlign: 'center', mt: 1 }}>
        <Typography variant="body2" color="text.secondary">
          Don't have an account?{' '}
          <Link
            to="/register"
            style={{ textDecoration: 'none' }}
          >
            <Typography
              component="span"
              variant="body2"
              sx={{ fontWeight: 700, color: 'text.primary', '&:hover': { textDecoration: 'underline' } }}
            >
              Create one
            </Typography>
          </Link>
        </Typography>
      </Box>
    </Box>
  );
};

export default LoginForm;
