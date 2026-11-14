"use client";

import React, { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

interface ApprovalGateProps {
  children: React.ReactNode;
}

export function ApprovalGate({ children }: ApprovalGateProps) {
  const pathname = usePathname();
  const router = useRouter();
  const { token, isLoading: isAuthLoading } = useAuth();

  const [isChecking, setIsChecking] = useState(true);
  const [isApproved, setIsApproved] = useState(false);

  const isExemptPage = pathname === "/merchant/status" || pathname === "/merchant/profile";

  useEffect(() => {
    // Status and profile pages are never blocked by the approval gate
    if (isExemptPage) {
      return;
    }

    if (isAuthLoading) return;

    if (!token) {
      router.replace("/login");
      return;
    }

    let isMounted = true;

    api
      .get<{ status: string }>("/api/v1/merchants/me", { token })
      .then((res) => {
        if (!isMounted) return;
        if (res.status === "APPROVED") {
          setIsApproved(true);
          setIsChecking(false);
        } else {
          router.replace("/merchant/status");
        }
      })
      .catch(() => {
        if (!isMounted) return;
        router.replace("/merchant/status");
      });

    return () => {
      isMounted = false;
    };
  }, [pathname, isExemptPage, token, isAuthLoading, router]);

  if (isExemptPage) {
    return <>{children}</>;
  }

  if (isChecking || !isApproved) {
    return (
      <div className="flex items-center justify-center min-h-[40vh]">
        <div className="flex flex-col items-center gap-3 text-muted-foreground">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <p className="text-xs">Verifying merchant approval status...</p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
