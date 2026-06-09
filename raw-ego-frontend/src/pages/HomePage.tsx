/**
 * HomePage.tsx
 *
 * Analog Archive Dark — Professional Homepage
 *
 * Sections:
 *  1. Hero — Full-viewport cinematic banner with animated headline
 *  2. Ticker — Scrolling marquee strip
 *  3. New Arrivals — Latest 8 products in a editorial grid
 *  4. Editorial — Three feature callout tiles
 *  5. Product Listing — Full Elasticsearch-powered catalog
 */

import React, { useRef } from 'react';
import { Link } from 'react-router-dom';
import {
  Box,
  Typography,
  Button,
} from '@mui/material';
import { motion, useScroll, useTransform } from 'framer-motion';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import ProductListingPage from '@/features/catalog/storefront/pages/ProductListingPage';


// ── Hero section ───────────────────────────────────────────────────────────────
const HeroSection: React.FC = () => {
  const heroRef = useRef<HTMLDivElement>(null);
  const { scrollY } = useScroll();
  const y = useTransform(scrollY, [0, 600], [0, 200]);

  return (
    <Box
      ref={heroRef}
      component="section"
      sx={{
        position: 'relative',
        height: '95vh',
        minHeight: 600,
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'background.default',
        borderBottom: '1px solid',
        borderColor: 'divider',
      }}
    >
      {/* Subtle grid texture overlay */}
      <Box
        sx={{
          position: 'absolute',
          inset: 0,
          backgroundImage: (theme) =>
            `repeating-linear-gradient(0deg, transparent, transparent 59px, ${theme.palette.border.default} 60px), repeating-linear-gradient(90deg, transparent, transparent 59px, ${theme.palette.border.default} 60px)`,
          opacity: 0.18,
          pointerEvents: 'none',
        }}
      />

      {/* Parallax large brand mark */}
      <motion.div style={{ y }} className="parallax-bg">
        <Typography
          component="div"
          sx={{
            fontFamily: (theme) => theme.typography.fontFamilyDisplay,
            fontWeight: 800,
            fontSize: { xs: '28vw', md: '22vw' },
            lineHeight: 0.85,
            letterSpacing: '-0.02em',
            color: 'transparent',
            WebkitTextStroke: (theme) => `1px ${theme.palette.border.default}`,
            userSelect: 'none',
            pointerEvents: 'none',
            position: 'absolute',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            whiteSpace: 'nowrap',
          }}
        >
          EGO
        </Typography>
      </motion.div>

      {/* Foreground content */}
      <Box
        sx={{
          position: 'relative',
          zIndex: 2,
          textAlign: 'center',
          px: 3,
        }}
      >
        {/* Eyebrow */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.1, ease: 'easeOut' }}
        >
          <Typography
            variant="overline"
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              fontWeight: 600,
              fontSize: '0.7rem',
              letterSpacing: '0.3em',
              color: 'text.secondary',
              display: 'block',
              mb: 4,
            }}
          >
            SEASON 2025 — ARCHIVE COLLECTION
          </Typography>
        </motion.div>

        {/* Main headline */}
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.9, delay: 0.25, ease: 'easeOut' }}
        >
          <Typography
            variant="h1"
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyDisplay,
              fontWeight: 700,
              fontSize: { xs: '3.8rem', sm: '5.5rem', md: '8rem', lg: '10rem' },
              lineHeight: 0.9,
              letterSpacing: '-0.02em',
              color: 'text.primary',
              textTransform: 'uppercase',
              mb: 6,
            }}
          >
            EGO
          </Typography>
        </motion.div>

        {/* Sub-headline */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.45, ease: 'easeOut' }}
        >
          <Typography
            variant="body1"
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyEditorial,
              fontSize: { xs: '1rem', md: '1.2rem' },
              color: 'text.secondary',
              maxWidth: 520,
              mx: 'auto',
              lineHeight: 1.7,
              mb: 8,
            }}
          >
            An exploration of form, void, and restraint. Luxury streetwear engineered for the modern landscape.
          </Typography>
        </motion.div>

        {/* CTAs */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.6, ease: 'easeOut' }}
          style={{ display: 'flex', gap: '16px', justifyContent: 'center', flexWrap: 'wrap' }}
        >
          <Button
            component={Link}
            to="/products"
            variant="contained"
            size="large"
            endIcon={<ArrowForwardIcon />}
            sx={{
              bgcolor: 'text.primary',
              color: 'background.default',
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              fontWeight: 700,
              fontSize: '0.8rem',
              letterSpacing: '0.18em',
              textTransform: 'uppercase',
              px: 5,
              py: 1.75,
              borderRadius: 0,
              border: (theme) => `1px solid ${theme.palette.text.primary}`,
              transition: 'all 0.3s ease',
              '&:hover': {
                bgcolor: 'transparent',
                color: 'text.primary',
              },
            }}
          >
            Shop Collection
          </Button>

        </motion.div>
      </Box>

      {/* Bottom scroll indicator */}
      <Box
        sx={{
          position: 'absolute',
          bottom: 32,
          left: '50%',
          transform: 'translateX(-50%)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 1,
          opacity: 0.5,
        }}
      >
        <Typography
          variant="overline"
          sx={{
            fontFamily: (theme) => theme.typography.fontFamilyUtility,
            fontSize: '0.6rem',
            letterSpacing: '0.2em',
            color: 'text.secondary',
          }}
        >
          SCROLL
        </Typography>
        <Box
          sx={{
            width: 1,
            height: 48,
            bgcolor: 'surface.tertiary',
            position: 'relative',
            overflow: 'hidden',
            '&::after': {
              content: '""',
              position: 'absolute',
              top: '-100%',
              left: 0,
              width: '100%',
              height: '100%',
              bgcolor: 'text.primary',
              animation: 'scrollLine 2s ease-in-out infinite',
            },
            '@keyframes scrollLine': {
              '0%':   { top: '-100%' },
              '100%': { top: '100%' },
            },
          }}
        />
      </Box>
    </Box>
  );
};

// ── Brand statement divider ────────────────────────────────────────────────────
const StatementBand: React.FC = () => (
  <Box
    sx={{
      borderTop: '1px solid',
      borderBottom: '1px solid',
      borderColor: 'divider',
      bgcolor: 'text.primary',
      py: { xs: 6, md: 10 },
      textAlign: 'center',
      px: 4,
    }}
  >
    <Typography
      variant="h3"
      sx={{
        fontFamily: (theme) => theme.typography.fontFamilyDisplay,
        fontWeight: 700,
        fontSize: { xs: '1.6rem', sm: '2.2rem', md: '3rem' },
        color: 'background.default',
        lineHeight: 1.3,
        maxWidth: 900,
        mx: 'auto',
        fontStyle: 'italic',
      }}
    >
      "Clothing is not decoration. It is a declaration of who you choose to be."
    </Typography>
    <Typography
      variant="overline"
      sx={{
        fontFamily: (theme) => theme.typography.fontFamilyUtility,
        fontWeight: 600,
        fontSize: '0.65rem',
        letterSpacing: '0.25em',
        color: 'text.secondary',
        display: 'block',
        mt: 3,
      }}
    >
      — EGO ARCHIVE, 2025
    </Typography>
  </Box>
);

// ── Main HomePage ──────────────────────────────────────────────────────────────

const HomePage: React.FC = () => {
  return (
    <Box sx={{ width: '100%', minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>


      {/* 2. Cinematic Hero */}
      <HeroSection />

      {/* 3. Statement Band */}
      <StatementBand />

      {/* 6. Full Product Listing */}
      <Box
        sx={{
          flex: 1,
          bgcolor: 'background.default',
          borderTop: '1px solid',
          borderColor: 'divider',
        }}
      >
        <ProductListingPage />
      </Box>

    </Box>
  );
};

export default HomePage;
