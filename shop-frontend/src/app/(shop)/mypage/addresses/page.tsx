import { redirect } from "next/navigation";
import { AddressBookClient } from "@/components/address/AddressBookClient";
import { backendFetch } from "@/lib/api/server";

export default async function AddressBookPage() {
  const me = await backendFetch("/members/me");
  if (me.status === 401) {
    redirect("/login?next=/mypage/addresses");
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="heading-ko text-2xl text-foreground">배송지 관리</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          기본 배송지는 주문서에서 자동으로 선택됩니다. 최대 10개까지 저장할 수 있습니다.
        </p>
      </div>
      <AddressBookClient />
    </div>
  );
}
