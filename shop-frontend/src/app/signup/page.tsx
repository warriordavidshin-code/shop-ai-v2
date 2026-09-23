import { BrandLogo } from "@/components/brand/BrandLogo";
import { SignupForm } from "@/components/auth/SignupForm";

export default function SignupPage() {
  return (
    <main className="min-h-screen bg-background">
      <div className="mx-auto max-w-[720px] px-4 py-12 md:px-8">
        <div className="mb-6">
          <BrandLogo size="auth" priority />
        </div>
        <h1 className="mb-8 text-3xl font-semibold text-foreground">회원가입</h1>
        <SignupForm />
      </div>
    </main>
  );
}
