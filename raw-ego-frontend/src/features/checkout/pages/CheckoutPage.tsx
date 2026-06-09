/**
 * CheckoutPage.tsx
 *
 * Distraction-free checkout page rendered under CheckoutLayout.
 * Route: /checkout  (protected — requires authentication)
 *
 * Panels:
 *  LEFT  — Order summary (cart items, subtotal, coupon, grand total)
 *  RIGHT — Shipping address input + coupon code field + Pay Now CTA
 *
 * Payment flow is fully delegated to usePayment() — this component
 * only handles form state and validation.
 *
 * Security guarantees:
 *  - Amount is NEVER calculated on the frontend (taken from cart.subtotal)
 *  - Coupon discount preview is informational only; enforcement is server-side
 *  - keyId comes from backend PaymentOrderResponse; never hardcoded
 */

import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Container, Grid, Typography, TextField, Button,
  CircularProgress, Alert, InputAdornment, IconButton,
  Chip, Skeleton,
} from '@mui/material';
import LocalOfferIcon from '@mui/icons-material/LocalOffer';
import CloseIcon from '@mui/icons-material/Close';
import LockIcon from '@mui/icons-material/Lock';
import ShoppingBagIcon from '@mui/icons-material/ShoppingBag';
import MailOutlineIcon from '@mui/icons-material/MailOutlined';
import { useCart } from '@/features/cart/hooks/useCart';
import { usePayment } from '@/features/orders/hooks/usePayment';
import { validateCoupon, type CouponValidateResponse } from '@/api/coupon.api';
import { useAuthStore } from '@/store/authStore';
import { resendVerification } from '@/api/auth.api';
import { toast } from '@/store/uiStore';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  listAddresses, addAddress,
  type AddressRequest,
} from '@/api/address.api';
import AddressDialog from '@/components/ui/AddressDialog';

// ── Coupon state ──────────────────────────────────────────────────────────────

type CouponState = 'idle' | 'validating' | 'valid' | 'invalid';

// ── Component ─────────────────────────────────────────────────────────────────

const CheckoutPage: React.FC = () => {
  const navigate = useNavigate();
  const { data: cart, isLoading: cartLoading } = useCart();
  const { user } = useAuthStore();
  const [isResending, setIsResending] = useState(false);
  const [sent, setSent] = useState(false);

  const {
    placeOrderAndPay,
    paymentState,
    isPaying,
    isGatewayReady,
    isGatewayFailed,
    error: paymentError,
    reset: resetPayment,
  } = usePayment();

  // ── Form state ──────────────────────────────────────────────────────────────
  const qc = useQueryClient();
  const { data: addresses = [], isLoading: addressesLoading, error: addressesError } = useQuery({
    queryKey: ['addresses'],
    queryFn: () => listAddresses().then(r => r.data.data!),
  });

  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);

  // Address dialog state
  const [addrDialog, setAddrDialog] = useState(false);
  const [addrForm, setAddrForm] = useState<AddressRequest>({
    fullName: '', phone: '', addressLine1: '', addressLine2: '',
    landmark: '', city: '', state: '', pinCode: '',
    country: 'India', addressType: 'HOME', setAsDefault: false,
  });
  const [addrError, setAddrError] = useState<string | null>(null);

  const saveMut = useMutation({
    mutationFn: (req: AddressRequest) => addAddress(req),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['addresses'] });
      setSelectedAddressId(res.data.data!.id);
      setAddrDialog(false);
      setAddrError(null);
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setAddrError(msg ?? 'Failed to save address. Please try again.');
    },
  });

  // Auto-select default address
  useEffect(() => {
    if (addresses.length > 0 && selectedAddressId === null) {
      const def = addresses.find(a => a.isDefault) || addresses[0];
      setSelectedAddressId(def.id);
    }
  }, [addresses, selectedAddressId]);
  const [couponInput, setCouponInput] = useState('');
  const [appliedCoupon, setAppliedCoupon] = useState('');
  const [couponState, setCouponState] = useState<CouponState>('idle');
  const [couponResult, setCouponResult] = useState<CouponValidateResponse | null>(null);
  const [couponError, setCouponError] = useState('');
  const validateTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Redirect if cart is empty
  useEffect(() => {
    if (!cartLoading && cart && cart.items.length === 0) {
      navigate('/products');
    }
  }, [cart, cartLoading, navigate]);

  // ── Coupon validation ───────────────────────────────────────────────────────

  const handleValidateCoupon = async () => {
    const code = couponInput.trim().toUpperCase();
    if (!code) return;
    if (!cart?.subtotal) return;

    setCouponState('validating');
    setCouponError('');

    try {
      const result = await validateCoupon(code, cart.subtotal);
      if (result.valid) {
        setCouponResult(result);
        setAppliedCoupon(code);
        setCouponState('valid');
        setCouponInput('');
      } else {
        setCouponState('invalid');
        setCouponError('Coupon is not valid or has expired.');
      }
    } catch {
      setCouponState('invalid');
      setCouponError('Could not validate coupon. Please try again.');
    }
  };

  const handleRemoveCoupon = () => {
    setAppliedCoupon('');
    setCouponResult(null);
    setCouponState('idle');
    setCouponError('');
  };

  const handleCouponKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleValidateCoupon();
    }
  };

  // ── Computed totals ─────────────────────────────────────────────────────────

  const subtotal = cart?.subtotal ?? 0;
  const discountAmount = couponResult?.discountAmount ?? 0;
  const afterDiscount = Math.max(subtotal - discountAmount, 0);
  const shippingTotal = 0; // Free shipping — backend computes the real value
  const estimatedTotal = afterDiscount + shippingTotal;

  // ── Pay Now handler ─────────────────────────────────────────────────────────

  const handlePayNow = async () => {
    if (!selectedAddressId) return;
    resetPayment(); // Clear any previous error state
    await placeOrderAndPay({
      addressId: selectedAddressId,
      ...(appliedCoupon ? { couponCode: appliedCoupon } : {}),
    });
  };

  const isFormValid = selectedAddressId !== null;
  const isPayDisabled =
    isPaying ||
    !isGatewayReady ||
    !isFormValid ||
    cartLoading ||
    !cart ||
    cart.items.length === 0;

  // ── Render ──────────────────────────────────────────────────────────────────

  if (user && !user.emailVerified) {
    const handleResend = async () => {
      try {
        setIsResending(true);
        await resendVerification();
        setSent(true);
        toast.success('Verification email sent! Please check your inbox.');
      } catch (error) {
        setSent(false);
      } finally {
        setIsResending(false);
      }
    };

    return (
      <Container maxWidth="sm" sx={{ py: { xs: 6, md: 12 }, bgcolor: 'background.default', minHeight: '80vh' }}>
        <Box
          sx={{
            p: 5,
            textAlign: 'center',
            border: (theme) => `1px solid ${theme.palette.border.default}`,
            bgcolor: 'surface.secondary',
          }}
        >
          <MailOutlineIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 3 }} />
          <Typography variant="sectionTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', mb: 2, }}>
            Verify Your Email
          </Typography>
          <Typography
            sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mb: 5 }}
          >
            You cannot proceed to checkout until your email address{' '}
            <Box component="span" sx={{ color: 'text.primary', fontStyle: 'italic' }}>{user.email}</Box>{' '}
            is verified.
          </Typography>
          <Button
            variant="contained"
            size="large"
            onClick={handleResend}
            disabled={isResending || sent}
            disableElevation
            sx={{ px: 5, py: 1.75 }}
          >
            {isResending ? <CircularProgress size={22} color="inherit" /> : sent ? 'Verification Email Sent' : 'Send Verification Email'}
          </Button>
        </Box>
      </Container>
    );
  }

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '100vh', pb: 10 }}>
      <Container maxWidth="lg" sx={{ py: { xs: 5, md: 8 } }}>

        {/* Page header */}
        <Box
          sx={{
            pb: 5,
            mb: 7,
            borderBottom: (theme) => `1px solid ${theme.palette.border.default}`,
          }}
        >
          <Typography
            variant="overline"
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              fontWeight: 700,
              fontSize: '0.65rem',
              letterSpacing: '0.25em',
              color: 'text.secondary',
              display: 'block',
              mb: 2,
            }}
          >
            SECURE CHECKOUT
          </Typography>
          <Typography
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyDisplay,
              fontWeight: 700,
              fontSize: { xs: '2rem', md: '3rem' },
              color: 'text.primary',
              lineHeight: 1,
              textTransform: 'uppercase',
            }}
          >
            Complete Your Order
          </Typography>
        </Box>

        <Grid container spacing={5}>
          {/* ── RIGHT: Shipping + Payment ────────────────────────── */}
          <Grid size={{ xs: 12, md: 7 }} sx={{ order: { xs: 2, md: 1 } }}>

            {/* Address section */}
            <Box
              sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', p: 4, mb: 3 }}
            >
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
                <Typography
                  sx={{
                    fontFamily: (theme) => theme.typography.fontFamilyUtility,
                    fontWeight: 700,
                    fontSize: '0.65rem',
                    letterSpacing: '0.25em',
                    color: 'text.secondary',
                    textTransform: 'uppercase',
                  }}
                >
                  Delivery Address
                </Typography>
                {addresses.length > 0 && addresses.length < 5 && (
                  <Button
                    size="small"
                    onClick={() => {
                      setAddrForm({
                        fullName: '', phone: '', addressLine1: '', addressLine2: '',
                        landmark: '', city: '', state: '', pinCode: '',
                        country: 'India', addressType: 'HOME', setAsDefault: false,
                      });
                      setAddrDialog(true);
                    }}
                    sx={{
                      borderRadius: 0,
                      textTransform: 'uppercase',
                      letterSpacing: '0.1em',
                      fontFamily: (theme) => theme.typography.fontFamilyUtility,
                      fontWeight: 700,
                      fontSize: '0.65rem',
                      color: 'text.secondary',
                      '&:hover': { color: 'text.primary', background: 'none' },
                    }}
                  >
                    + Add New
                  </Button>
                )}
              </Box>

              {addressesLoading ? (
                <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}><CircularProgress size={28} sx={{ color: 'text.secondary' }} /></Box>
              ) : addressesError ? (
                <Alert severity="error" sx={{ borderRadius: 0 }}>Failed to load addresses. Please refresh.</Alert>
              ) : addresses.length === 0 ? (
                <Box sx={{ textAlign: 'center', py: 6, border: (theme) => `1px dashed ${theme.palette.border.default}` }}>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mb: 3 }}>
                    You have no saved delivery addresses.
                  </Typography>
                  <Button
                    variant="contained"
                    onClick={() => {
                      setAddrForm({
                        fullName: '', phone: '', addressLine1: '', addressLine2: '',
                        landmark: '', city: '', state: '', pinCode: '',
                        country: 'India', addressType: 'HOME', setAsDefault: false,
                      });
                      setAddrDialog(true);
                    }}
                    sx={{ px: 4, py: 1.5 }}
                  >
                    Add Delivery Address
                  </Button>
                </Box>
              ) : (
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  {addresses.map(addr => (
                    <Box
                      key={addr.id}
                      onClick={() => !isPaying && setSelectedAddressId(addr.id)}
                      sx={{
                        border: '1px solid',
                        borderColor: selectedAddressId === addr.id ? 'border.strong' : 'border.default',
                        bgcolor: selectedAddressId === addr.id ? (theme) => `${theme.palette.text.primary}0A` : 'transparent',
                        p: { xs: 2.5, md: 3 },
                        cursor: isPaying ? 'default' : 'pointer',
                        transition: 'border-color 0.2s',
                      }}
                    >
                      {/* Badges */}
                      <Box sx={{ display: 'flex', gap: 1, mb: 1.5, flexWrap: 'wrap', alignItems: 'center' }}>
                        <input
                          type="radio"
                          checked={selectedAddressId === addr.id}
                          readOnly
                          style={{ accentColor: 'inherit', cursor: 'pointer', margin: 0, marginRight: '4px' }}
                        />
                        <Chip
                          label={addr.addressType}
                          size="small"
                          sx={{
                            fontSize: '0.6rem',
                            fontWeight: 700,
                            letterSpacing: '0.08em',
                            borderRadius: 0,
                            height: 18,
                            bgcolor: 'surface.tertiary',
                            color: 'text.secondary',
                          }}
                        />
                        {addr.isDefault && (
                          <Chip
                            label="DEFAULT"
                            size="small"
                            sx={{
                              fontSize: '0.6rem',
                              fontWeight: 700,
                              letterSpacing: '0.08em',
                              borderRadius: 0,
                              height: 18,
                              bgcolor: 'brand.primary',
                              color: 'background.default',
                            }}
                          />
                        )}
                      </Box>
                      <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.primary', mb: 0.5, }}>
                        {addr.fullName}
                      </Typography>
                      <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', fontSize: '0.85rem', lineHeight: 1.6 }}>
                        {addr.addressLine1}
                        {addr.addressLine2 ? `, ${addr.addressLine2}` : ''}
                        {addr.landmark ? ` (near ${addr.landmark})` : ''}
                      </Typography>
                      <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', fontSize: '0.85rem' }}>
                        {addr.city}, {addr.state} — {addr.pinCode}
                      </Typography>
                      <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', fontSize: '0.85rem', mt: 0.5 }}>
                        {addr.phone}
                      </Typography>
                    </Box>
                  ))}
                </Box>
              )}
            </Box>

            {/* Coupon section */}
            <Box sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', p: 4, mb: 3 }}>
              <Typography
                sx={{
                  fontFamily: (theme) => theme.typography.fontFamilyUtility,
                  fontWeight: 700,
                  fontSize: '0.65rem',
                  letterSpacing: '0.25em',
                  color: 'text.secondary',
                  textTransform: 'uppercase',
                  mb: 3,
                  display: 'block',
                }}
              >
                Coupon Code
              </Typography>

              {couponState === 'valid' && couponResult ? (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Chip
                    icon={<LocalOfferIcon sx={{ fontSize: 16 }} />}
                    label={`${couponResult.code} — ${couponResult.description}`}
                    color="success"
                    variant="outlined"
                    sx={{ fontWeight: 600, fontSize: '0.8rem', borderRadius: 0 }}
                  />
                  <IconButton size="small" onClick={handleRemoveCoupon} disabled={isPaying}>
                    <CloseIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </IconButton>
                </Box>
              ) : (
                <Box sx={{ display: 'flex', gap: 2 }}>
                  <TextField
                    size="small"
                    placeholder="Enter coupon code"
                    value={couponInput}
                    onChange={(e) => {
                      setCouponInput(e.target.value.toUpperCase());
                      setCouponState('idle');
                      setCouponError('');
                      if (validateTimerRef.current) clearTimeout(validateTimerRef.current);
                    }}
                    onKeyDown={handleCouponKeyDown}
                    disabled={isPaying || couponState === 'validating'}
                    error={couponState === 'invalid'}
                    helperText={couponError}
                    sx={{
                      flex: 1,
                      '& .MuiOutlinedInput-root': {
                        borderRadius: 0,
                        bgcolor: 'background.default',
                        '& fieldset': { borderColor: 'border.default' },
                        '&:hover fieldset': { borderColor: 'border.strong' },
                        '&.Mui-focused fieldset': { borderColor: 'text.primary' },
                      },
                      '& input': { color: 'text.primary', fontFamily: (theme) => theme.typography.fontFamilyUtility, letterSpacing: '0.1em' },
                    }}
                    slotProps={{
                      input: {
                        startAdornment: (
                          <InputAdornment position="start">
                            <LocalOfferIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
                          </InputAdornment>
                        ),
                      },
                    }}
                  />
                  <Button
                    variant="outlined"
                    onClick={handleValidateCoupon}
                    disabled={!couponInput.trim() || isPaying || couponState === 'validating'}
                    sx={{
                      borderRadius: 0,
                      px: 3,
                      whiteSpace: 'nowrap',
                      borderColor: 'border.default',
                      color: 'text.secondary',
                      fontFamily: (theme) => theme.typography.fontFamilyUtility,
                      fontWeight: 700,
                      fontSize: '0.72rem',
                      letterSpacing: '0.1em',
                      '&:hover': { borderColor: 'text.primary', color: 'text.primary', bgcolor: 'transparent' },
                    }}
                  >
                    {couponState === 'validating' ? <CircularProgress size={14} sx={{ color: 'text.secondary' }} /> : 'Apply'}
                  </Button>
                </Box>
              )}
            </Box>

            {/* Gateway error */}
            {isGatewayFailed && (
              <Alert severity="error" sx={{ mb: 3, borderRadius: 0 }}>
                Payment gateway failed to load. Please refresh the page.
              </Alert>
            )}

            {paymentError && (
              <Alert severity="error" sx={{ mb: 3, borderRadius: 0 }}>
                {paymentError}
              </Alert>
            )}

            {/* Pay Now button */}
            <Box sx={{ position: 'relative' }}>
              <Button
                fullWidth
                variant="contained"
                size="large"
                onClick={handlePayNow}
                disabled={isPayDisabled}
                startIcon={
                  isPaying ? <CircularProgress size={18} color="inherit" /> : <LockIcon />
                }
                sx={{ py: 2.25 }}
              >
                {paymentState === 'placing_order'
                  ? 'Placing order…'
                  : paymentState === 'creating_payment'
                    ? 'Opening payment…'
                    : paymentState === 'modal_open'
                      ? 'Complete payment in modal…'
                      : !isGatewayReady
                        ? 'Loading payment gateway…'
                        : `Pay ₹${estimatedTotal.toLocaleString('en-IN')}`}
              </Button>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 1, mt: 2 }}>
                <LockIcon sx={{ fontSize: 12, color: 'text.secondary' }} />
                <Typography
                  sx={{
                    fontFamily: (theme) => theme.typography.fontFamilyUtility,
                    fontSize: '0.65rem',
                    letterSpacing: '0.1em',
                    color: 'text.secondary',
                  }}
                >
                  Secured by Razorpay
                </Typography>
              </Box>
            </Box>
          </Grid>

          {/* ── LEFT: Order Summary ──────────────────────────── */}
          <Grid size={{ xs: 12, md: 5 }} sx={{ order: { xs: 1, md: 2 } }}>
            <Box
              sx={{
                border: (theme) => `1px solid ${theme.palette.border.default}`,
                bgcolor: 'surface.secondary',
                p: { xs: 3, md: 4 },
                position: 'sticky',
                top: 100,
              }}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 4 }}>
                <ShoppingBagIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
                <Typography
                  sx={{
                    fontFamily: (theme) => theme.typography.fontFamilyUtility,
                    fontWeight: 700,
                    fontSize: '0.65rem',
                    letterSpacing: '0.25em',
                    color: 'text.secondary',
                    textTransform: 'uppercase',
                  }}
                >
                  Order Summary
                </Typography>
              </Box>

              {cartLoading ? (
                <>
                  <Skeleton height={52} sx={{ mb: 1, bgcolor: 'surface.tertiary' }} />
                  <Skeleton height={52} sx={{ mb: 1, bgcolor: 'surface.tertiary' }} />
                  <Skeleton height={52} sx={{ bgcolor: 'surface.tertiary' }} />
                </>
              ) : (
                <>
                  {cart?.items.map((item) => (
                    <Box
                      key={item.variantId}
                      sx={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'flex-start',
                        py: 2,
                        borderBottom: (theme) => `1px solid ${theme.palette.border.default}`,
                      }}
                    >
                      <Box sx={{ flex: 1, mr: 2 }}>
                        <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.primary', }}>
                          {item.productName}
                        </Typography>
                        {item.variantLabel && (
                          <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.72rem', letterSpacing: '0.05em' }}>
                            {item.variantLabel}
                          </Typography>
                        )}
                        <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.72rem', letterSpacing: '0.05em', display: 'block' }}>
                          Qty: {item.quantity}
                        </Typography>
                      </Box>
                      <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.primary', whiteSpace: 'nowrap', }}>
                        ₹{(item.price * item.quantity).toLocaleString('en-IN')}
                      </Typography>
                    </Box>
                  ))}

                  <Box sx={{ borderTop: (theme) => `1px solid ${theme.palette.border.default}`, mt: 1, pt: 3 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1.5 }}>
                      <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary' }}>Subtotal</Typography>
                      <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 600, color: 'text.primary' }}>₹{subtotal.toLocaleString('en-IN')}</Typography>
                    </Box>

                    {couponResult && discountAmount > 0 && (
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1.5 }}>
                        <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'success.main', }}>
                          Coupon ({couponResult.code})
                        </Typography>
                        <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'success.main', }}>
                          −₹{discountAmount.toLocaleString('en-IN')}
                        </Typography>
                      </Box>
                    )}

                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
                      <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary' }}>Shipping</Typography>
                      <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, color: 'success.main', fontSize: '0.85rem', letterSpacing: '0.05em' }}>
                        Free
                      </Typography>
                    </Box>

                    <Box sx={{ borderTop: (theme) => `1px solid ${theme.palette.border.default}`, pt: 3, display: 'flex', justifyContent: 'space-between' }}>
                      <Typography
                        sx={{
                          fontFamily: (theme) => theme.typography.fontFamilyUtility,
                          fontWeight: 700,
                          fontSize: '0.65rem',
                          letterSpacing: '0.25em',
                          color: 'text.secondary',
                          textTransform: 'uppercase',
                        }}
                      >
                        Total
                      </Typography>
                      <Typography variant="sectionTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', }}>
                        ₹{estimatedTotal.toLocaleString('en-IN')}
                      </Typography>
                    </Box>

                    <Typography
                      sx={{
                        fontFamily: (theme) => theme.typography.fontFamilyUtility,
                        fontSize: '0.65rem',
                        letterSpacing: '0.05em',
                        color: 'text.secondary',
                        display: 'block',
                        mt: 1,
                        textAlign: 'right',
                      }}
                    >
                      Inclusive of all taxes
                    </Typography>
                  </Box>
                </>
              )}
            </Box>
          </Grid>
        </Grid>

        <AddressDialog
          open={addrDialog}
          onClose={() => setAddrDialog(false)}
          onSave={() => saveMut.mutate(addrForm)}
          editAddr={null}
          addrForm={addrForm}
          setAddrForm={setAddrForm}
          addrError={addrError}
          isSaving={saveMut.isPending}
        />
      </Container>
    </Box>
  );
};

export default CheckoutPage;
