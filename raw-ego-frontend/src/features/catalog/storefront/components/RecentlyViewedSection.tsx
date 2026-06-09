import React from 'react';
import { Box, Typography, Grid } from '@mui/material';
import { useRecentlyViewed } from '@/hooks/useRecentlyViewed';
import ProductCard from './ProductCard'; // Since it's in the same directory as ProductCard

const RecentlyViewedSection: React.FC<{ currentProductId: number }> = ({ currentProductId }) => {
  const { items } = useRecentlyViewed();
  
  // Filter out the currently viewed product
  const recentItems = items.filter(item => item.id !== currentProductId).slice(0, 4);

  if (recentItems.length === 0) return null;

  return (
    <Box sx={{ mt: 10, mb: 4 }}>
      <Typography variant="metadata" sx={{ mb: 4, }}>
        Recently Viewed
      </Typography>
      <Grid container spacing={3}>
        {recentItems.map((item) => (
          <Grid size={{ xs: 6, md: 3 }} key={item.id}>
            {/* Minimal implementation mapping ViewedProduct to ProductSearchResponse shape for ProductCard */}
            <ProductCard 
              product={{
                id: item.id,
                name: item.name,
                slug: item.slug,
                minPrice: item.price ?? null,
                maxPrice: item.compareAtPrice ?? null,
                primaryImageUrl: item.imageUrl || null,
                status: 'ACTIVE',
                categoryName: '',
                createdAt: new Date().toISOString(),
              }} 
            />
          </Grid>
        ))}
      </Grid>
    </Box>
  );
};

export default RecentlyViewedSection;
