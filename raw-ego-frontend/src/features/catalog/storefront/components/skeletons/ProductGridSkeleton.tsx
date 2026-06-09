import React from 'react';
import { Grid } from '@mui/material';
import ProductCardSkeleton from './ProductCardSkeleton';

interface Props {
  count?: number;
}

const ProductGridSkeleton: React.FC<Props> = ({ count = 12 }) => {
  return (
    <Grid container spacing={3}>
      {Array.from({ length: count }).map((_, index) => (
        <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={`skeleton-${index}`}>
          <ProductCardSkeleton />
        </Grid>
      ))}
    </Grid>
  );
};

export default ProductGridSkeleton;
