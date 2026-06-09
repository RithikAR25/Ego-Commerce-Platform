package com.ego.raw_ego.catalog.dto.response;

import com.ego.raw_ego.catalog.entity.AttributeType;
import com.ego.raw_ego.catalog.entity.AttributeValue;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Full attribute type response used in admin attribute management.
 *
 * <p>Includes all values under the type so the admin UI can render
 * the full attribute matrix (e.g. Color → [Black, White, Red]).
 */
@Data
@Builder
public class AttributeTypeDetailResponse {

    private Long id;
    private String name;
    private Integer displayOrder;
    private List<AttributeValueDetailResponse> values;

    public static AttributeTypeDetailResponse from(AttributeType type) {
        return AttributeTypeDetailResponse.builder()
                .id(type.getId())
                .name(type.getName())
                .displayOrder(type.getDisplayOrder())
                .values(type.getValues().stream()
                        .map(AttributeValueDetailResponse::from)
                        .collect(Collectors.toList()))
                .build();
    }

    @Data
    @Builder
    public static class AttributeValueDetailResponse {
        private Long id;
        private String value;
        private String code;
        private Integer displayOrder;
        private String hexColor;
        private String swatchImageUrl;

        public static AttributeValueDetailResponse from(AttributeValue av) {
            return AttributeValueDetailResponse.builder()
                    .id(av.getId())
                    .value(av.getValue())
                    .code(av.getCode())
                    .displayOrder(av.getDisplayOrder())
                    .hexColor(av.getHexColor())
                    .swatchImageUrl(av.getSwatchImageUrl())
                    .build();
        }
    }
}
