import React from 'react';
import { Box, Typography } from '@mui/material';
import { useLocation } from 'react-router-dom';

const TICKER_ITEMS = [
  'NEW SEASON ARCHIVE',
  'FREE SHIPPING OVER ₹1500',
  'EDITORIAL DROPS',
  'PREMIUM STREETWEAR',
  'LIMITED STOCK',
  'EGO ARCHIVE',
];

const AnnouncementBar: React.FC = () => {
  const location = useLocation();

  // Only show the ticker on the homepage
  if (location.pathname !== '/') {
    return null;
  }

  return (
    <Box
      sx={{
        overflow: 'hidden',
        bgcolor: 'text.primary',
        color: 'background.default',
        py: 0.6,
        borderBottom: '1px solid',
        borderColor: 'divider',
        position: 'relative',
      }}
    >
      <Box
        sx={{
          display: 'flex',
          gap: 0,
          animation: 'ticker 28s linear infinite',
          width: 'max-content',
          '@keyframes ticker': {
            '0%':   { transform: 'translateX(0)' },
            '100%': { transform: 'translateX(-50%)' },
          },
          '&:hover': { animationPlayState: 'paused' },
        }}
      >
        {[...TICKER_ITEMS, ...TICKER_ITEMS, ...TICKER_ITEMS, ...TICKER_ITEMS].map((item, i) => (
          <Box key={i} sx={{ display: 'flex', alignItems: 'center', gap: 0 }}>
            <Typography
              variant="button"
              sx={{
                fontFamily: (theme) => theme.typography.fontFamilyUtility,
                fontWeight: 600,
                fontSize: '0.7rem',
                letterSpacing: '0.18em',
                whiteSpace: 'nowrap',
                px: 3,
                color: 'background.default',
              }}
            >
              {item}
            </Typography>
            <Box
              sx={{
                width: 4,
                height: 4,
                borderRadius: '50%',
                bgcolor: 'text.secondary',
                flexShrink: 0,
              }}
            />
          </Box>
        ))}
      </Box>
    </Box>
  );
};

export default AnnouncementBar;
