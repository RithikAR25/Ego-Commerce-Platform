/**
 * VariantSelector.tsx
 *
 * Storefront component for selecting color and size.
 * RULES:
 *  - Selecting a color triggers onColorSelect (which swaps the image gallery).
 *  - Selecting a size does NOT change images.
 *  - Out of stock combinations are visually disabled.
 */

import React, { useMemo } from 'react';
import { Box, Typography, ButtonBase, useTheme, alpha } from '@mui/material';
import { motion } from 'framer-motion';
import type { ProductVariantResponse, AttributeTypeResponse } from '@/types/catalog.types';

interface Props {
  variants:        ProductVariantResponse[];
  attributeTypes:  AttributeTypeResponse[];
  selectedVariant: ProductVariantResponse;
  onSelectVariant: (variant: ProductVariantResponse) => void;
}

const VariantSelector: React.FC<Props> = ({ variants, attributeTypes, selectedVariant, onSelectVariant }) => {
  const theme = useTheme();
  // Extract all unique colors and sizes from variants
  const colorType = attributeTypes.find(t => t.name.toLowerCase() === 'color');
  const sizeType = attributeTypes.find(t => t.name.toLowerCase() === 'size');

  const availableColors = useMemo(() => {
    if (!colorType) return [];
    const colorIds = new Set(variants.map(v => v.attributeValues.find(a => a.id === colorType.values.find((cv: any) => cv.id === a.id)?.id)?.id).filter(Boolean));
    return colorType.values.filter((v: any) => colorIds.has(v.id));
  }, [variants, colorType]);

  const availableSizes = useMemo(() => {
    if (!sizeType) return [];
    const sizeIds = new Set(variants.map(v => v.attributeValues.find(a => a.id === sizeType.values.find((sv: any) => sv.id === a.id)?.id)?.id).filter(Boolean));
    return sizeType.values.filter((v: any) => sizeIds.has(v.id));
  }, [variants, sizeType]);

  // Current selections based on selectedVariant
  const selectedColorId = selectedVariant.attributeValues.find(a => availableColors.some(c => c.id === a.id))?.id;
  const selectedSizeId  = selectedVariant.attributeValues.find(a => availableSizes.some(s => s.id === a.id))?.id;

  const handleColorClick = (colorId: number) => {
    if (colorId === selectedColorId) return;
    // Find variant with new color and same size (fallback to first available with new color)
    let nextVariant = variants.find(v => 
      v.attributeValues.some(a => a.id === colorId) && 
      v.attributeValues.some(a => a.id === selectedSizeId)
    );
    if (!nextVariant) {
      nextVariant = variants.find(v => v.attributeValues.some(a => a.id === colorId));
    }
    if (nextVariant) onSelectVariant(nextVariant);
  };

  const handleSizeClick = (sizeId: number) => {
    if (sizeId === selectedSizeId) return;
    // Find variant with same color and new size
    const nextVariant = variants.find(v => 
      v.attributeValues.some(a => a.id === selectedColorId) && 
      v.attributeValues.some(a => a.id === sizeId)
    );
    if (nextVariant) onSelectVariant(nextVariant);
  };

  // Helper to check if a color/size combo is in stock
  const isComboInStock = (colorId?: number, sizeId?: number) => {
    const v = variants.find(v => 
      (!colorId || v.attributeValues.some(a => a.id === colorId)) &&
      (!sizeId || v.attributeValues.some(a => a.id === sizeId))
    );
    return v ? v.stockStatus !== 'OUT_OF_STOCK' : false;
  };

  return (
    <Box sx={{ mt: 1.5 }}>
      {/* Colors */}
      {availableColors.length > 0 && (
        <Box sx={{ mb: 2 }}>
          <Typography variant="metadata" sx={{ mb: 1, }}>
            Color: <Typography component="span" color="text.secondary">{availableColors.find(c => c.id === selectedColorId)?.value}</Typography>
          </Typography>
          <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
            {availableColors.map(color => {
              const isSelected = color.id === selectedColorId;
              // Check if THIS color has ANY size in stock
              const hasStock = isComboInStock(color.id, undefined);
              return (
                <ButtonBase
                  key={color.id}
                  component={motion.button}
                  whileHover={hasStock ? { scale: 1.1 } : {}}
                  whileTap={hasStock ? { scale: 0.95 } : {}}
                  onClick={() => handleColorClick(color.id)}
                  sx={{
                    width: 32,
                    height: 32,
                    borderRadius: '50%',
                    border: `1px solid ${alpha(theme.palette.text.primary, 0.1)}`,
                    bgcolor: color.hexColor || theme.palette.text.secondary,
                    opacity: hasStock ? 1 : 0.3,
                    position: 'relative',
                    transition: 'all 0.2s',
                    outline: isSelected ? `1px solid ${theme.palette.text.primary}` : 'none',
                    outlineOffset: '3px',
                  }}
                  title={color.value}
                />
              );
            })}
          </Box>
        </Box>
      )}

      {/* Sizes */}
      {availableSizes.length > 0 && (
        <Box>
          <Typography variant="metadata" sx={{ mb: 1, }}>
            Size: <Typography component="span" color="text.secondary">{availableSizes.find(s => s.id === selectedSizeId)?.value}</Typography>
          </Typography>
          <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
            {availableSizes.map(size => {
              const isSelected = size.id === selectedSizeId;
              const hasStock = isComboInStock(selectedColorId, size.id);
              
              return (
                <ButtonBase
                  key={size.id}
                  component={motion.button}
                  whileHover={hasStock && !isSelected ? { scale: 1.05, backgroundColor: alpha(theme.palette.text.primary, 0.05) } : {}}
                  whileTap={{ scale: 0.95 }}
                  onClick={() => handleSizeClick(size.id)}
                  sx={{
                    minWidth: 40,
                    height: 40,
                    px: 2,
                    border: '1px solid',
                    borderColor: isSelected ? 'text.primary' : alpha(theme.palette.text.primary, 0.1),
                    bgcolor: isSelected ? 'text.primary' : 'transparent',
                    color: isSelected ? 'background.paper' : 'text.primary',
                    fontWeight: isSelected ? 600 : 400,
                    opacity: hasStock ? 1 : 0.5,
                    position: 'relative',
                    overflow: 'hidden',
                    transition: 'all 0.2s ease',
                  }}
                >
                  {size.value}
                  {!hasStock && (
                    <Box sx={{ position: 'absolute', top: '50%', left: -10, right: -10, height: '1px', bgcolor: alpha(theme.palette.text.primary, 0.3), transform: 'rotate(-30deg)' }} />
                  )}
                </ButtonBase>
              );
            })}
          </Box>
        </Box>
      )}
    </Box>
  );
};

export default VariantSelector;
