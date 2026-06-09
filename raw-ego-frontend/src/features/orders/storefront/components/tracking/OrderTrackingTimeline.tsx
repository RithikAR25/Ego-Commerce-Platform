import React from 'react';
import { Box, Typography, useTheme, useMediaQuery } from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import LensIcon from '@mui/icons-material/Lens';
import type { TrackingStage } from './OrderTrackingUtils';

interface OrderTrackingTimelineProps {
  stages: TrackingStage[];
}

const OrderTrackingTimeline: React.FC<OrderTrackingTimelineProps> = ({ stages }) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

  return (
    <Box sx={{ width: '100%', py: { xs: 2, md: 4 } }}>
      {/* 
        We use flex-direction column on mobile, row on desktop. 
        Semantic list for accessibility.
      */}
      <Box
        component="ol"
        sx={{
          display: 'flex',
          flexDirection: { xs: 'column', sm: 'row' },
          justifyContent: 'space-between',
          alignItems: { xs: 'flex-start', sm: 'center' },
          listStyle: 'none',
          p: 0,
          m: 0,
          position: 'relative',
          gap: { xs: 4, sm: 0 },
        }}
      >
        {stages.map((stage, index) => {
          const isLast = index === stages.length - 1;

          // Determine Icon
          let Icon = RadioButtonUncheckedIcon;
          let iconColor = 'text.disabled';
          if (stage.isCompleted && !stage.isCurrent) {
            Icon = CheckCircleIcon;
            iconColor = 'text.primary'; // Black/Primary color for premium feel
          } else if (stage.isCurrent) {
            Icon = LensIcon; // Solid circle for current
            iconColor = 'text.primary';
          }

          return (
            <Box
              component="li"
              key={stage.id}
              aria-current={stage.isCurrent ? 'step' : undefined}
              sx={{
                display: 'flex',
                flexDirection: { xs: 'row', sm: 'column' },
                alignItems: { xs: 'flex-start', sm: 'center' },
                position: 'relative',
                flex: { sm: 1 },
                width: { xs: '100%', sm: 'auto' },
                zIndex: 1,
              }}
            >
              {/* Connecting Line - Desktop */}
              {!isMobile && !isLast && (
                <Box
                  sx={{
                    position: 'absolute',
                    top: 12, // Half of icon height (24px)
                    left: '50%',
                    width: '100%',
                    height: 2,
                    bgcolor: stage.isCompleted ? 'text.primary' : 'divider',
                    zIndex: -1,
                  }}
                />
              )}

              {/* Connecting Line - Mobile */}
              {isMobile && !isLast && (
                <Box
                  sx={{
                    position: 'absolute',
                    top: 24, // Start below icon
                    left: 11, // Center of 24px icon
                    width: 2,
                    height: 'calc(100% + 16px)', // Stretch to next item
                    bgcolor: stage.isCompleted ? 'text.primary' : 'divider',
                    zIndex: -1,
                  }}
                />
              )}

              {/* Step Icon */}
              <Box
                sx={{
                  bgcolor: 'background.paper',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: iconColor,
                  mr: { xs: 2, sm: 0 },
                  mb: { xs: 0, sm: 2 },
                  '& svg': {
                    fontSize: 24,
                  }
                }}
              >
                <Icon />
              </Box>

              {/* Step Content */}
              <Box sx={{ textAlign: { xs: 'left', sm: 'center' }, mt: { xs: 0.5, sm: 0 } }}>
                <Typography 
                  variant="subtitle2" 
                  sx={{ 
                    fontWeight: stage.isCurrent || stage.isCompleted ? 700 : 500,
                    color: stage.isCurrent || stage.isCompleted ? 'text.primary' : 'text.disabled',
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em',
                  }}
                >
                  {stage.label}
                </Typography>
                {stage.timestamp && (
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                    {new Date(stage.timestamp).toLocaleDateString(undefined, {
                      month: 'short', day: 'numeric'
                    })}
                  </Typography>
                )}
              </Box>
            </Box>
          );
        })}
      </Box>
    </Box>
  );
};

export default OrderTrackingTimeline;
