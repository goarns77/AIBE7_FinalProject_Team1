package org.example.springtestci.product.dto;

import java.util.List;

public record ProductPageResponse(List<ProductResponse> items, Page page) {
  public record Page(int number, int size, long totalElements, long totalPages, boolean hasNext) {}
}
