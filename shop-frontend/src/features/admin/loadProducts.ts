import { backendFetch } from "@/lib/api/server";
import { adminProductPageSchema, type AdminProductPage } from "@/features/admin/products";

export const ADMIN_PRODUCT_PAGE_SIZE = 30;

/** Returns null when the viewer is not an authenticated admin. */
export async function loadAdminProductPage(page: number): Promise<AdminProductPage | null> {
  const safePage = Number.isFinite(page) && page > 0 ? Math.floor(page) : 0;
  const response = await backendFetch(`/admin/products?page=${safePage}&size=${ADMIN_PRODUCT_PAGE_SIZE}`);
  if (response.status === 401 || response.status === 403) {
    return null;
  }
  if (!response.ok) {
    return { content: [], page: safePage, size: ADMIN_PRODUCT_PAGE_SIZE, totalElements: 0, totalPages: 0 };
  }
  return adminProductPageSchema.parse(await response.json());
}
