package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.request.ImageUploadRequest;
import com.ego.raw_ego.catalog.dto.request.ReorderImagesRequest;
import com.ego.raw_ego.catalog.dto.response.ImageResponse;
import com.ego.raw_ego.catalog.entity.Product;
import com.ego.raw_ego.catalog.entity.ProductImage;
import com.ego.raw_ego.catalog.repository.ProductImageRepository;
import com.ego.raw_ego.catalog.repository.ProductRepository;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ProductImageService} — product-level gallery image management.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository      productRepository;
    private final CloudinaryService      cloudinaryService;

    @Override
    @Transactional(readOnly = true)
    public List<ImageResponse> getProductImages(Long productId) {
        ensureProductExists(productId);
        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(img -> ImageResponse.fromProductImage(
                        img,
                        cloudinaryService.buildTransformationUrls(img.getCloudinaryPublicId())
                ))
                .collect(Collectors.toList());
    }

    @Override
    public ImageResponse uploadAndSaveProductImage(Long productId, MultipartFile file,
                                                   ImageUploadRequest request) {
        Map<String, Object> uploadResult = cloudinaryService.uploadProductGalleryImage(file, productId);
        String publicId  = (String) uploadResult.get("public_id");
        String secureUrl = (String) uploadResult.get("secure_url");
        return persistProductImage(productId, publicId, secureUrl, request);
    }

    @Override
    @Transactional
    public ImageResponse persistProductImage(Long productId, String publicId,
                                             String secureUrl, ImageUploadRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: id=" + productId));

        int displayOrder = resolveDisplayOrder(request, productId);
        String altText   = request != null && request.getAltText() != null
                ? request.getAltText().trim() : "";

        ProductImage image = ProductImage.builder()
                .product(product)
                .cloudinaryPublicId(publicId)
                .url(secureUrl)
                .altText(altText)
                .displayOrder(displayOrder)
                .build();

        image = productImageRepository.save(image);
        log.info("Product gallery image saved: id={} productId={} publicId={} displayOrder={}",
                image.getId(), productId, publicId, displayOrder);

        return ImageResponse.fromProductImage(
                image,
                cloudinaryService.buildTransformationUrls(publicId)
        );
    }

    @Override
    public void deleteProductImage(Long productId, Long imageId) {
        String publicId = deleteProductImageFromDb(productId, imageId);
        cloudinaryService.deleteImage(publicId);
    }

    @Override
    @Transactional
    public String deleteProductImageFromDb(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found: id=" + imageId));

        if (!image.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException(
                    "Image id=" + imageId + " does not belong to product id=" + productId);
        }

        String publicId = image.getCloudinaryPublicId();
        productImageRepository.delete(image);
        log.info("Product gallery image deleted from DB: id={} productId={}", imageId, productId);
        return publicId;
    }

    @Override
    @Transactional
    public List<ImageResponse> reorderProductImages(Long productId, ReorderImagesRequest reorderRequest) {
        ensureProductExists(productId);

        List<ProductImage> existing = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        var existingIds = existing.stream().map(ProductImage::getId).collect(Collectors.toSet());

        for (var entry : reorderRequest.getOrder()) {
            if (!existingIds.contains(entry.getImageId())) {
                throw new IllegalArgumentException(
                        "Image id=" + entry.getImageId() + " does not belong to product id=" + productId);
            }
        }

        for (var entry : reorderRequest.getOrder()) {
            productImageRepository.updateDisplayOrder(entry.getImageId(), entry.getDisplayOrder());
        }

        log.info("Reordered {} images for product={}", reorderRequest.getOrder().size(), productId);

        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(img -> ImageResponse.fromProductImage(
                        img,
                        cloudinaryService.buildTransformationUrls(img.getCloudinaryPublicId())
                ))
                .collect(Collectors.toList());
    }

    private void ensureProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: id=" + productId);
        }
    }

    private int resolveDisplayOrder(ImageUploadRequest request, Long productId) {
        if (request != null && request.getDisplayOrder() != null) {
            return request.getDisplayOrder();
        }
        return (int) productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId).size();
    }
}
