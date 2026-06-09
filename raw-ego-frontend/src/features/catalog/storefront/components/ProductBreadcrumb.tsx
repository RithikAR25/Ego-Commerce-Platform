/**
 * ProductBreadcrumb.tsx
 *
 * Storefront breadcrumb navigation for PDP.
 */

import React from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { Breadcrumbs, Typography, Link, Skeleton, Box } from '@mui/material';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
import type { CategoryResponse } from '@/types/catalog.types';
import { useCategoryBreadcrumbs } from '@/hooks/useCatalog';

interface Props {
  category: CategoryResponse;
  productName: string;
}

const ProductBreadcrumb: React.FC<Props> = ({ category, productName }) => {
  const { data: breadcrumbs, isLoading } = useCategoryBreadcrumbs(category.slug);

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 3 }}>
        {[1, 2, 3, 4].map((i) => (
          <React.Fragment key={i}>
            <Skeleton width={60} height={16} />
            {i < 4 && <NavigateNextIcon fontSize="small" sx={{ color: 'text.disabled' }} />}
          </React.Fragment>
        ))}
      </Box>
    );
  }

  const items = breadcrumbs || [category];

  return (
    <Breadcrumbs
      separator={<NavigateNextIcon fontSize="small" />}
      aria-label="breadcrumb"
      sx={{ mb: 3, '& .MuiBreadcrumbs-separator': { mx: 0.5 } }}
    >
      <Link component={RouterLink} to="/" color="inherit" underline="hover" sx={{ fontSize: '0.85rem' }}>
        Home
      </Link>
      {items.map((crumb) => (
        <Link 
          key={crumb.id} 
          component={RouterLink} 
          to={`/products?category=${crumb.slug}`} 
          color="inherit" 
          underline="hover" 
          sx={{ fontSize: '0.85rem' }}
        >
          {crumb.name}
        </Link>
      ))}
      <Typography variant="metadata" color="text.primary">
        {productName}
      </Typography>
    </Breadcrumbs>
  );
};

export default ProductBreadcrumb;
