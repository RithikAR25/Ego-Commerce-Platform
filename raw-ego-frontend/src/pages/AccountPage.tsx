/**
 * AccountPage.tsx — F7 full implementation
 *
 * Tabs: Profile | Addresses | Orders | Security
 *
 * - Profile:   Name, email (read-only), phone. Edit + Save via PATCH /auth/me (future).
 * - Addresses: Full address book (list, add, edit, delete, set-default).
 * - Orders:    Shortcut link to /orders.
 * - Security:  Change password → navigates to /auth/forgot-password.
 */

import { useState } from 'react';
import {
  Box, Typography, Avatar, Chip, Paper, Button, Alert,
  Tabs, Tab, CircularProgress, IconButton, Tooltip,
  Dialog, DialogTitle, DialogContent, DialogActions,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import { useCurrentUser } from '@/hooks/useAuth';
import {
  listAddresses, addAddress, updateAddress,
  deleteAddress, setDefaultAddress,
  type UserAddress, type AddressRequest,
} from '@/api/address.api';
import PageWrapper   from '@/components/layout/PageWrapper';
import PageLoader    from '@/components/ui/PageLoader';
import AddressDialog from '@/components/ui/AddressDialog';
import { useOrders } from '@/features/orders/hooks/useOrders';


// ── Icons (text-based fallbacks — no icon lib dependency) ─────────────────────
const EditIcon     = () => <span style={{ fontSize: 16 }}>✏</span>;
const DeleteIcon   = () => <span style={{ fontSize: 16 }}>🗑</span>;
const StarIcon     = () => <span style={{ fontSize: 14 }}>★</span>;
const AddIcon      = () => <span style={{ fontSize: 20 }}>＋</span>;

// ── Address form default ───────────────────────────────────────────────────────
const emptyForm = (): AddressRequest => ({
  fullName: '', phone: '', addressLine1: '', addressLine2: '',
  landmark: '', city: '', state: '', pinCode: '',
  country: 'India', addressType: 'HOME', setAsDefault: false,
});

const addrQueryKey = ['addresses'];

// ══════════════════════════════════════════════════════════════════════════════
const AccountPage = () => {
  const { user: storeUser }             = useAuthStore();
  const { data: freshUser, isLoading }  = useCurrentUser();
  const navigate                        = useNavigate();
  const qc                              = useQueryClient();

  const [tab, setTab]                   = useState(0);

  // ── Address state ───────────────────────────────────────────────────────────
  const [addrDialog, setAddrDialog]     = useState(false);
  const [editAddr, setEditAddr]         = useState<UserAddress | null>(null);
  const [addrForm, setAddrForm]         = useState<AddressRequest>(emptyForm());
  const [addrError, setAddrError]       = useState<string | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<UserAddress | null>(null);

  const user = freshUser ?? storeUser;
  if (isLoading && !user) return <PageLoader />;
  if (!user) return null;

  const initial    = user.firstName?.[0]?.toUpperCase() ?? 'U';
  const fullName   = `${user.firstName} ${user.lastName}`;
  const joinedDate = new Date(user.createdAt).toLocaleDateString('en-IN', {
    year: 'numeric', month: 'long', day: 'numeric',
  });

  return (
    <PageWrapper>
      <Box sx={{ maxWidth: 860, mx: 'auto', py: { xs: 6, md: 10 }, px: 2 }}>

        {/* ── Header ──────────────────────────────────────────── */}
        <Box
          sx={{
            display: 'flex',
            alignItems: 'flex-end',
            gap: 4,
            pb: 6,
            mb: 6,
            borderBottom: (theme) => `1px solid ${theme.palette.border.default}`,
          }}
        >
          <Avatar
            sx={{
              width: 72, height: 72,
              fontFamily: (theme) => theme.typography.fontFamilyDisplay,
              fontSize: '2rem',
              fontWeight: 700,
              bgcolor: 'surface.tertiary',
              color: 'text.primary',
              borderRadius: 0,
            }}
          >
            {initial}
          </Avatar>
          <Box>
            <Typography
              variant="overline"
              sx={{
                fontFamily: (theme) => theme.typography.fontFamilyUtility,
                fontWeight: 600,
                fontSize: '0.6rem',
                letterSpacing: '0.25em',
                color: 'text.secondary',
                display: 'block',
                mb: 1,
              }}
            >
              {user.role}
            </Typography>
            <Typography
              sx={{
                fontFamily: (theme) => theme.typography.fontFamilyDisplay,
                fontWeight: 700,
                fontSize: { xs: '1.75rem', md: '2.5rem' },
                color: 'text.primary',
                lineHeight: 1,
              }}
            >
              {fullName}
            </Typography>
            <Typography
              sx={{
                fontFamily: (theme) => theme.typography.fontFamilyEditorial,
                color: 'text.secondary',
                fontSize: '0.85rem',
                mt: 1,
              }}
            >
              Member since {joinedDate}
            </Typography>
          </Box>
        </Box>

        {/* ── Tabs ──────────────────────────────────────────── */}
        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v)}
          sx={{
            borderBottom: (theme) => `1px solid ${theme.palette.border.default}`,
            mb: 6,
            '& .MuiTab-root': {
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              fontWeight: 700,
              letterSpacing: '0.12em',
              fontSize: '0.65rem',
              textTransform: 'uppercase',
              minWidth: 80,
              color: 'text.secondary',
            },
            '& .Mui-selected': { color: 'text.primary !important' },
            '& .MuiTabs-indicator': { bgcolor: 'text.primary', height: '1px' },
          }}
        >
          <Tab label="Profile"   id="tab-profile"   aria-controls="panel-profile" />
          <Tab label="Addresses" id="tab-addresses" aria-controls="panel-addresses" />
          <Tab label="Orders"    id="tab-orders"    aria-controls="panel-orders" />
          <Tab label="Security"  id="tab-security"  aria-controls="panel-security" />
        </Tabs>

        <AnimatePresence mode="wait">

          {/* ── PROFILE TAB ──────────────────────────────────────────────── */}
          {tab === 0 && (
            <TabPanel key="profile" id="panel-profile">
              <Paper
                elevation={0}
                sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', borderRadius: 0, p: { xs: 3, md: 4 } }}
              >
                {[
                  { label: 'First Name',     value: user.firstName },
                  { label: 'Last Name',      value: user.lastName },
                  { label: 'Email',          value: user.email },
                  { label: 'Phone',          value: user.phone ?? '—' },
                  { label: 'Member since',   value: joinedDate },
                  { label: 'Email verified', value: user.emailVerified ? '✅ Verified' : '⚠ Not verified' },
                ].map(({ label, value }) => (
                  <Box
                    key={label}
                    sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', py: 1.75, borderBottom: '1px solid', borderColor: 'divider', '&:last-child': { borderBottom: 'none' } }}
                  >
                    <Typography variant="caption" color="text.secondary" sx={{ letterSpacing: '0.08em', textTransform: 'uppercase' }}>
                      {label}
                    </Typography>
                    <Typography variant="metadata" >
                      {value}
                    </Typography>
                  </Box>
                ))}
              </Paper>
              <Typography variant="caption" color="text.disabled" sx={{ mt: 2, display: 'block', textAlign: 'center' }}>
                Profile editing coming soon.
              </Typography>
            </TabPanel>
          )}

          {/* ── ADDRESSES TAB ────────────────────────────────────────────── */}
          {tab === 1 && (
            <TabPanel key="addresses" id="panel-addresses">
              <AddressesTab
                qc={qc}
                addrDialog={addrDialog}       setAddrDialog={setAddrDialog}
                editAddr={editAddr}           setEditAddr={setEditAddr}
                addrForm={addrForm}           setAddrForm={setAddrForm}
                addrError={addrError}         setAddrError={setAddrError}
                deleteConfirm={deleteConfirm} setDeleteConfirm={setDeleteConfirm}
              />
            </TabPanel>
          )}

          {/* ── ORDERS TAB ───────────────────────────────────────────────── */}
          {tab === 2 && (
            <TabPanel key="orders" id="panel-orders">
              <OrdersTab />
            </TabPanel>
          )}

          {/* ── SECURITY TAB ─────────────────────────────────────────────── */}
          {tab === 3 && (
            <TabPanel key="security" id="panel-security">
              <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 0, p: { xs: 3, md: 4 } }}>
                <Typography variant="metadata" sx={{ mb: 0.5 }}>Change Password</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                  You'll receive a secure reset link at your registered email address.
                  Your current session will remain active until you log in with the new password.
                </Typography>
                <Button
                  variant="outlined"
                  sx={{ borderRadius: 0, py: 1.25, px: 3 }}
                  onClick={() => navigate('/auth/forgot-password')}
                >
                  Send Password Reset Link
                </Button>
              </Paper>

              {/* DEV-only test panel (preserved from old AccountPage) */}
              {import.meta.env.DEV && <DevTestPanel />}
            </TabPanel>
          )}

        </AnimatePresence>
      </Box>
    </PageWrapper>
  );
};

// ══════════════════════════════════════════════════════════════════════════════
// Addresses sub-component
// ══════════════════════════════════════════════════════════════════════════════
interface AddrTabProps {
  qc: ReturnType<typeof useQueryClient>;
  addrDialog: boolean;       setAddrDialog: (v: boolean) => void;
  editAddr: UserAddress | null; setEditAddr: (v: UserAddress | null) => void;
  addrForm: AddressRequest;  setAddrForm: (v: AddressRequest) => void;
  addrError: string | null;  setAddrError: (v: string | null) => void;
  deleteConfirm: UserAddress | null; setDeleteConfirm: (v: UserAddress | null) => void;
}


const AddressesTab = ({
  qc, addrDialog, setAddrDialog, editAddr, setEditAddr,
  addrForm, setAddrForm, addrError, setAddrError,
  deleteConfirm, setDeleteConfirm,
}: AddrTabProps) => {

  const { data: addresses = [], isLoading } = useQuery({
    queryKey: addrQueryKey,
    queryFn: () => listAddresses().then(r => r.data.data!),
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: addrQueryKey });

  const saveMut = useMutation({
    mutationFn: (req: AddressRequest) =>
      editAddr ? updateAddress(editAddr.id, req) : addAddress(req),
    onSuccess: () => { invalidate(); closeDialog(); },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setAddrError(msg ?? 'Failed to save address. Please try again.');
    },
  });

  const deleteMut = useMutation({
    mutationFn: (id: number) => deleteAddress(id),
    onSuccess: () => { invalidate(); setDeleteConfirm(null); },
  });

  const defaultMut = useMutation({
    mutationFn: (id: number) => setDefaultAddress(id),
    onSuccess: () => invalidate(),
  });

  const openAdd = () => {
    setEditAddr(null);
    setAddrForm(emptyForm());
    setAddrError(null);
    setAddrDialog(true);
  };

  const openEdit = (a: UserAddress) => {
    setEditAddr(a);
    setAddrForm({
      fullName: a.fullName, phone: a.phone,
      addressLine1: a.addressLine1, addressLine2: a.addressLine2 ?? '',
      landmark: a.landmark ?? '', city: a.city, state: a.state,
      pinCode: a.pinCode, country: a.country,
      addressType: a.addressType, setAsDefault: a.isDefault,
    });
    setAddrError(null);
    setAddrDialog(true);
  };

  const closeDialog = () => { setAddrDialog(false); setEditAddr(null); setAddrError(null); };

  const handleSave = () => { saveMut.mutate(addrForm); };

  const atLimit = addresses.length >= 5;

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="body2" color="text.secondary">
          {addresses.length}/5 addresses
        </Typography>
        <Tooltip title={atLimit ? 'Remove an address before adding a new one' : ''}>
          <span>
            <Button
              id="add-address-btn"
              variant="contained"
              startIcon={<AddIcon />}
              disabled={atLimit}
              onClick={openAdd}
              sx={{ borderRadius: 0 }}
            >
              Add New Address
            </Button>
          </span>
        </Tooltip>
      </Box>

      {isLoading && <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}><CircularProgress size={28} /></Box>}

      {!isLoading && addresses.length === 0 && (
        <Paper elevation={0} sx={{ border: '1px dashed', borderColor: 'divider', borderRadius: 0, p: 5, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            You have no saved addresses yet.
          </Typography>
          <Button variant="outlined" onClick={openAdd} sx={{ borderRadius: 0 }}>
            Add Your First Address
          </Button>
        </Paper>
      )}

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {addresses.map(addr => (
          <motion.div key={addr.id} layout initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
            <Paper
              elevation={0}
              sx={{
                border: '1px solid',
                borderColor: addr.isDefault ? 'text.primary' : 'divider',
                borderRadius: 0,
                p: { xs: 2.5, md: 3 },
                position: 'relative',
              }}
            >
              {/* Badges */}
              <Box sx={{ display: 'flex', gap: 1, mb: 1.5, flexWrap: 'wrap' }}>
                <Chip label={addr.addressType} size="small" sx={{ fontSize: '0.65rem', fontWeight: 700, letterSpacing: '0.08em', borderRadius: 0, height: 20 }} />
                {addr.isDefault && (
                  <Chip
                    icon={<StarIcon />}
                    label="DEFAULT"
                    size="small"
                    sx={{ fontSize: '0.65rem', fontWeight: 700, letterSpacing: '0.08em', borderRadius: 0, height: 20, bgcolor: 'text.primary', color: 'background.paper' }}
                  />
                )}
              </Box>

              {/* Address content */}
              <Typography variant="metadata" sx={{ mb: 0.25 }}>{addr.fullName}</Typography>
              <Typography variant="body2" color="text.secondary">
                {addr.addressLine1}
                {addr.addressLine2 ? `, ${addr.addressLine2}` : ''}
                {addr.landmark ? ` (near ${addr.landmark})` : ''}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {addr.city}, {addr.state} — {addr.pinCode}
              </Typography>
              <Typography variant="body2" color="text.secondary">{addr.phone}</Typography>

              {/* Actions */}
              <Box sx={{ display: 'flex', gap: 1, mt: 2, flexWrap: 'wrap' }}>
                <Tooltip title="Edit"><IconButton size="small" onClick={() => openEdit(addr)} sx={{ borderRadius: 0 }}><EditIcon /></IconButton></Tooltip>
                <Tooltip title={addr.isDefault ? 'Set another address as default before deleting this one' : 'Delete'}>
                  <span>
                    <IconButton
                      size="small"
                      onClick={() => setDeleteConfirm(addr)}
                      disabled={addr.isDefault}
                      sx={{ borderRadius: 0, color: addr.isDefault ? 'text.disabled' : 'error.main' }}
                    >
                      <DeleteIcon />
                    </IconButton>
                  </span>
                </Tooltip>
                {!addr.isDefault && (
                  <Button
                    size="small"
                    variant="text"
                    onClick={() => defaultMut.mutate(addr.id)}
                    disabled={defaultMut.isPending}
                    sx={{ borderRadius: 0, fontSize: '0.72rem', textTransform: 'uppercase', letterSpacing: '0.06em' }}
                  >
                    Set as Default
                  </Button>
                )}
              </Box>
            </Paper>
          </motion.div>
        ))}
      </Box>

      {/* ── Add/Edit Dialog ──────────────────────────────────────────────── */}
      <AddressDialog
        open={addrDialog}
        onClose={closeDialog}
        onSave={handleSave}
        editAddr={editAddr}
        addrForm={addrForm}
        setAddrForm={setAddrForm}
        addrError={addrError}
        isSaving={saveMut.isPending}
      />

      {/* ── Delete Confirm Dialog ────────────────────────────────────────── */}
      <Dialog open={!!deleteConfirm} onClose={() => setDeleteConfirm(null)} slotProps={{ paper: { sx: { borderRadius: 0 } } }}>
        <DialogTitle sx={{ fontWeight: 700 }}>Delete Address?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            Remove <strong>{deleteConfirm?.addressLine1}, {deleteConfirm?.city}</strong>?
            This won't affect any existing orders.
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setDeleteConfirm(null)} sx={{ borderRadius: 0 }}>Cancel</Button>
          <Button
            variant="contained"
            color="error"
            onClick={() => deleteConfirm && deleteMut.mutate(deleteConfirm.id)}
            disabled={deleteMut.isPending}
            sx={{ borderRadius: 0 }}
          >
            {deleteMut.isPending ? <CircularProgress size={18} color="inherit" /> : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

// ══════════════════════════════════════════════════════════════════════════════
// Orders sub-component
// ══════════════════════════════════════════════════════════════════════════════
const OrdersTab = () => {
  const navigate = useNavigate();
  const { data, isLoading } = useOrders(0, 5); // Fetch first page with size 5

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="body2" color="text.secondary">Recent Orders</Typography>
        <Button variant="outlined" size="small" onClick={() => navigate('/orders')} sx={{ borderRadius: 0 }}>
          View All Orders
        </Button>
      </Box>

      {isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}><CircularProgress size={28} /></Box>
      ) : data?.content.length === 0 ? (
        <Paper elevation={0} sx={{ border: '1px dashed', borderColor: 'divider', borderRadius: 0, p: 5, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            You haven't placed any orders yet.
          </Typography>
          <Button variant="contained" onClick={() => navigate('/products')} sx={{ borderRadius: 0 }}>
            Start Shopping
          </Button>
        </Paper>
      ) : (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {data?.content.map(order => (
             <Paper
               key={order.id}
               elevation={0}
               sx={{
                 border: (theme) => `1px solid ${theme.palette.border.default}`, borderRadius: 0, p: { xs: 2.5, md: 3 },
                 display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2,
                 cursor: 'pointer', transition: 'all 0.2s', '&:hover': { bgcolor: 'surface.tertiary' }
               }}
               onClick={() => navigate(`/orders/${order.id}`)}
             >
               <Box>
                 <Typography variant="metadata" sx={{ mb: 0.25 }}>Order #{order.id}</Typography>
                 <Typography variant="body2" color="text.secondary">
                   {new Date(order.createdAt).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })} • {order.itemCount} items
                 </Typography>
               </Box>
               <Box sx={{ textAlign: 'right' }}>
                 <Typography variant="metadata" sx={{ mb: 0.5 }}>₹{order.grandTotal.toLocaleString('en-IN')}</Typography>
                 <Chip 
                   label={order.status.replace(/_/g, ' ')} 
                   size="small" 
                   sx={{ 
                     borderRadius: 0, 
                     height: 20, 
                     fontSize: '0.65rem', 
                     fontWeight: 700, 
                     textTransform: 'uppercase',
                     bgcolor: (theme) => theme.palette.statusColors?.[order.status] ?? theme.palette.statusColors?.INACTIVE,
                     color: 'text.primary'
                   }} 
                 />
               </Box>
             </Paper>
          ))}
        </Box>
      )}
    </Box>
  );
};

// ── Tab panel wrapper ──────────────────────────────────────────────────────────
const TabPanel = ({ children, id }: { children: React.ReactNode; id: string }) => (
  <motion.div
    key={id}
    id={id}
    role="tabpanel"
    initial={{ opacity: 0, y: 10 }}
    animate={{ opacity: 1, y: 0 }}
    exit={{ opacity: 0, y: -10 }}
    transition={{ duration: 0.22 }}
  >
    {children}
  </motion.div>
);

// ── DEV test panel (preserved from old AccountPage) ───────────────────────────
import apiClient from '@/api/client';
import { authKeys } from '@/hooks/queryKeys';
const DevTestPanel = () => {
  const qc = useQueryClient();
  const [status, setStatus] = useState<'idle' | 'loading' | 'ok' | 'error'>('idle');
  const [msg, setMsg]       = useState('');
  const test = async () => {
    setStatus('loading'); setMsg('');
    try {
      await qc.invalidateQueries({ queryKey: authKeys.me() });
      const res = await apiClient.get('/auth/me');
      setStatus('ok');
      setMsg(`✅ GET /auth/me — user: ${(res.data.data as { firstName: string }).firstName}`);
    } catch { setStatus('error'); setMsg('❌ Failed — check Console.'); }
  };
  return (
    <Paper elevation={0} sx={{ border: '2px dashed', borderColor: 'warning.main', borderRadius: 0, p: 3, mt: 4 }}>
      <Typography variant="metadata" sx={{ color: 'warning.main', display: 'block', mb: 1.5 }}>
        DEV — SILENT REFRESH TEST
      </Typography>
      <Button variant="outlined" size="small" onClick={test} disabled={status === 'loading'} sx={{ borderRadius: 0, mb: msg ? 2 : 0 }}>
        {status === 'loading' ? 'Calling...' : 'Test Silent Refresh'}
      </Button>
      {msg && <Alert severity={status === 'ok' ? 'success' : 'error'} sx={{ borderRadius: 0, fontSize: '0.78rem', mt: 1 }}>{msg}</Alert>}
    </Paper>
  );
};

export default AccountPage;
