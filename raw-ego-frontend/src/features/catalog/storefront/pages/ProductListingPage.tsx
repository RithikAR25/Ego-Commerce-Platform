/**
 * ProductListingPage.tsx
 *
 * Elasticsearch-powered product listing with faceted filtering.
 * All filter state is encoded in URL search params (shareable, refresh-proof).
 *
 * Layout:
 *   Desktop: left filter panel (260px) + right product grid
 *   Mobile:  bottom filter drawer toggled by a "Filters" button
 */

import React, { useCallback, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Box,
  Typography,
  Grid,
  Pagination,
  Container,
  Select,
  MenuItem,
  FormControl,
  Checkbox,
  FormControlLabel,
  FormGroup,
  Alert,
  Slider,
  Button,
  Drawer,
  IconButton,
  useMediaQuery,
  useTheme,
  Badge,
} from '@mui/material';
import FilterListIcon from '@mui/icons-material/FilterList';
import CloseIcon from '@mui/icons-material/Close';
import { useSearch } from '@/hooks/useSearch';
import ProductCard from '../components/ProductCard';
import ProductGridSkeleton from '../components/skeletons/ProductGridSkeleton';
import type { SearchParams } from '@/types/search.types';

// ── Helpers ───────────────────────────────────────────────────────────────────

const parseList = (val: string | null): string[] =>
  val ? val.split(',').filter(Boolean) : [];

// ── Filter Panel (shared by desktop sidebar + mobile drawer) ──────────────────

interface FilterPanelProps {
  sizes: { value: string; count: number }[];
  colors: { value: string; count: number }[];
  priceMin: number;
  priceMax: number;
  selectedSizes: string[];
  selectedColors: string[];
  selectedPriceRange: [number, number];
  onSizeChange: (size: string, checked: boolean) => void;
  onColorChange: (color: string, checked: boolean) => void;
  onPriceChange: (range: [number, number]) => void;
  onClear: () => void;
  activeFilterCount: number;
}

const FilterPanel: React.FC<FilterPanelProps> = ({
  sizes, colors, priceMin, priceMax,
  selectedSizes, selectedColors, selectedPriceRange,
  onSizeChange, onColorChange, onPriceChange, onClear, activeFilterCount,
}) => (
  <Box sx={{ width: '100%' }}>
    {/* Clear all */}
    {activeFilterCount > 0 && (
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: '0.08em' }}>
          {activeFilterCount} filter{activeFilterCount > 1 ? 's' : ''} active
        </Typography>
        <Button size="small" onClick={onClear} sx={{ fontSize: '0.75rem', p: 0, minWidth: 0 }}>
          Clear all
        </Button>
      </Box>
    )}

    {/* ── Sizes ── */}
    {sizes.length > 0 && (
      <Box sx={{ mb: 4 }}>
        <Typography variant="metadata" sx={{ color: 'text.primary', mb: 2, pb: 1, borderBottom: `1px solid`, borderColor: 'divider' }}>
          SIZE
        </Typography>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
          {sizes.map(({ value }) => {
            const active = selectedSizes.includes(value);
            return (
              <Box
                key={value}
                onClick={() => onSizeChange(value, !active)}
                sx={{
                  px: 2, py: 0.5,
                  border: '1px solid',
                  borderColor: active ? 'primary.main' : 'divider',
                  bgcolor: active ? 'background.paper' : 'transparent',
                  color: active ? 'primary.main' : 'text.primary',
                  cursor: 'pointer',
                  userSelect: 'none',
                  fontSize: '0.875rem',
                  borderRadius: '0px',
                  transition: 'all 0.2s',
                  '&:hover': { borderColor: 'primary.main' },
                }}
              >
                {value}
              </Box>
            );
          })}
        </Box>
      </Box>
    )}

    {/* ── Colors ── */}
    {colors.length > 0 && (
      <Box sx={{ mb: 4 }}>
        <Typography variant="metadata" sx={{ color: 'text.primary', mb: 2, pb: 1, borderBottom: `1px solid`, borderColor: 'divider' }}>
          COLOR
        </Typography>
        <FormGroup>
          {colors.map(({ value }) => (
            <FormControlLabel
              key={value}
              label={
                <Typography variant="body1" sx={{ color: 'text.secondary', transition: 'color 0.2s', '&:hover': { color: 'text.primary' } }}>
                  {value}
                </Typography>
              }
              control={
                <Checkbox
                  size="small"
                  checked={selectedColors.includes(value)}
                  onChange={(e) => onColorChange(value, e.target.checked)}
                  sx={{ py: 0.5, color: 'text.secondary', '&.Mui-checked': { color: 'primary.main' } }}
                />
              }
            />
          ))}
        </FormGroup>
      </Box>
    )}

    {/* ── Price Range ── */}
    {priceMax > priceMin && (
      <Box sx={{ mb: 4 }}>
        <Typography variant="metadata" sx={{ color: 'text.primary', mb: 2, pb: 1, borderBottom: `1px solid`, borderColor: 'divider' }}>
          PRICE
        </Typography>
        <Box sx={{ px: 1 }}>
          <Slider
            value={selectedPriceRange}
            min={Math.floor(priceMin)}
            max={Math.ceil(priceMax)}
            onChange={(_, v) => onPriceChange(v as [number, number])}
            valueLabelDisplay="auto"
            valueLabelFormat={(v) => `$${v.toLocaleString('en-US')}`}
            disableSwap
            size="small"
            sx={{ color: 'text.primary' }}
          />
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 0.5 }}>
            <Typography variant="body2" color="text.secondary">
              ${selectedPriceRange[0].toLocaleString('en-US')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              ${selectedPriceRange[1].toLocaleString('en-US')}
            </Typography>
          </Box>
        </Box>
      </Box>
    )}
  </Box>
);

// ── Main Page ─────────────────────────────────────────────────────────────────

const ProductListingPage: React.FC = () => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  const [searchParams, setSearchParams] = useSearchParams();
  const [mobileFilterOpen, setMobileFilterOpen] = useState(false);
  // Local slider state (only committed to URL on mouse-up to avoid thrashing)
  const [localPriceRange, setLocalPriceRange] = useState<[number, number] | null>(null);

  // ── Read filters from URL ──────────────────────────────────────────────────
  const query = searchParams.get('query') || undefined;
  const category = searchParams.get('category') || undefined;
  const page = Math.max(0, parseInt(searchParams.get('page') || '1', 10) - 1);
  const sort = (searchParams.get('sort') || 'createdAt,desc') as SearchParams['sort'];
  const sizes = parseList(searchParams.get('sizes'));
  const colors = parseList(searchParams.get('colors'));
  const minPrice = searchParams.get('minPrice') ? Number(searchParams.get('minPrice')) : undefined;
  const maxPrice = searchParams.get('maxPrice') ? Number(searchParams.get('maxPrice')) : undefined;

  // ── Build search params ──────────────────────────────────────────────────
  // categorySlug is passed directly — works at any depth (ROOT/GROUP/LEAF).
  // The backend resolves it to a categorySlugPath term filter in Elasticsearch.
  const searchParams2: SearchParams = {
    query,
    categorySlug: category,
    sizes,
    colors,
    sort,
    page,
    size: 24,
    minPrice,
    maxPrice,
  };
  const { data, isLoading } = useSearch(searchParams2);

  const facets = data?.facets;
  const priceStats = facets?.priceStats ?? { min: 0, max: 0, avg: 0 };
  // Effective price range for slider (falls back to facet min/max)
  const effectivePriceRange: [number, number] = localPriceRange ?? [
    minPrice ?? Math.floor(priceStats.min),
    maxPrice ?? Math.ceil(priceStats.max),
  ];

  // ── URL helpers ────────────────────────────────────────────────────────────
  const setParam = useCallback((key: string, value: string | undefined) => {
    setSearchParams(prev => {
      if (value) prev.set(key, value);
      else prev.delete(key);
      prev.set('page', '1');
      return prev;
    });
  }, [setSearchParams]);

  const handleSizeChange = (size: string, checked: boolean) => {
    const next = checked ? [...sizes, size] : sizes.filter(s => s !== size);
    setParam('sizes', next.join(',') || undefined);
  };

  const handleColorChange = (color: string, checked: boolean) => {
    const next = checked ? [...colors, color] : colors.filter(c => c !== color);
    setParam('colors', next.join(',') || undefined);
  };

  const handlePriceCommit = (_: unknown, value: number | number[]) => {
    const [lo, hi] = value as number[];
    setLocalPriceRange(null);
    setSearchParams(prev => {
      prev.set('minPrice', String(lo));
      prev.set('maxPrice', String(hi));
      prev.set('page', '1');
      return prev;
    });
  };

  const handleClearFilters = () => {
    setLocalPriceRange(null);
    setSearchParams(prev => {
      prev.delete('sizes');
      prev.delete('colors');
      prev.delete('minPrice');
      prev.delete('maxPrice');
      prev.set('page', '1');
      return prev;
    });
  };

  const handleSortChange = (e: any) =>
    setParam('sort', e.target.value);

  const handlePageChange = (_: unknown, value: number) => {
    setSearchParams(prev => { prev.set('page', value.toString()); return prev; });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const activeFilterCount =
    sizes.length +
    colors.length +
    (minPrice !== undefined ? 1 : 0) +
    (maxPrice !== undefined ? 1 : 0);

  const filterPanelProps: FilterPanelProps = {
    sizes: facets?.sizes ?? [],
    colors: facets?.colors ?? [],
    priceMin: priceStats.min,
    priceMax: priceStats.max,
    selectedSizes: sizes,
    selectedColors: colors,
    selectedPriceRange: effectivePriceRange,
    onSizeChange: handleSizeChange,
    onColorChange: handleColorChange,
    onPriceChange: (range) => setLocalPriceRange(range),
    onClear: handleClearFilters,
    activeFilterCount,
  };

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <Container maxWidth={false} sx={{ py: { xs: 4, md: 8 }, px: { xs: 2, md: 6, lg: 10 } }}>

      {/* ── Fallback Mode Banner ───────────────────────────────────────────── */}
      {data?.fallbackMode && (
        <Alert severity="info" sx={{ mb: 3, borderRadius: 0, fontSize: '0.82rem' }}>
          Search is currently running in limited mode (Elasticsearch is unavailable).
          Filters and facets may not be available. Results are from the main catalog.
        </Alert>
      )}

      {/* ── Page Header ─────────────────────────────────── */}
      <Box component="header" sx={{ mb: 8, borderBottom: (theme) => `1px solid ${theme.palette.border.default}`, pb: 5 }}>
        <Typography
          variant="overline"
          sx={{
            fontFamily: (theme) => theme.typography.fontFamilyUtility,
            fontWeight: 700,
            fontSize: '0.65rem',
            letterSpacing: '0.25em',
            color: 'text.secondary',
            display: 'block',
            mb: 2,
          }}
        >
          EGO ARCHIVE
        </Typography>
        <Typography
          variant="h1"
          sx={{
            fontFamily: (theme) => theme.typography.fontFamilyDisplay,
            fontWeight: 700,
            fontSize: { xs: '2.5rem', md: '4rem' },
            color: 'text.primary',
            lineHeight: 1,
            textTransform: 'uppercase',
            mb: 3,
          }}
        >
          {category ? category.replace(/-/g, ' ') : 'The Collection'}
        </Typography>
        <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', maxWidth: '640px', fontStyle: 'italic', }}>
          An exploration of form and void. Luxury streetwear engineered for the modern dystopian landscape.
        </Typography>
      </Box>

      {/* ── Two-column layout ─────────────────────────────────────────────── */}
      <Grid container spacing={{ xs: 0, md: 4 }}>

        {/* Desktop filter sidebar */}
        {!isMobile && (
          <Grid size={{ xs: 12, md: 3 }} sx={{ position: 'sticky', top: 100, alignSelf: 'flex-start' }}>
            <FilterPanel {...filterPanelProps} onPriceChange={(r) => {
              setLocalPriceRange(r);
              handlePriceCommit(null, r);
            }} />
          </Grid>
        )}

        {/* Product grid */}
        <Grid size={{ xs: 12, md: 9 }} sx={{ minWidth: 0 }}>
          {isLoading ? (
            <ProductGridSkeleton count={12} />
          ) : data?.content.length === 0 ? (
            <Box sx={{ textAlign: 'center', py: 10 }}>
              <Typography variant="h6" color="text.secondary">
                No products found.
              </Typography>
              {activeFilterCount > 0 && (
                <Button onClick={handleClearFilters} sx={{ mt: 2 }}>Clear all filters</Button>
              )}
            </Box>
          ) : (
            <>
              {/* Result count & Sort */}
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4, flexWrap: 'wrap', gap: 2 }}>
                <Typography variant="body2" color="text.secondary">
                  {data?.totalElements ?? 0} product{(data?.totalElements ?? 0) !== 1 ? 's' : ''}
                </Typography>
                
                {/* Moved Sort bar here, opposite to result count */}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  {isMobile && (
                    <Button
                      startIcon={<Badge badgeContent={activeFilterCount} color="primary" showZero={false}><FilterListIcon /></Badge>}
                      variant="outlined" size="small" onClick={() => setMobileFilterOpen(true)}
                      sx={{ textTransform: 'none' }}
                    >
                      Filters
                    </Button>
                  )}
                  <FormControl size="small" sx={{ minWidth: 190 }}>
                    <Select value={sort} onChange={handleSortChange} displayEmpty>
                      <MenuItem value="createdAt,desc">Newest Arrivals</MenuItem>
                      <MenuItem value="minPrice,asc">Price: Low to High</MenuItem>
                      <MenuItem value="minPrice,desc">Price: High to Low</MenuItem>
                      <MenuItem value="avgRating,desc">Top Rated</MenuItem>
                    </Select>
                  </FormControl>
                </Box>
              </Box>

              <Grid container spacing={3}>
                {data?.content.map((product) => (
                  <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={product.id}>
                    <ProductCard product={product} />
                  </Grid>
                ))}
              </Grid>

              {data && data.totalPages > 1 && (
                <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
                  <Pagination
                    count={data.totalPages}
                    page={page + 1}
                    onChange={handlePageChange}
                    shape="rounded"
                    size="large"
                  />
                </Box>
              )}
            </>
          )}
        </Grid>
      </Grid>

      {/* ── Mobile Filter Drawer ───────────────────────────────────────────── */}
      <Drawer
        anchor="bottom"
        open={mobileFilterOpen}
        onClose={() => setMobileFilterOpen(false)}
        slotProps={{
          paper: {
            sx: {
              borderTopLeftRadius: 0,
              borderTopRightRadius: 0,
              maxHeight: '85vh',
              p: 3,
              overflowY: 'auto',
            }
          }
        }}
      >
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
          <Typography variant="metadata" >
            Filters
          </Typography>
          <IconButton onClick={() => setMobileFilterOpen(false)}>
            <CloseIcon />
          </IconButton>
        </Box>
        <FilterPanel {...filterPanelProps} onPriceChange={(r) => {
          setLocalPriceRange(r);
          handlePriceCommit(null, r);
        }} />
        <Box sx={{ pt: 2, pb: 1 }}>
          <Button
            fullWidth
            variant="contained"
            onClick={() => setMobileFilterOpen(false)}
            sx={{ borderRadius: 0, py: 1.5, fontWeight: 700, letterSpacing: '0.08em' }}
          >
            See Results ({data?.totalElements ?? 0})
          </Button>
        </Box>
      </Drawer>
    </Container>
  );
};

export default ProductListingPage;
