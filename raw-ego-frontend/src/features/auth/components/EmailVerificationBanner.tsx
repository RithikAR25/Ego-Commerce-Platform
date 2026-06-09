import { useState } from 'react';
import { useAuthStore } from '@/store/authStore';
import { resendVerification } from '@/api/auth.api';
import { Box, Typography, Button, IconButton, CircularProgress, Fade } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { toast } from '@/store/uiStore';

export default function EmailVerificationBanner() {
  const { user, isAuthenticated } = useAuthStore();
  const [isSending, setIsSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [dismissed, setDismissed] = useState(false);

  // Only show if logged in, but email is NOT verified, and user hasn't dismissed it
  if (!isAuthenticated || !user || user.emailVerified || dismissed) {
    return null;
  }

  const handleResend = async () => {
    try {
      setIsSending(true);
      await resendVerification();
      setSent(true);
      toast.success('Verification email sent! Please check your inbox.');
    } catch (error) {
      // client.ts global interceptor handles showing the error toast
      setSent(false);
    } finally {
      setIsSending(false);
    }
  };

  return (
    <Fade in={!dismissed}>
      <Box 
        role="status"
        aria-label="Email Verification Notice"
        sx={{
          width: '100%',
          bgcolor: 'primary.main',
          borderBottom: (theme) => `1px solid ${theme.palette.border.subtle}`,
          px: { xs: 2, md: 4 },
          py: { xs: 2, md: 1.5 },
          display: 'flex',
          flexDirection: { xs: 'column', md: 'row' },
          alignItems: { xs: 'flex-start', md: 'center' },
          justifyContent: 'space-between',
          gap: { xs: 2, md: 3 }
        }}
      >
        <Box sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' }, alignItems: { xs: 'flex-start', md: 'center' }, gap: { xs: 0.5, md: 1.5 }, width: { xs: '100%', md: 'auto' } }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', width: '100%', alignItems: 'center' }}>
            <Typography variant="metadata" sx={{ color: 'primary.contrastText' }}>
              Verify your email to enable checkout.
            </Typography>
            {/* Mobile close button (visible only on xs, hidden on md and up) */}
            <IconButton 
              size="small" 
              onClick={() => setDismissed(true)} 
              sx={{ display: { xs: 'flex', md: 'none' }, color: 'text.disabled', p: 0 }}
              aria-label="Dismiss notification"
            >
              <CloseIcon fontSize="small" />
            </IconButton>
          </Box>
          <Typography variant="metadata" sx={{ color: 'text.disabled' }}>
            {sent ? 'Verification email sent.' : `Please verify your email address: ${user.email}`}
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, width: { xs: '100%', md: 'auto' } }}>
          <Button 
            onClick={handleResend}
            disabled={isSending || sent}
            fullWidth={false}
            disableElevation
            variant="contained"
            sx={{ 
              bgcolor: 'primary.contrastText', color: 'primary.main', borderRadius: 0, textTransform: 'none', px: 3, py: 0.5,
              width: { xs: '100%', md: 'auto' },
              '&:hover': { bgcolor: 'surface.tertiary' },
              '&.Mui-disabled': { bgcolor: 'action.disabledBackground', color: 'text.disabled' }
            }}
          >
            {isSending ? <CircularProgress size={16} color="inherit" /> : sent ? 'Sent' : 'Send Email'}
          </Button>
          
          {/* Desktop close button */}
          <IconButton 
            size="small" 
            onClick={() => setDismissed(true)} 
            sx={{ display: { xs: 'none', md: 'flex' }, color: 'text.disabled' }}
            aria-label="Dismiss notification"
          >
            <CloseIcon fontSize="small" />
          </IconButton>
        </Box>

      </Box>
    </Fade>
  );
}
