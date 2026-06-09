import React, { useState } from 'react';
import {
  Box,
  Typography,
  Button,
  Rating,
  Card,
  CardContent,
  CardActions,
  Grid,
} from '@mui/material';
import { useProductReviews } from '@/features/reviews/hooks/useReviews';
import { useAdminDeleteReview } from '../hooks/useAdminReviews';
import { toast } from '@/store/uiStore';
import DeleteIcon from '@mui/icons-material/Delete';

interface AdminProductReviewsPanelProps {
  productId: number;
}

const AdminProductReviewsPanel: React.FC<AdminProductReviewsPanelProps> = ({ productId }) => {
  const [page, setPage] = useState(0);
  const rowsPerPage = 10;
  
  const { data, isLoading } = useProductReviews(productId, page, rowsPerPage);
  const { mutate: deleteReview } = useAdminDeleteReview(productId);

  const handleDelete = (reviewId: number) => {
    if (window.confirm('Are you sure you want to permanently delete this review?')) {
      deleteReview(reviewId, {
        onSuccess: () => toast.success('Review deleted successfully'),
        onError: () => toast.error('Failed to delete review'),
      });
    }
  };

  if (isLoading) return <Typography>Loading reviews...</Typography>;

  if (!data?.content || data.content.length === 0) {
    return <Typography color="text.secondary">No reviews for this product yet.</Typography>;
  }

  return (
    <Box>
      <Grid container spacing={2}>
        {data.content.map(review => (
          <Grid size={{ xs: 12 }} key={review.id}>
            <Card variant="outlined" sx={{ bgcolor: 'transparent' }}>
              <CardContent sx={{ pb: 1 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1 }}>
                  <Box>
                    <Rating value={review.rating} readOnly size="small" sx={{ mb: 0.5 }} />
                    <Typography variant="metadata" >
                      {review.title || 'No Title'}
                    </Typography>
                  </Box>
                  <Typography variant="caption" color="text.secondary">
                    {new Date(review.createdAt).toLocaleDateString()}
                  </Typography>
                </Box>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                  {review.body || 'No comment provided.'}
                </Typography>
                <Typography variant="metadata" >
                  By: {review.reviewerFirstName}
                </Typography>
              </CardContent>
              <CardActions sx={{ justifyContent: 'flex-end', pt: 0 }}>
                <Button 
                  size="small" 
                  color="error" 
                  startIcon={<DeleteIcon />} 
                  onClick={() => handleDelete(review.id)}
                >
                  Delete
                </Button>
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>
      
      {data.totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3, gap: 2 }}>
          <Button 
            disabled={page === 0} 
            onClick={() => setPage(p => Math.max(0, p - 1))}
          >
            Previous
          </Button>
          <Typography sx={{ alignSelf: 'center' }}>
            Page {page + 1} of {data.totalPages}
          </Typography>
          <Button 
            disabled={page >= data.totalPages - 1} 
            onClick={() => setPage(p => p + 1)}
          >
            Next
          </Button>
        </Box>
      )}
    </Box>
  );
};

export default AdminProductReviewsPanel;
