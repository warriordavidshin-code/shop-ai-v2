import { z } from "zod";

export const productSummarySchema = z.object({
  productId: z.number(),
  productName: z.string(),
  brandName: z.string(),
  normalPrice: z.coerce.number(),
  salePrice: z.coerce.number(),
  discountRate: z.number(),
  mainImageUrl: z.string().nullable().optional(),
  imageUrls: z.array(z.string()).optional().default([]),
  colorCount: z.number(),
  soldOut: z.boolean(),
  wishlisted: z.boolean(),
});

export type ProductSummary = z.infer<typeof productSummarySchema>;

export const pageResponseSchema = <T extends z.ZodTypeAny>(item: T) =>
  z.object({
    content: z.array(item),
    page: z.number(),
    size: z.number(),
    totalElements: z.number(),
    totalPages: z.number(),
  });

export const productDetailSchema = z.object({
  productId: z.number(),
  categoryId: z.number(),
  productName: z.string(),
  brandName: z.string(),
  summary: z.string().nullable().optional(),
  description: z.string().nullable().optional(),
  normalPrice: z.coerce.number(),
  salePrice: z.coerce.number(),
  discountRate: z.number(),
  status: z.string(),
  fitType: z.string().nullable().optional(),
  material: z.string().nullable().optional(),
  thickness: z.string().nullable().optional(),
  stretch: z.string().nullable().optional(),
  seeThrough: z.string().nullable().optional(),
  season: z.string().nullable().optional(),
  soldOut: z.boolean(),
  wishlisted: z.boolean(),
  images: z.array(
    z.object({
      imageId: z.number(),
      imageUrl: z.string().nullable().optional(),
      imageType: z.string(),
      altText: z.string().nullable().optional(),
      sortOrder: z.number(),
    }),
  ),
  skus: z.array(
    z.object({
      skuId: z.number(),
      skuCode: z.string(),
      color: z.string(),
      size: z.string(),
      additionalPrice: z.coerce.number(),
      status: z.string(),
      availableQuantity: z.number(),
    }),
  ),
  measurements: z.array(
    z.object({
      measurementId: z.number(),
      size: z.string(),
      shoulder: z.coerce.number().nullable().optional(),
      chest: z.coerce.number().nullable().optional(),
      waist: z.coerce.number().nullable().optional(),
      hip: z.coerce.number().nullable().optional(),
      sleeve: z.coerce.number().nullable().optional(),
      totalLength: z.coerce.number().nullable().optional(),
      rise: z.coerce.number().nullable().optional(),
      thigh: z.coerce.number().nullable().optional(),
      hem: z.coerce.number().nullable().optional(),
    }),
  ),
});

export type ProductDetail = z.infer<typeof productDetailSchema>;

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

export async function fetchProducts(searchParams: Record<string, string | undefined>) {
  const qs = new URLSearchParams();
  Object.entries(searchParams).forEach(([k, v]) => {
    if (v) qs.set(k, v);
  });
  const response = await fetch(`${BACKEND_URL}/api/products?${qs}`, { next: { revalidate: 30 } });
  if (!response.ok) {
    throw new Error("상품 목록을 불러오지 못했습니다.");
  }
  return pageResponseSchema(productSummarySchema).parse(await response.json());
}

export async function fetchProduct(productId: string) {
  const response = await fetch(`${BACKEND_URL}/api/products/${productId}`, {
    next: { revalidate: 30 },
  });
  if (!response.ok) {
    return null;
  }
  return productDetailSchema.parse(await response.json());
}

export async function fetchNewProducts() {
  const response = await fetch(`${BACKEND_URL}/api/products/new`, { next: { revalidate: 60 } });
  if (!response.ok) {
    return [] as ProductSummary[];
  }
  return z.array(productSummarySchema).parse(await response.json());
}

export async function fetchBestProducts() {
  const response = await fetch(`${BACKEND_URL}/api/products/best`, { next: { revalidate: 60 } });
  if (!response.ok) {
    return [] as ProductSummary[];
  }
  return z.array(productSummarySchema).parse(await response.json());
}
