package com.frietsync.backend.dto.community;

import java.util.List;

public record PostPageResponse(
        List<PostResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}