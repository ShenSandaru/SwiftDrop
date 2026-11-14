"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";

export default function CustomerProfilePage() {
  const router = useRouter();

  useEffect(() => {
    router.replace("/profile");
  }, [router]);

  return (
    <div className="flex flex-1 items-center justify-center p-12">
      <Loader2 className="size-8 animate-spin text-primary" aria-label="Redirecting to profile" />
    </div>
  );
}
