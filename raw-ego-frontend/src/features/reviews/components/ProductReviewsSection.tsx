import React, { useState } from 'react';
import {
  Box, Typography, Grid, LinearProgress, Rating, Button,
  Avatar, Paper, Dialog, DialogTitle, DialogContent, DialogActions,
  TextField, Pagination, Skeleton
} from '@mui/material';
import CreateIcon from '@mui/icons-material/Create';
import { useProductReviews, useProductRatingSummary, useSubmitReview } from '../hooks/useReviews';
import { useAuthStore } from '@/store/authStore';
import { useNavigate } from 'react-router-dom';

interface ProductReviewsSectionProps {
  productId: number;
}

const ProductReviewsSection: React.FC<ProductReviewsSectionProps> = ({ productId }) => {
  const [page, setPage] = useState(0);
  const { data: summary, isLoading: loadingSummary } = useProductRatingSummary(productId);
  const { data: reviews, isLoading: loadingReviews } = useProductReviews(productId, page, 5);
  const { isAuthenticated } = useAuthStore();
  const navigate = useNavigate();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [rating, setRating] = useState<number | null>(0);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const { mutate: submitReview, isPending: isSubmitting } = useSubmitReview(productId);

  const handleWriteReview = () => {
    if (!isAuthenticated) {
      navigate('/login');
      return;
    }
    setDialogOpen(true);
  };

  const handleSubmit = () => {
    if (!rating || rating < 1) return;
    submitReview(
      { rating, title: title.trim() || undefined, body: body.trim() || undefined },
      {
        onSuccess: () => {
          setDialogOpen(false);
          setRating(0);
          setTitle('');
          setBody('');
        }
      }
    );
  };

  return (
    <Box sx={{ mt: 10 }}>
      <Typography variant="metadata" sx={{ mb: 6 }}>
        Customer Reviews
      </Typography>

      <Grid container spacing={6}>
        {/* Left column: Summary */}
        <Grid size={{ xs: 12, md: 4 }}>
          {loadingSummary ? (
            <Skeleton variant="rectangular" height={200} />
          ) : !summary || summary.reviewCount === 0 ? (
            <Box sx={{ textAlign: 'center', py: 4, bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
              <Typography variant="h6" color="text.secondary" sx={{ mb: 2 }}>No reviews yet</Typography>
              <Button
                variant="outlined"
                startIcon={<CreateIcon />}
                onClick={handleWriteReview}
                sx={{ borderRadius: 0, textTransform: 'uppercase', letterSpacing: '0.04em' }}
              >
                Write a Review
              </Button>
            </Box>
          ) : (
            <Box>
              <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: 2, mb: 3 }}>
                <Typography variant="metadata" >
                  {(summary.averageRating || 0).toFixed(1)}
                </Typography>
                <Box sx={{ pb: 0.5 }}>
                  <Rating value={summary.averageRating || 0} precision={0.1} readOnly size="small" />
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Based on {summary.reviewCount} review{summary.reviewCount > 1 ? 's' : ''}
                  </Typography>
                </Box>
              </Box>

              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, mb: 4 }}>
                {[5, 4, 3, 2, 1].map((star) => {
                  const count = summary.ratingBreakdown[star] || 0;
                  const percentage = summary.reviewCount > 0 ? (count / summary.reviewCount) * 100 : 0;
                  return (
                    <Box key={star} sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                      <Typography variant="metadata" sx={{ minWidth: 16, }}>{star}</Typography>
                      <Rating value={1} max={1} readOnly size="small" sx={{ color: 'text.secondary' }} />
                      <LinearProgress
                        variant="determinate"
                        value={percentage}
                        sx={{
                          flex: 1,
                          height: 6,
                          bgcolor: 'divider',
                          '& .MuiLinearProgress-bar': { bgcolor: 'text.primary' }
                        }}
                      />
                      <Typography variant="caption" color="text.secondary" sx={{ minWidth: 24, textAlign: 'right' }}>
                        {count}
                      </Typography>
                    </Box>
                  );
                })}
              </Box>

              <Button
                fullWidth
                variant="outlined"
                startIcon={<CreateIcon />}
                onClick={handleWriteReview}
                sx={{ borderRadius: 0, textTransform: 'uppercase', letterSpacing: '0.04em', py: 1.5 }}
              >
                Write a Review
              </Button>
            </Box>
          )}
        </Grid>

        {/* Right column: Review List */}
        <Grid size={{ xs: 12, md: 8 }}>
          {loadingReviews ? (
            Array.from({ length: 3 }).map((_, i) => (
              <Box key={i} sx={{ mb: 4 }}><Skeleton variant="rectangular" height={150} /></Box>
            ))
          ) : !reviews || reviews.empty ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
              <Typography variant="body1" color="text.secondary">Be the first to share your thoughts.</Typography>
            </Box>
          ) : (
            <>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                {reviews.content.map((review) => (
                  <Paper key={review.id} elevation={0} sx={{ p: 3, border: '1px solid', borderColor: 'divider' }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                      <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
                        <Avatar sx={{ width: 40, height: 40, bgcolor: 'text.primary', fontSize: '1rem', fontWeight: 700 }}>
                          {review.reviewerFirstName ? review.reviewerFirstName.charAt(0).toUpperCase() : '?'}
                        </Avatar>
                        <Box>
                          <Typography variant="metadata" >{review.reviewerFirstName}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {new Date(review.createdAt).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })}
                          </Typography>
                        </Box>
                      </Box>
                      <Rating value={review.rating} readOnly size="small" />
                    </Box>
                    {review.title && (
                      <Typography variant="metadata" sx={{ mb: 1 }}>{review.title}</Typography>
                    )}
                    {review.body && (
                      <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>
                        {review.body}
                      </Typography>
                    )}
                  </Paper>
                ))}
              </Box>

              {reviews.totalPages > 1 && (
                <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
                  <Pagination
                    count={reviews.totalPages}
                    page={page + 1}
                    onChange={(_, p) => setPage(p - 1)}
                    shape="rounded"
                  />
                </Box>
              )}
            </>
          )}
        </Grid>
      </Grid>

      {/* Write Review Dialog */}
      <Dialog open={dialogOpen} onClose={() => !isSubmitting && setDialogOpen(false)} maxWidth="sm" fullWidth slotProps={{ paper: { sx: { borderRadius: 0, p: 1 } } }}>
        <DialogTitle sx={{ fontWeight: 800, textTransform: 'uppercase', letterSpacing: '0.04em' }}>Write a Review</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', mb: 4, mt: 2 }}>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>Overall Rating</Typography>
            <Rating
              size="large"
              value={rating}
              onChange={(_, newValue) => setRating(newValue)}
              disabled={isSubmitting}
            />
          </Box>
          <TextField
            fullWidth
            label="Review Title"
            variant="outlined"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            disabled={isSubmitting}
            sx={{ mb: 3, '& .MuiOutlinedInput-root': { borderRadius: 0 } }}
            placeholder="Sum up your experience"
          />
          <TextField
            fullWidth
            label="Review Detail"
            variant="outlined"
            multiline
            rows={4}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            disabled={isSubmitting}
            sx={{ mb: 2, '& .MuiOutlinedInput-root': { borderRadius: 0 } }}
            placeholder="What did you like or dislike?"
          />
          <Typography variant="caption" color="text.secondary">
            Note: You can only submit a review if you have purchased and received this item.
          </Typography>
        </DialogContent>
        <DialogActions sx={{ p: 3, pt: 0 }}>
          <Button onClick={() => setDialogOpen(false)} disabled={isSubmitting} sx={{ borderRadius: 0 }}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleSubmit}
            disabled={!rating || rating < 1 || isSubmitting}
            sx={{ borderRadius: 0, bgcolor: 'text.primary', color: 'background.paper', '&:hover': { bgcolor: 'surface.secondary', color: 'text.primary' } }}
          >
            Submit Review
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ProductReviewsSection;
