import { DEFAULT_SHIPPING_POLICY, type ShippingPolicy } from "@/features/orders/delivery";
import { shippingPolicySchema } from "@/features/shipping/api";
import { backendFetch } from "@/lib/api/server";

/** Server-component variant of the shipping policy lookup; never throws. */
export async function fetchShippingPolicyOnServer(): Promise<ShippingPolicy> {
  try {
    const response = await backendFetch("/shipping/policy");
    if (!response.ok) return DEFAULT_SHIPPING_POLICY;
    return shippingPolicySchema.parse(await response.json());
  } catch {
    return DEFAULT_SHIPPING_POLICY;
  }
}
