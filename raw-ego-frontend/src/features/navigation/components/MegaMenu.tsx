/**
 * MegaMenu.tsx
 *
 * Enterprise full-width mega-menu panel for the 3-level category navigation.
 *
 * Layout:
 *   - Full-width panel below the AppBar, frosted glass / white background
 *   - ROOT column header on the left
 *   - GROUP columns evenly distributed (max 7 groups per ROOT, flex-wrap if more)
 *   - Each GROUP column lists LEAF categories as clickable links
 *   - "View All {ROOT}" pill link at end of each GROUP column
 *   - Smooth fade-in on mount via CSS transition
 *
 * Props:
 *   root         — The active ROOT category with its groups+leaves
 *   onMouseEnter — Cancel the close timer when hovering over the panel
 *   onMouseLeave — Schedule close when leaving the panel
 *   onNavigate   — Close the menu immediately when a link is clicked
 */

import React from 'react';
import { Box, Typography, Divider, alpha } from '@mui/material';
import { Link } from 'react-router-dom';
import type { CategoryTreeResponse } from '@/types/catalog.types';

interface MegaMenuProps {
  root:           CategoryTreeResponse;
  onMouseEnter:   () => void;
  onMouseLeave:   () => void;
  onNavigate:     () => void;
}

const MegaMenu: React.FC<MegaMenuProps> = ({ root, onMouseEnter, onMouseLeave, onNavigate }) => {
  const visibleGroups = root.groups.filter((g) => g.visible);

  return (
    <Box
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      sx={{
        position:        'absolute',
        top:             '100%',
        left:            0,
        right:           0,
        zIndex:          -1,
        bgcolor:         'background.paper',
        borderBottom:    '1px solid',
        borderColor:     'divider',
        boxShadow:       (theme) => `0px 10px 30px ${alpha(theme.palette.background.default, 0.05)}`,
        borderBottomLeftRadius: '16px',
        borderBottomRightRadius: '16px',
        animation:       'megaMenuFadeIn 0.2s ease-out',
        '@keyframes megaMenuFadeIn': {
          from: { opacity: 0, transform: 'translateY(-10px)' },
          to:   { opacity: 1, transform: 'translateY(0)' },
        },
        display: { xs: 'none', md: 'block' },   // desktop only
        maxHeight: '70vh',
        overflowY: 'auto',
        mt: 1,
      }}
    >
      <Box
        sx={{
          maxWidth:   1400,
          mx:         'auto',
          px:         4,
          py:         3,
          display:    'flex',
          gap:        4,
          alignItems: 'flex-start',
        }}
      >
        {/* ── ROOT identity column ─────────────────────────────────── */}
        <Box
          sx={{
            minWidth: 120,
            flexShrink: 0,
            display: 'flex',
            flexDirection: 'column',
            gap: 1,
          }}
        >
          <Typography variant="sectionTitle" component={Link} to={`/products?category=${root.slug}`} onClick={onNavigate} sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', textDecoration: 'none', '&:hover': { color: 'primary.main', }, }}>
            {root.name}
          </Typography>

          <Typography
            component={Link}
            to={`/products?category=${root.slug}`}
            onClick={onNavigate}
            sx={{
              fontWeight:     600,
              fontSize:       '0.875rem',
              color:          'primary.main',
              textDecoration: 'none',
              letterSpacing:  '0.02em',
              textTransform:  'uppercase',
              mt:             0.5,
              '&:hover': { color: 'primary.main' },
            }}
          >
            VIEW ALL →
          </Typography>
        </Box>

        <Divider orientation="vertical" flexItem sx={{ mx: 1 }} />

        {/* ── GROUP columns ────────────────────────────────────────── */}
        <Box
          sx={{
            flex:       1,
            display:    'flex',
            flexWrap:   'wrap',
            gap:        '32px 48px',
            alignItems: 'flex-start',
          }}
        >
          {visibleGroups.map((group) => {
            const visibleLeaves = group.leafCategories.filter((l) => l.visible);
            return (
              <Box
                key={group.id}
                sx={{
                  minWidth:     140,
                  display:      'flex',
                  flexDirection:'column',
                  gap:          0.5,
                }}
              >
                {/* GROUP header */}
                <Typography
                  component={Link}
                  to={`/products?category=${group.slug}`}
                  onClick={onNavigate}
                  sx={{
                    fontWeight:     600,
                    fontSize:       '0.875rem',
                    letterSpacing:  '0.05em',
                    textTransform:  'uppercase',
                    color:          'text.primary',
                    textDecoration: 'none',
                    mb:             0.5,
                    pb:             0.5,
                    borderBottom:   '1px solid',
                    borderColor:    'divider',
                    display:        'block',
                    '&:hover': { color: 'primary.main' },
                  }}
                >
                  {group.resolvedLabel}
                </Typography>

                {/* LEAF links */}
                {visibleLeaves.map((leaf) => (
                  <Typography variant="body" key={leaf.id} component={Link} to={`/products?category=${leaf.slug}`} onClick={onNavigate} sx={{ color: 'text.secondary', textDecoration: 'none', py: '4px', position: 'relative', display: 'inline-block', transition: 'all 0.2s ease', '&:hover': { color: 'primary.main', }, }}>
                    {leaf.resolvedLabel}
                  </Typography>
                ))}
              </Box>
            );
          })}
        </Box>
      </Box>
    </Box>
  );
};

export default MegaMenu;
