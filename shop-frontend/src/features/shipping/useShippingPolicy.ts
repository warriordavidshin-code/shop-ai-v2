"use client";

import { useEffect, useState } from "react";
import { DEFAULT_SHIPPING_POLICY, type ShippingPolicy } from "@/features/orders/delivery";
import { getShippingPolicyOrDefault } from "@/features/shipping/api";

let cached: Promise<ShippingPolicy> | null = null;

/** Live shipping policy for client components; starts with the default values. */
export function useShippingPolicy(): ShippingPolicy {
  const [policy, setPolicy] = useState<ShippingPolicy>(DEFAULT_SHIPPING_POLICY);
  useEffect(() => {
    let active = true;
    cached ??= getShippingPolicyOrDefault();
    void cached.then((value) => {
      if (active) setPolicy(value);
    });
    return () => {
      active = false;
    };
  }, []);
  return policy;
}
