import React, { useMemo } from 'react';
import {
  Box,
  Grid,
  Alert,
  Paper,
  Typography,
  Chip,
  Stack,
  Button,
  CircularProgress,
  useTheme,
} from '@mui/material';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import DataCard from '@/components/admin/DataCard';
import { useDashboardSummary } from '@/features/dashboard/hooks/useDashboard';
import { useReindex } from '@/hooks/useSearch';
import { toast } from '@/store/uiStore';
import { useNavigate } from 'react-router-dom';
import CurrencyRupeeIcon from '@mui/icons-material/CurrencyRupee';
import ShoppingBagIcon from '@mui/icons-material/ShoppingBag';
import AssignmentReturnIcon from '@mui/icons-material/AssignmentReturn';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import SyncIcon from '@mui/icons-material/Sync';


import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  ArcElement,
  Title,
  Tooltip,
  Legend,
  Filler,
} from 'chart.js';
import { Line, Doughnut } from 'react-chartjs-2';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  ArcElement,
  Title,
  Tooltip,
  Legend,
  Filler,  // required for fill: true on Line datasets
);

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Returns the last 7 calendar day labels: ["May 21", ..., "May 27"] */
function getLast7DayLabels(): string[] {
  const labels: string[] = [];
  const today = new Date();
  for (let i = 6; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(d.getDate() - i);
    labels.push(
      d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
    );
  }
  return labels;
}



const STATUS_LABEL: Record<string, string> = {
  PENDING_PAYMENT: 'Pending Payment',
  CONFIRMED:       'Confirmed',
  PROCESSING:      'Processing',
  SHIPPED:         'Shipped',
  DELIVERED:       'Delivered',
  CANCELLED:       'Cancelled',
  REFUNDED:        'Refunded',
};

// ── Component ─────────────────────────────────────────────────────────────────

const AdminDashboardPage: React.FC = () => {
  const { data, isLoading, isError } = useDashboardSummary();
  const { mutateAsync: triggerReindex, isPending: isReindexing } = useReindex();
  const navigate = useNavigate();
  const theme = useTheme();

  const handleReindex = async () => {
    try {
      const msg = await triggerReindex();
      toast.success(msg);
    } catch {
      toast.error('Failed to trigger reindex');
    }
  };

  const dayLabels = useMemo(() => getLast7DayLabels(), []);

  // ── Revenue Trend (Line chart) ─────────────────────────────────────────────
  const lineChartData = {
    labels: dayLabels,
    datasets: [
      {
        label: 'Revenue (₹)',
        data: data?.revenueTrend ?? Array(7).fill(0),
        borderColor: theme.palette.text.secondary,
        backgroundColor: theme.palette.surface.tertiary,
        borderWidth: 2,
        tension: 0.4,
        fill: true,
        pointBackgroundColor: theme.palette.text.secondary,
        pointRadius: 3,
        pointHoverRadius: 5,
      },
    ],
  };

  const lineChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      y: {
        beginAtZero: true,
        ticks: {
          callback: (value: number | string) => `₹${Number(value).toLocaleString('en-IN')}`,
          color: theme.palette.text.secondary,
          font: { family: '"Work Sans", sans-serif', size: 11 },
        },
        grid: { color: theme.palette.border.default },
      },
      x: {
        grid: { display: false },
        ticks: { color: theme.palette.text.secondary, font: { family: '"Work Sans", sans-serif', size: 11 } },
      },
    },
  };

  // ── Orders by Status (Doughnut chart) ─────────────────────────────────────
  const statusEntries = Object.entries(data?.ordersByStatus ?? {});
  const doughnutData = {
    labels: statusEntries.map(([k]) => STATUS_LABEL[k] ?? k),
    datasets: [
      {
        data: statusEntries.map(([, v]) => v),
        backgroundColor: statusEntries.map(([k]) => theme.palette.statusColors?.[k] ?? theme.palette.text.secondary),
        borderWidth: 2,
        borderColor: theme.palette.background.default,
        hoverOffset: 6,
      },
    ],
  };

  const doughnutOptions = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '68%',
    plugins: {
      legend: {
        position: 'bottom' as const,
        labels: {
          boxWidth: 10,
          padding: 12,
          font: { size: 11, family: '"Work Sans", sans-serif' },
          color: theme.palette.text.secondary,
        },
      },
    },
  };

  return (
    <Box>
      <AdminPageHeader
        title="Dashboard Overview"
        subtitle="Key metrics and platform performance."
        action={
          <Button
            variant="outlined"
            startIcon={isReindexing ? <CircularProgress size={14} sx={{ color: 'text.secondary' }} /> : <SyncIcon />}
            onClick={handleReindex}
            disabled={isReindexing}
            sx={{
              borderRadius: 0,
              borderColor: 'border.default',
              color: 'text.secondary',
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              fontWeight: 700,
              fontSize: '0.65rem',
              letterSpacing: '0.1em',
              '&:hover': { borderColor: 'text.primary', color: 'text.primary', bgcolor: 'transparent' },
            }}
          >
            {isReindexing ? 'Reindexing...' : 'Reindex Search'}
          </Button>
        }
      />

      {isError && (
        <Alert severity="warning" sx={{ mb: 4, borderRadius: '8px' }}>
          Unable to fetch dashboard metrics. Check that the backend is running and you are
          logged in as ADMIN.
        </Alert>
      )}

      {/* KPI Cards */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <DataCard
            title="Total Revenue"
            value={`₹${Number(data?.totalRevenue ?? 0).toLocaleString('en-IN')}`}
            icon={<CurrencyRupeeIcon />}
            isLoading={isLoading}
            trend={{ value: `${data?.deliveredOrders ?? 0} delivered`, isPositive: true, label: '' }}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <DataCard
            title="Total Orders"
            value={data?.totalOrders ?? '0'}
            icon={<ShoppingBagIcon />}
            isLoading={isLoading}
            trend={{ value: `${data?.pendingOrders ?? 0} active`, isPositive: true, label: '' }}
            onClick={() => navigate('/admin/orders')}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <DataCard
            title="Pending Returns"
            value={data?.refundCount ?? '0'}
            icon={<AssignmentReturnIcon />}
            isLoading={isLoading}
            trend={{ value: 'Needs review', isPositive: false, label: '' }}
            onClick={() => navigate('/admin/returns')}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <DataCard
            title="Low Stock Alerts"
            value={data?.lowStockCount ?? '0'}
            icon={<WarningAmberIcon />}
            isLoading={isLoading}
            onClick={() => navigate('/admin/inventory')}
          />
        </Grid>
      </Grid>

      {/* Charts row */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {/* Revenue Trend — Line Chart */}
        <Grid size={{ xs: 12, lg: 8 }}>
          <Paper
            elevation={0}
            sx={{
              p: 3,
              borderRadius: 0,
              border: (theme) => `1px solid ${theme.palette.border.default}`,
              bgcolor: 'surface.secondary',
            }}
          >
            <Typography
              sx={{
                fontFamily: (theme) => theme.typography.fontFamilyUtility,
                fontWeight: 700,
                fontSize: '0.65rem',
                letterSpacing: '0.2em',
                color: 'text.secondary',
                textTransform: 'uppercase',
                mb: 3,
              }}
            >
              Revenue Trend — Last 7 Days
            </Typography>
            <Box sx={{ height: 280 }}>
              <Line data={lineChartData} options={lineChartOptions as any} />
            </Box>
          </Paper>
        </Grid>

        {/* Orders by Status — Doughnut */}
        <Grid size={{ xs: 12, lg: 4 }}>
          <Paper
            elevation={0}
            sx={{
              p: 3,
              borderRadius: 0,
              border: (theme) => `1px solid ${theme.palette.border.default}`,
              bgcolor: 'surface.secondary',
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
            }}
          >
            <Typography
              sx={{
                fontFamily: (theme) => theme.typography.fontFamilyUtility,
                fontWeight: 700,
                fontSize: '0.65rem',
                letterSpacing: '0.2em',
                color: 'text.secondary',
                textTransform: 'uppercase',
                mb: 2,
              }}
            >
              Orders by Status
            </Typography>
            {statusEntries.length > 0 ? (
              <Box sx={{ flex: 1, minHeight: 240 }}>
                <Doughnut data={doughnutData} options={doughnutOptions} />
              </Box>
            ) : (
              <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', fontStyle: 'italic' }}>
                  {isLoading ? 'Loading…' : 'No order data yet.'}
                </Typography>
              </Box>
            )}
          </Paper>
        </Grid>
      </Grid>

      {/* Recent Orders panel */}
      <Paper
        elevation={0}
        sx={{
          p: 3,
          borderRadius: 0,
          border: (theme) => `1px solid ${theme.palette.border.default}`,
          bgcolor: 'surface.secondary',
        }}
      >
        <Typography
          sx={{
            fontFamily: (theme) => theme.typography.fontFamilyUtility,
            fontWeight: 700,
            fontSize: '0.65rem',
            letterSpacing: '0.2em',
            color: 'text.secondary',
            textTransform: 'uppercase',
            mb: 3,
          }}
        >
          Recent Orders
        </Typography>
        <Box sx={{ borderTop: (theme) => `1px solid ${theme.palette.border.default}` }} />

        {isLoading ? (
          <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.85rem', py: 3 }}>Loading…</Typography>
        ) : !data?.recentOrders?.length ? (
          <Box sx={{ py: 5, textAlign: 'center' }}>
            <CheckCircleIcon sx={{ fontSize: 36, color: 'border.default', mb: 1 }} />
            <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', fontStyle: 'italic' }}>No orders yet.</Typography>
          </Box>
        ) : (
          <Stack spacing={0}>
            {data.recentOrders.map((order) => (
              <Box
                key={order.id}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  py: 2,
                  borderBottom: (theme) => `1px solid ${theme.palette.border.default}`,
                  '&:last-child': { borderBottom: 'none' },
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.primary', minWidth: 72, }}>
                    #{order.id}
                  </Typography>
                  <Chip
                    label={STATUS_LABEL[order.status] ?? order.status}
                    size="small"
                    sx={{
                      bgcolor: (theme) => theme.palette.statusColors?.[order.status] ?? theme.palette.statusColors?.INACTIVE,
                      color: 'text.primary',
                      fontFamily: (theme) => theme.typography.fontFamilyUtility,
                      fontWeight: 700,
                      fontSize: '0.6rem',
                      letterSpacing: '0.05em',
                      borderRadius: 0,
                    }}
                  />
                </Box>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.8rem' }}>
                    {new Date(order.createdAt).toLocaleDateString(undefined, {
                      month: 'short',
                      day: 'numeric',
                      year: 'numeric',
                    })}
                  </Typography>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, fontWeight: 700, color: 'text.primary', minWidth: 80, textAlign: 'right' }}>
                    ₹{Number(order.grandTotal).toLocaleString('en-IN')}
                  </Typography>
                </Box>
              </Box>
            ))}
          </Stack>
        )}
      </Paper>
    </Box>
  );
};

export default AdminDashboardPage;
