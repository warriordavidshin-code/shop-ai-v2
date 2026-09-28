import { z } from "zod";
import { parseApiError, shopFetch } from "@/lib/api/client";
import { memberSchema, type Member } from "@/features/auth/schemas";

export const memberAddressSchema = z.object({
  addressId: z.number(),
  label: z.string(),
  receiverName: z.string(),
  receiverPhone: z.string(),
  postcode: z.string(),
  address1: z.string(),
  address2: z.string().nullable().optional(),
  defaultAddress: z.boolean(),
  updatedAt: z.string().nullable().optional(),
});

export type MemberAddress = z.infer<typeof memberAddressSchema>;

export const addressFormSchema = z.object({
  label: z.string().trim().min(1, "배송지 이름을 입력해 주세요.").max(50),
  receiverName: z.string().trim().min(1, "받는 분을 입력해 주세요.").max(100),
  receiverPhone: z
    .string()
    .trim()
    .regex(/^[0-9+\-\s]{9,32}$/, "연락처를 올바르게 입력해 주세요."),
  postcode: z.string().trim().min(1, "우편번호 찾기로 주소를 입력해 주세요.").max(16),
  address1: z.string().trim().min(1, "기본주소를 입력해 주세요.").max(255),
  address2: z.string().max(255).optional(),
  defaultAddress: z.boolean(),
});

export type AddressFormValues = z.infer<typeof addressFormSchema>;

export const reauthStatusSchema = z.object({
  verified: z.boolean(),
  expiresAt: z.string().nullable().optional(),
  method: z.enum(["LOCAL", "KAKAO", "NAVER"]).nullable().optional(),
});

export type ReauthStatus = z.infer<typeof reauthStatusSchema>;

export function formatAddressLine(address: Pick<MemberAddress, "postcode" | "address1" | "address2">) {
  return `(${address.postcode}) ${address.address1}${address.address2 ? ` ${address.address2}` : ""}`;
}

export async function listAddresses(): Promise<MemberAddress[]> {
  const response = await shopFetch("/members/me/addresses");
  if (!response.ok) throw await parseApiError(response);
  return z.array(memberAddressSchema).parse(await response.json());
}

export async function createAddress(values: AddressFormValues): Promise<MemberAddress> {
  const response = await shopFetch("/members/me/addresses", {
    method: "POST",
    body: JSON.stringify(values),
  });
  if (!response.ok) throw await parseApiError(response);
  return memberAddressSchema.parse(await response.json());
}

export async function updateAddress(addressId: number, values: AddressFormValues): Promise<MemberAddress> {
  const response = await shopFetch(`/members/me/addresses/${addressId}`, {
    method: "PUT",
    body: JSON.stringify(values),
  });
  if (!response.ok) throw await parseApiError(response);
  return memberAddressSchema.parse(await response.json());
}

export async function setDefaultAddress(addressId: number): Promise<MemberAddress> {
  const response = await shopFetch(`/members/me/addresses/${addressId}/default`, { method: "POST" });
  if (!response.ok) throw await parseApiError(response);
  return memberAddressSchema.parse(await response.json());
}

export async function deleteAddress(addressId: number): Promise<void> {
  const response = await shopFetch(`/members/me/addresses/${addressId}`, { method: "DELETE" });
  if (!response.ok) throw await parseApiError(response);
}

export async function getReauthStatus(): Promise<ReauthStatus> {
  const response = await shopFetch("/members/me/reauth");
  if (!response.ok) throw await parseApiError(response);
  return reauthStatusSchema.parse(await response.json());
}

export async function verifyPassword(password: string): Promise<ReauthStatus> {
  const response = await shopFetch("/members/me/reauth", {
    method: "POST",
    body: JSON.stringify({ password }),
  });
  if (!response.ok) throw await parseApiError(response);
  return reauthStatusSchema.parse(await response.json());
}

export async function clearReauth(): Promise<void> {
  try {
    await shopFetch("/members/me/reauth", { method: "DELETE" });
  } catch {
    // The cookie expires on its own; nothing to recover here.
  }
}

export function socialReauthUrl(provider: "KAKAO" | "NAVER", redirect = "/mypage/profile") {
  return `/api/shop/auth/${provider.toLowerCase()}/reauth?redirect=${encodeURIComponent(redirect)}`;
}

export type ProfileUpdateInput = {
  name: string;
  birthDate?: string | null;
  gender?: Member["gender"];
  phone?: string;
};

export async function updateProfile(input: ProfileUpdateInput): Promise<Member> {
  const response = await shopFetch("/members/me", {
    method: "PATCH",
    body: JSON.stringify({
      name: input.name,
      birthDate: input.birthDate || null,
      gender: input.gender ?? null,
      phone: input.phone ?? null,
    }),
  });
  if (!response.ok) throw await parseApiError(response);
  return memberSchema.parse(await response.json());
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  const response = await shopFetch("/members/me/password", {
    method: "PATCH",
    body: JSON.stringify({ currentPassword, newPassword }),
  });
  if (!response.ok) throw await parseApiError(response);
}
