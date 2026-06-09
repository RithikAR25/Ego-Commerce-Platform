/**
 * review.types.ts
 *
 * TypeScript interfaces for the product reviews domain.
 */

export interface ProductReview {
  id:                number;
  rating:            number; // 1-5
  title:             string | null;
  body:              string | null;
  reviewerFirstName: string;
  createdAt:         string;
}

export interface ProductRatingSummary {
  averageRating?:   number | null;
  reviewCount:      number;
  ratingBreakdown:  Record<number, number>; // { 1: 0, 2: 5, 3: 10, 4: 20, 5: 50 }
}

export interface CreateReviewPayload {
  rating: number; // 1-5
  title?: string;
  body?:  string;
}

// Same as SpringPage
export interface ReviewPageResponse {
  content:          ProductReview[];
  totalElements:    number;
  totalPages:       number;
  number:           number;
  size:             number;
  first:            boolean;
  last:             boolean;
  empty:            boolean;
}
