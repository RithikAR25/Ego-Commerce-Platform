import React from 'react';
import { Box, Typography } from '@mui/material';
import { motion, AnimatePresence } from 'framer-motion';
import type { ProductVariantResponse } from '@/types/catalog.types';

interface Props {
  variant: ProductVariantResponse | null;
}

const StockUrgencyIndicator: React.FC<Props> = ({ variant }) => {
  if (!variant) return null;

  const { stockUrgencyMessage, quantityAvailable } = variant;

  // Don't render anything if there's no urgency message
  if (!stockUrgencyMessage) return null;

  const isOutOfStock = stockUrgencyMessage === 'Out of stock';
  const isOnlyXLeft = stockUrgencyMessage.includes('Only');
  const isSellingFast = stockUrgencyMessage === 'Selling fast';

  // Determine styling based on message type
  let textColor = 'text.secondary';
  let icon = '';

  if (isOutOfStock) {
    textColor = 'text.secondary';
  } else if (isOnlyXLeft) {
    textColor = 'error.main'; // Stronger emphasis (premium red)
    icon = '🔥 ';
  } else if (isSellingFast) {
    textColor = 'warning.main'; // Softer emphasis (amber)
    icon = '⚡ ';
  }

  // Render a subtle premium inventory meter if qty <= 3
  const showMeter = quantityAvailable !== undefined && quantityAvailable > 0 && quantityAvailable <= 3;
  const TOTAL_METER_BLOCKS = 10;

  return (
    <AnimatePresence mode="wait">
      <Box
        component={motion.div}
        key={variant.id} // Re-animate when variant changes
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.3, ease: 'easeOut' }}
        sx={{ mt: 2, display: 'flex', flexDirection: 'column', gap: 0.5 }}
        aria-live="polite"
        role="status"
      >
        <Typography variant="metadata" sx={{ color: textColor, display: 'flex', alignItems: 'center', }}>
          {icon}
          {stockUrgencyMessage}
        </Typography>

        {showMeter && (
          <Box
            sx={{
              display: 'flex',
              gap: '2px',
              mt: 0.5,
              width: 120, // fixed small width
            }}
            aria-hidden="true" // Hide from screen readers, message is sufficient
          >
            {Array.from({ length: TOTAL_METER_BLOCKS }).map((_, i) => (
              <Box
                key={i}
                sx={{
                  height: 4,
                  flex: 1,
                  bgcolor: i < quantityAvailable ? textColor : 'divider',
                  borderRadius: 0.5,
                  opacity: i < quantityAvailable ? 1 : 0.6,
                }}
              />
            ))}
          </Box>
        )}
      </Box>
    </AnimatePresence>
  );
};

export default StockUrgencyIndicator;
