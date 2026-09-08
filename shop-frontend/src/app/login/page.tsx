import { Suspense } from "react";
import { LoginForm } from "@/components/auth/LoginForm";

export default function LoginPage() {
  return (
    <main className="min-h-screen bg-background">
      <div className="mx-auto grid min-h-screen max-w-[1280px] lg:grid-cols-2">
        <section className="flex flex-col justify-center px-4 py-12 md:px-8 lg:px-12">
          <p className="mb-2 text-sm tracking-wide text-muted-foreground">BoutiqueCamel</p>
          <h1 className="mb-6 text-3xl font-semibold text-foreground">로그인</h1>
          <Suspense fallback={<div className="h-64 animate-pulse rounded-xl bg-surface-soft" />}>
            <LoginForm />
          </Suspense>
        </section>
        <aside
          className="hidden min-h-[220px] bg-surface-warm lg:block lg:min-h-full"
          aria-hidden
        />
      </div>
    </main>
  );
}
