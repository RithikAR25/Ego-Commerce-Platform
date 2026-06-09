/**
 * ImageGrid.tsx
 *
 * Renders uploaded images in a responsive grid.
 * Provides controls for deleting, setting primary (if variant), and reordering.
 */

import React, { useState } from 'react';
import { Box, Typography, IconButton, Chip, Paper, alpha } from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import {
  useDeleteGalleryImage,
  useDeleteVariantImage,
  useSetPrimaryVariantImage,
  useReorderGalleryImages,
  useReorderVariantImages,
} from '@/hooks/useAdminCatalog';
import { toast } from '@/store/uiStore';
import type { ImageResponse } from '@/types/image.types';

interface Props {
  productId: number;
  variantId?: number;
  images: ImageResponse[];
}

const ImageGrid: React.FC<Props> = ({ productId, variantId, images }) => {
  const { mutateAsync: deleteGallery } = useDeleteGalleryImage(productId);
  const { mutateAsync: deleteVariant } = useDeleteVariantImage(productId, variantId ?? 0);
  const { mutateAsync: setPrimary }    = useSetPrimaryVariantImage(productId, variantId ?? 0);
  const { mutateAsync: reorderGallery } = useReorderGalleryImages(productId);
  const { mutateAsync: reorderVariant } = useReorderVariantImages(productId, variantId ?? 0);

  const isVariant = Boolean(variantId);
  
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null);
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null);

  const handleDelete = async (imageId: number) => {
    if (!window.confirm('Are you sure you want to delete this image?')) return;
    try {
      if (isVariant) await deleteVariant(imageId);
      else           await deleteGallery(imageId);
      toast.success('Image deleted');
    } catch {
      toast.error('Failed to delete image');
    }
  };

  const handleSetPrimary = async (imageId: number) => {
    try {
      await setPrimary(imageId);
      toast.success('Primary image updated');
    } catch {
      toast.error('Failed to update primary image');
    }
  };

  // Reorder using drag and drop
  const handleDragStart = (e: React.DragEvent, index: number) => {
    setDraggedIndex(index);
    e.dataTransfer.effectAllowed = 'move';
  };

  const handleDragOver = (e: React.DragEvent, index: number) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    if (dragOverIndex !== index) {
      setDragOverIndex(index);
    }
  };

  const handleDragEnd = () => {
    setDraggedIndex(null);
    setDragOverIndex(null);
  };

  const handleDrop = async (e: React.DragEvent, dropIndex: number) => {
    e.preventDefault();
    if (draggedIndex === null || draggedIndex === dropIndex) {
      handleDragEnd();
      return;
    }

    const newOrder = [...images];
    const [draggedItem] = newOrder.splice(draggedIndex, 1);
    newOrder.splice(dropIndex, 0, draggedItem);

    const payload = newOrder.map((img, idx) => ({ imageId: img.id, displayOrder: idx }));

    handleDragEnd();

    try {
      if (isVariant) await reorderVariant(payload);
      else           await reorderGallery(payload);
      toast.success('Images reordered');
    } catch {
      toast.error('Failed to reorder images');
    }
  };

  if (images.length === 0) {
    return (
      <Box sx={{ p: 2, textAlign: 'center', bgcolor: (theme) => alpha(theme.palette.text.primary, 0.02), borderRadius: 1 }}>
        <Typography variant="body2" color="text.secondary">No images uploaded yet</Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: 2 }}>
      {images.map((img, index) => (
        <Paper
          key={img.id}
          variant="outlined"
          draggable
          onDragStart={(e) => handleDragStart(e, index)}
          onDragOver={(e) => handleDragOver(e, index)}
          onDragEnd={handleDragEnd}
          onDrop={(e) => handleDrop(e, index)}
          sx={{
            position: 'relative',
            overflow: 'hidden',
            aspectRatio: '4/5',
            borderRadius: 2,
            cursor: 'grab',
            opacity: draggedIndex === index ? 0.5 : 1,
            transform: dragOverIndex === index ? 'scale(1.05)' : 'scale(1)',
            transition: 'transform 0.2s, opacity 0.2s',
            border: (theme) => dragOverIndex === index ? `2px dashed ${theme.palette.text.primary}` : '1px solid transparent',
            '&:hover .controls': { opacity: 1 },
          }}
        >
          {/* Image */}
          <Box
            component="img"
            src={img.transformations?.thumbnail ?? img.url}
            alt={img.altText}
            sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
          />

          {/* Primary Badge */}
          {img.primary && (
            <Chip
              label="Primary"
              size="small"
              color="primary"
              icon={<StarIcon fontSize="small" />}
              sx={{ position: 'absolute', top: 8, left: 8, fontWeight: 600, boxShadow: 1 }}
            />
          )}

          {/* Hover Controls */}
          <Box
            className="controls"
            sx={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              bgcolor: 'overlay.main',
              opacity: 0,
              transition: 'opacity 0.2s',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between',
              p: 1,
            }}
          >
            {/* Top row: Delete & Primary */}
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              {isVariant ? (
                <IconButton
                  size="small"
                  onClick={() => handleSetPrimary(img.id)}
                  sx={{ color: 'text.primary', bgcolor: 'overlay.main', '&:hover': { bgcolor: 'overlay.dark' } }}
                  title="Set as Primary"
                >
                  {img.primary ? <StarIcon fontSize="small" /> : <StarBorderIcon fontSize="small" />}
                </IconButton>
              ) : <Box />}
              <IconButton
                size="small"
                onClick={() => handleDelete(img.id)}
                sx={{ color: 'error.main', bgcolor: 'overlay.main', '&:hover': { bgcolor: 'overlay.dark' } }}
              >
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Box>

            {/* Bottom row: Drag Handle hint */}
            <Box sx={{ display: 'flex', justifyContent: 'center' }}>
              <Typography variant="caption" sx={{ color: 'text.primary', bgcolor: 'overlay.main', px: 1, py: 0.5, borderRadius: 1 }}>
                Drag to reorder
              </Typography>
            </Box>
          </Box>
        </Paper>
      ))}
    </Box>
  );
};

export default ImageGrid;
