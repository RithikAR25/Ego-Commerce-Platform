/**
 * image.types.ts
 *
 * Mirrors the backend ImageResponse and Transformations DTOs.
 */

export interface ImageTransformations {
  thumbnail: string;
  card:      string;
  detail:    string;
  zoom:      string;
}

export interface ImageResponse {
  id:                 number;
  url:                string;
  cloudinaryPublicId: string;
  altText:            string;
  primary:            boolean;
  displayOrder:       number;
  transformations?:   ImageTransformations;
}

export interface ReorderItem {
  imageId:      number;
  displayOrder: number;
}
