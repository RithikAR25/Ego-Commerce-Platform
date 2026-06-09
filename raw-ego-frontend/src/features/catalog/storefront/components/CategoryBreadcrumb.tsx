/**
 * CategoryBreadcrumb.tsx
 *
 * 3-level breadcrumb trail for the category hierarchy.
 *
 * Renders: ROOT > GROUP > LEAF (or fewer items for higher-level pages).
 * Each segment except the last is a clickable link.
 * The last segment is the current page (non-linked, bold).
 *
 * Usage:
 *   <CategoryBreadcrumb categorySlug="t-shirts" />
 *   // Renders: Men > Topwear > T-Shirts
 */

import React from 'react';
import { Box, Typography, Skeleton } from '@mui/material';
import { Link } from 'react-router-dom';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useCategoryBreadcrumbs } from '@/hooks/useCatalog';
import type { CategoryResponse } from '@/types/catalog.types';

interface CategoryBreadcrumbProps {
  /** URL slug of the current category (ROOT, GROUP, or LEAF). */
  categorySlug: string;

  /**
   * Optional: if the breadcrumb data was already fetched by the parent,
   * pass it directly to avoid a redundant network request.
   */
  data?: CategoryResponse[];
}

const CategoryBreadcrumb: React.FC<CategoryBreadcrumbProps> = ({ categorySlug, data: propData }) => {
  const { data: fetchedData, isLoading } = useCategoryBreadcrumbs(
    propData ? '' : categorySlug   // skip fetch if propData provided
  );

  const breadcrumbs = propData ?? fetchedData;

  if (isLoading && !propData) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
        {[1, 2, 3].map((i) => (
          <React.Fragment key={i}>
            <Skeleton width={60} height={16} />
            {i < 3 && <ChevronRightIcon sx={{ fontSize: 14, color: 'text.disabled' }} />}
          </React.Fragment>
        ))}
      </Box>
    );
  }

  if (!breadcrumbs || breadcrumbs.length === 0) return null;

  return (
    <Box
      component="nav"
      aria-label="Category breadcrumb"
      sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 0.25 }}
    >
      {breadcrumbs.map((crumb, idx) => {
        const isLast = idx === breadcrumbs.length - 1;

        return (
          <React.Fragment key={crumb.id}>
            {isLast ? (
              <Typography variant="metadata" sx={{ color: 'text.primary', }}>
                {crumb.name}
              </Typography>
            ) : (
              <Typography
                component={Link}
                to={`/products?category=${crumb.slug}`}
                variant="caption"
                sx={{
                  color:          'text.secondary',
                  textDecoration: 'none',
                  letterSpacing:  '0.02em',
                  '&:hover': {
                    color:          'text.primary',
                    textDecoration: 'underline',
                  },
                }}
              >
                {crumb.name}
              </Typography>
            )}

            {!isLast && (
              <ChevronRightIcon
                sx={{ fontSize: 13, color: 'text.disabled', flexShrink: 0 }}
              />
            )}
          </React.Fragment>
        );
      })}
    </Box>
  );
};

export default CategoryBreadcrumb;
