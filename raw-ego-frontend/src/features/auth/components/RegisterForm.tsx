/**
 * RegisterForm.tsx
 *
 * Registration form component.
 *
 * Fields: firstName, lastName, email, password, phone (optional)
 * All fields validated client-side by Zod before API call.
 * Backend field errors (409 email conflict, etc.) mapped to form fields.
 *
 * Password requirements displayed inline below the password field.
 */

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  TextField,
  Button,
  Box,
  Typography,
  CircularProgress,
  Alert,
  Grid,
  IconButton,
  InputAdornment,
} from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import { Link } from 'react-router-dom';
import { useState } from 'react';
import { motion } from 'framer-motion';
import { RegisterSchema, type RegisterFormData } from '@/schemas/auth.schema';
import { useRegister } from '@/hooks/useAuth';

interface RegisterFormProps {
  onPasswordVisibilityChange?: (isVisible: boolean) => void;
}

const RegisterForm = ({ onPasswordVisibilityChange }: RegisterFormProps = {}) => {
  const [rootError, setRootError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(RegisterSchema),
    mode:     'onBlur',
  });

  const { mutate: registerUser, isPending } = useRegister({
    onFieldError: (field, msg) => {
      if (field === 'email' || field === 'firstName' || field === 'lastName' || field === 'password' || field === 'phone') {
        setError(field as keyof RegisterFormData, { message: msg });
      } else {
        setRootError(msg);
      }
    },
  });

  const onSubmit = (data: RegisterFormData) => {
    setRootError(null);
    registerUser(data);
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
      {rootError && (
        <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }}>
          <Alert severity="error" sx={{ borderRadius: 0, fontSize: '0.85rem' }}>
            {rootError}
          </Alert>
        </motion.div>
      )}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            {...register('firstName')}
            id="register-firstName"
            label="First Name"
            autoComplete="given-name"
            autoFocus
            fullWidth
            error={!!errors.firstName}
            helperText={errors.firstName?.message}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            {...register('lastName')}
            id="register-lastName"
            label="Last Name"
            autoComplete="family-name"
            fullWidth
            error={!!errors.lastName}
            helperText={errors.lastName?.message}
          />
        </Grid>
      </Grid>

      <TextField
        {...register('email')}
        id="register-email"
        label="Email Address"
        type="email"
        autoComplete="email"
        fullWidth
        error={!!errors.email}
        helperText={errors.email?.message}
      />

      <TextField
        {...register('password')}
        id="register-password"
        label="Password"
        type={showPassword ? 'text' : 'password'}
        autoComplete="new-password"
        fullWidth
        error={!!errors.password}
        helperText={
          errors.password?.message ??
          'Min 8 chars, uppercase, number, and special character'
        }
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

      <TextField
        {...register('phone')}
        id="register-phone"
        label="Phone Number (optional)"
        type="tel"
        autoComplete="tel"
        fullWidth
        error={!!errors.phone}
        helperText={errors.phone?.message ?? 'e.g. +91 98765 43210'}
      />

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
            'Create Account'
          )}
        </Button>
      </motion.div>

      <Box sx={{ textAlign: 'center', mt: 1 }}>
        <Typography variant="body2" color="text.secondary">
          Already have an account?{' '}
          <Link
            to="/login"
            style={{ textDecoration: 'none' }}
          >
            <Typography
              component="span"
              variant="body2"
              sx={{ fontWeight: 700, color: 'text.primary', '&:hover': { textDecoration: 'underline' } }}
            >
              Login
            </Typography>
          </Link>
        </Typography>
      </Box>
    </Box>
  );
};

export default RegisterForm;
