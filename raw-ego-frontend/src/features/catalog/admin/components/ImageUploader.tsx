/**
 * ImageUploader.tsx
 */

import React, { useRef, useState } from 'react';
import { Box, Typography, CircularProgress, alpha } from '@mui/material';
import { motion, AnimatePresence } from 'framer-motion';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import { useUploadGalleryImage, useUploadVariantImage } from '@/hooks/useAdminCatalog';
import { toast } from '@/store/uiStore';

interface Props {
  productId: number;
  variantId?: number;
  nextDisplayOrder: number;
}

const ImageUploader: React.FC<Props> = ({ productId, variantId, nextDisplayOrder }) => {
  const fileInputRef = useRef<HTMLInputElement>(null);
  
  const { mutateAsync: uploadGallery, isPending: isUploadingGallery } = useUploadGalleryImage(productId);
  const { mutateAsync: uploadVariant, isPending: isUploadingVariant } = useUploadVariantImage(productId, variantId ?? 0);
  
  const isUploading = isUploadingGallery || isUploadingVariant;
  const [isDragActive, setIsDragActive] = useState(false);

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragActive(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragActive(false);
  };

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragActive(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      await processFile(e.dataTransfer.files[0]);
    }
  };

  const processFile = async (file: File) => {
    try {
      if (variantId) {
        await uploadVariant({ file, displayOrder: nextDisplayOrder });
      } else {
        await uploadGallery({ file, displayOrder: nextDisplayOrder });
      }
      toast.success('Image uploaded successfully');
    } catch {
      toast.error('Failed to upload image');
    }
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      await processFile(e.target.files[0]);
    }
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  return (
    <Box
      component={motion.div}
      whileHover={{ scale: 1.01 }}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      sx={{
        border: '2px dashed',
        borderColor: isDragActive ? 'primary.main' : 'divider',
        bgcolor: (theme) => isDragActive ? alpha(theme.palette.text.primary, 0.04) : 'transparent',
        p: 4,
        textAlign: 'center',
        borderRadius: 3,
        transition: 'all 0.2s ease',
        cursor: 'pointer',
        position: 'relative',
        overflow: 'hidden'
      }}
      onClick={() => !isUploading && fileInputRef.current?.click()}
    >
      <input
        type="file"
        accept="image/*"
        style={{ display: 'none' }}
        ref={fileInputRef}
        onChange={handleFileChange}
      />
      <AnimatePresence mode="wait">
        {isUploading ? (
          <motion.div
            key="uploading"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
          >
            <CircularProgress size={32} sx={{ mb: 2 }} />
            <Typography variant="metadata" >Uploading...</Typography>
          </motion.div>
        ) : (
          <motion.div
            key="idle"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
          >
            <CloudUploadIcon sx={{ fontSize: 48, color: isDragActive ? 'primary.main' : 'text.secondary', mb: 1, transition: 'color 0.2s' }} />
            <Typography variant="metadata" gutterBottom>
              {isDragActive ? 'Drop image here' : 'Click or drag image to upload'}
            </Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              JPEG, PNG, WEBP (Max 5MB)
            </Typography>
          </motion.div>
        )}
      </AnimatePresence>
    </Box>
  );
};

export default ImageUploader;
