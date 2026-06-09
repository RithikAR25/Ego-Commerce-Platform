import React from 'react';
import {
  Box,
  Paper,
  Typography,
  Grid,
  Chip,
  Button,
  Alert,
  CircularProgress,
  Stack,
  alpha,
} from '@mui/material';
import { useNavigate, useParams } from 'react-router-dom';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import PersonIcon from '@mui/icons-material/Person';
import EmailIcon from '@mui/icons-material/Email';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import { useAdminUser } from '@/features/users/admin/hooks/useAdminUsers';
import AdminPageHeader from '@/components/admin/AdminPageHeader';

// ── Helper: labelled field row ────────────────────────────────────────────────

const FieldRow: React.FC<{
  icon: React.ReactNode;
  label: string;
  value: React.ReactNode;
}> = ({ icon, label, value }) => (
  <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2, py: 2, borderBottom: (theme) => `1px solid ${theme.palette.border.default}`, '&:last-child': { borderBottom: 'none' } }}>
    <Box sx={{ color: 'text.secondary', mt: 0.2, flexShrink: 0 }}>{icon}</Box>
    <Box sx={{ flex: 1 }}>
      <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.6rem', letterSpacing: '0.15em', color: 'text.secondary', textTransform: 'uppercase', display: 'block', mb: 0.5 }}>
        {label}
      </Typography>
      <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.primary', }}>
        {value}
      </Typography>
    </Box>
  </Box>
);

// ── Page ──────────────────────────────────────────────────────────────────────

const AdminUserDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const userId = Number(id);

  const { data: user, isLoading, isError } = useAdminUser(userId);

  return (
    <Box>
      <Button
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate('/admin/users')}
        sx={{ mb: 4, borderRadius: 0, p: 0, color: 'text.secondary', fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.72rem', letterSpacing: '0.1em', fontWeight: 600, '&:hover': { color: 'text.primary', bgcolor: 'transparent' } }}
      >
        ← Back to Users
      </Button>

      {!user && !isLoading && !isError && null}

      {user && (
        <AdminPageHeader
          title={`${user.firstName} ${user.lastName}`}
          subtitle={user.email}
        />
      )}

      {/* Loading */}
      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 10 }}>
          <CircularProgress sx={{ color: 'text.secondary' }} />
        </Box>
      )}

      {/* Error */}
      {isError && (
        <Alert severity="error" sx={{ borderRadius: 0 }}>
          User not found or failed to load. Please check the user ID and try again.
        </Alert>
      )}

      {/* Profile card */}
      {user && (
        <Grid container spacing={4}>
          {/* Left: identity card */}
          <Grid size={{ xs: 12, md: 4 }}>
            <Paper
              elevation={0}
              sx={{
                p: 4,
                borderRadius: 0,
                border: (theme) => `1px solid ${theme.palette.border.default}`,
                bgcolor: 'surface.secondary',
                textAlign: 'center',
              }}
            >
              {/* Avatar */}
              <Box
                sx={{
                  width: 80,
                  height: 80,
                  bgcolor: 'surface.tertiary',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  mx: 'auto',
                  mb: 3,
                  border: (theme) => `1px solid ${theme.palette.text.secondary}`,
                }}
              >
                <Typography variant="sectionTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.secondary', }}>
                  {user.firstName[0]?.toUpperCase()}{user.lastName[0]?.toUpperCase()}
                </Typography>
              </Box>

              <Typography variant="productTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', mb: 0.5 }}>
                {user.firstName} {user.lastName}
              </Typography>
              <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.85rem', mb: 3 }}>
                {user.email}
              </Typography>

              <Stack direction="row" spacing={1} sx={{ justifyContent: 'center' }}>
                <Chip
                  label={user.role}
                  size="small"
                  sx={{
                    bgcolor: 'surface.tertiary',
                    color: user.role === 'ADMIN' ? 'text.secondary' : 'text.primary',
                    fontFamily: (theme) => theme.typography.fontFamilyUtility,
                    fontWeight: 700,
                    fontSize: '0.6rem',
                    letterSpacing: '0.1em',
                    border: (theme) => `1px solid ${theme.palette.border.default}`,
                    borderRadius: 0,
                  }}
                />
                <Chip
                  label={user.active ? 'Active' : 'Suspended'}
                  size="small"
                  sx={{
                    bgcolor: (theme) => alpha(user.active ? theme.palette.success.main : theme.palette.error.main, 0.12),
                    color: user.active ? 'success.main' : 'error.main',
                    px: 1,
                    py: 0.5,
                    border: (theme) => `1px solid ${alpha(user.active ? theme.palette.success.main : theme.palette.error.main, 0.4)}`,
                    borderRadius: 0,
                  }}
                />
              </Stack>
            </Paper>
          </Grid>

          {/* Right: details */}
          <Grid size={{ xs: 12, md: 8 }}>
            <Paper
              elevation={0}
              sx={{
                borderRadius: 0,
                border: (theme) => `1px solid ${theme.palette.border.default}`,
                bgcolor: 'surface.secondary',
                overflow: 'hidden',
              }}
            >
              <Box sx={{ px: 3, py: 2.5, borderBottom: (theme) => `1px solid ${theme.palette.border.default}` }}>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.2em', color: 'text.secondary', textTransform: 'uppercase' }}>
                  Account Details
                </Typography>
              </Box>
              <Box sx={{ px: 3 }}>
                <FieldRow
                  icon={<PersonIcon fontSize="small" />}
                  label="User ID"
                  value={`#${user.id}`}
                />
                <FieldRow
                  icon={<PersonIcon fontSize="small" />}
                  label="Full Name"
                  value={`${user.firstName} ${user.lastName}`}
                />
                <FieldRow
                  icon={<EmailIcon fontSize="small" />}
                  label="Email Address"
                  value={user.email}
                />
                <FieldRow
                  icon={<AdminPanelSettingsIcon fontSize="small" />}
                  label="Account Role"
                  value={
                    <Chip
                      label={user.role}
                      size="small"
                      sx={{
                        bgcolor: 'surface.tertiary',
                        color: user.role === 'ADMIN' ? 'text.secondary' : 'text.primary',
                        fontFamily: (theme) => theme.typography.fontFamilyUtility,
                        fontWeight: 700,
                        fontSize: '0.6rem',
                        letterSpacing: '0.1em',
                        border: (theme) => `1px solid ${theme.palette.border.default}`,
                        borderRadius: 0,
                      }}
                    />
                  }
                />
                <FieldRow
                  icon={<CalendarTodayIcon fontSize="small" />}
                  label="Registered"
                  value={new Date(user.createdAt).toLocaleDateString(undefined, {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                  })}
                />
                <FieldRow
                  icon={<PersonIcon fontSize="small" />}
                  label="Account Status"
                  value={
                    <Chip
                      label={user.active ? 'Active' : 'Suspended'}
                      size="small"
                      sx={{
                        bgcolor: (theme) => alpha(user.active ? theme.palette.success.main : theme.palette.error.main, 0.12),
                        color: user.active ? 'success.main' : 'error.main',
                        fontFamily: (theme) => theme.typography.fontFamilyUtility,
                        fontWeight: 700,
                        fontSize: '0.65rem',
                        letterSpacing: '0.1em',
                        border: (theme) => `1px solid ${alpha(user.active ? theme.palette.success.main : theme.palette.error.main, 0.4)}`,
                        borderRadius: 0,
                      }}
                    />
                  }
                />
              </Box>
            </Paper>
          </Grid>
        </Grid>
      )}
    </Box>
  );
};

export default AdminUserDetailPage;
