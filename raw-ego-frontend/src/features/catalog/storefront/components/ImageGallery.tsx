import React, { useState, useEffect, useCallback } from 'react';
import { Box, IconButton } from '@mui/material';
import { motion, AnimatePresence } from 'framer-motion';
import useEmblaCarousel from 'embla-carousel-react';
import type { ImageResponse } from '@/types/image.types';
import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';

interface Props {
  variantImages: ImageResponse[];
  galleryImages: ImageResponse[];
}

const ImageGallery: React.FC<Props> = ({ variantImages, galleryImages }) => {
  const allImages = [
    ...variantImages.map(img => ({ ...img, _key: `v-${img.id}` })),
    ...galleryImages.map(img => ({ ...img, _key: `g-${img.id}` })),
  ];
  
  const [selectedImage, setSelectedImage] = useState<ImageResponse & { _key?: string } | null>(null);
  
  // Embla Carousel for mobile
  const [emblaRef, emblaApi] = useEmblaCarousel({ loop: true });
  const [selectedIndex, setSelectedIndex] = useState(0);

  const scrollTo = useCallback((index: number) => {
    if (emblaApi) emblaApi.scrollTo(index);
  }, [emblaApi]);

  const onSelect = useCallback(() => {
    if (!emblaApi) return;
    setSelectedIndex(emblaApi.selectedScrollSnap());
  }, [emblaApi]);

  useEffect(() => {
    if (emblaApi) {
      emblaApi.on('select', onSelect);
      onSelect();
    }
  }, [emblaApi, onSelect]);

  // Zoom logic
  const [isZooming, setIsZooming] = useState(false);
  const [zoomPos, setZoomPos] = useState({ x: 0, y: 0 });

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    const { left, top, width, height } = e.currentTarget.getBoundingClientRect();
    const x = ((e.clientX - left) / width) * 100;
    const y = ((e.clientY - top) / height) * 100;
    setZoomPos({ x, y });
  };

  useEffect(() => {
    if (variantImages.length > 0) {
      const primary = variantImages.find(img => img.primary) || variantImages[0];
      setSelectedImage({ ...primary, _key: `v-${primary.id}` });
    } else if (galleryImages.length > 0) {
      setSelectedImage({ ...galleryImages[0], _key: `g-${galleryImages[0].id}` });
    } else {
      setSelectedImage(null);
    }
    // Reset carousel index when images change
    if (emblaApi) emblaApi.scrollTo(0);
  }, [variantImages, galleryImages, emblaApi]);

  if (!selectedImage && allImages.length === 0) {
    return (
      <Box sx={{ aspectRatio: '4/5', bgcolor: 'surface.secondary', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        No images available
      </Box>
    );
  }

  const currentHeroUrl = selectedImage?.transformations?.detail ?? selectedImage?.url ?? '';

  return (
    <Box sx={{ display: 'flex', gap: 2, height: { xs: 'auto', md: 'calc(100vh - 120px)' }, position: { md: 'sticky' }, top: { md: 100 } }}>
      
      {/* Desktop Thumbnail Strip */}
      {allImages.length > 1 && (
        <Box
          sx={{
            display: { xs: 'none', md: 'flex' },
            flexDirection: 'column',
            gap: 2,
            width: 80,
            overflowY: 'auto',
            '&::-webkit-scrollbar': { display: 'none' },
            scrollbarWidth: 'none',
          }}
        >
          {allImages.map((img) => (
            <Box
              key={img._key}
              onClick={() => setSelectedImage(img)}
              sx={{
                width: '100%',
                aspectRatio: '4/5',
                cursor: 'pointer',
                opacity: selectedImage?._key === img._key ? 1 : 0.6,
                border: selectedImage?._key === img._key ? '2px solid black' : '2px solid transparent',
                transition: 'all 0.2s',
                '&:hover': { opacity: 1 },
              }}
            >
              <img
                src={img.transformations?.thumbnail ?? img.url}
                alt={img.altText}
                loading="lazy"
                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
              />
            </Box>
          ))}
        </Box>
      )}

      {/* Desktop Hero Image with Zoom */}
      <Box 
        sx={{ 
          display: { xs: 'none', md: 'block' },
          flex: 1, 
          bgcolor: 'surface.secondary', 
          position: 'relative', 
          overflow: 'hidden',
          cursor: isZooming ? 'zoom-out' : 'zoom-in'
        }}
        onMouseEnter={() => setIsZooming(true)}
        onMouseLeave={() => setIsZooming(false)}
        onMouseMove={handleMouseMove}
      >
        <AnimatePresence mode="wait">
          {!isZooming ? (
            <motion.img
              key={selectedImage?._key}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.3 }}
              src={currentHeroUrl}
              alt={selectedImage?.altText}
              loading="eager"
              style={{ width: '100%', height: '100%', objectFit: 'cover', position: 'absolute', top: 0, left: 0 }}
            />
          ) : (
            <motion.div
              key={`${selectedImage?._key}-zoom`}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: '100%',
                backgroundImage: `url(${currentHeroUrl})`,
                backgroundPosition: `${zoomPos.x}% ${zoomPos.y}%`,
                backgroundSize: '200%',
                backgroundRepeat: 'no-repeat',
              }}
            />
          )}
        </AnimatePresence>
      </Box>

      {/* Mobile Embla Carousel */}
      <Box sx={{ display: { xs: 'block', md: 'none' }, width: '100%', position: 'relative' }}>
        <Box ref={emblaRef} sx={{ overflow: 'hidden', width: '100%' }}>
          <Box sx={{ display: 'flex', touchAction: 'pan-y' }}>
            {allImages.map((img) => (
              <Box key={img._key} sx={{ flex: '0 0 100%', minWidth: 0 }}>
                <Box sx={{ aspectRatio: '4/5', bgcolor: 'surface.secondary' }}>
                  <img
                    src={img.transformations?.detail ?? img.url}
                    alt={img.altText}
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    loading="eager"
                  />
                </Box>
              </Box>
            ))}
          </Box>
        </Box>
        
        {/* Mobile Carousel Dots */}
        {allImages.length > 1 && (
          <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2, gap: 1 }}>
            {allImages.map((img, idx) => (
              <IconButton 
                key={img._key} 
                onClick={() => scrollTo(idx)} 
                size="small"
                sx={{ p: 0 }}
              >
                <FiberManualRecordIcon 
                  sx={{ 
                    fontSize: 12, 
                    color: selectedIndex === idx ? 'primary.main' : 'text.disabled',
                    transition: 'color 0.2s' 
                  }} 
                />
              </IconButton>
            ))}
          </Box>
        )}
      </Box>
    </Box>
  );
};

export default ImageGallery;
