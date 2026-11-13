"use client";

import Image from "next/image";
import Link from "next/link";
import { ArrowRight, Building2, UserPlus, LogIn } from "lucide-react";
import { Reveal } from "./reveal";

interface BusinessResource {
  id: string;
  tag: string;
  title: string;
  description: string;
  ctaText: string;
  href: string;
  imageSrc: string;
  imageAlt: string;
  icon: typeof Building2;
}

const RESOURCES: BusinessResource[] = [
  {
    id: "overview",
    tag: "Services & Overview",
    title: "New to SwiftDrop?",
    description: "Discover how SwiftDrop helps businesses manage and deliver their parcels.",
    ctaText: "Learn about merchant services",
    href: "/signup/merchant",
    imageSrc: "/images/business-shipping.png",
    imageAlt: "Business owner packaging parcels for dispatch",
    icon: Building2,
  },
  {
    id: "signup",
    tag: "Direct Onboarding",
    title: "Create a Business Account",
    description: "Register your business and apply to become a SwiftDrop merchant.",
    ctaText: "Apply as a merchant",
    href: "/signup/merchant",
    imageSrc: "/images/business-account.png",
    imageAlt: "Merchant shipping account dashboard on digital tablet",
    icon: UserPlus,
  },
  {
    id: "login",
    tag: "Merchant Workspace",
    title: "Already a Partner?",
    description: "Access your merchant workspace and manage your deliveries.",
    ctaText: "Sign in to merchant portal",
    href: "/login?redirect=/merchant",
    imageSrc: "/images/business-partner.png",
    imageAlt: "SwiftDrop courier delivering packages to a partner storefront",
    icon: LogIn,
  },
];

/**
 * Business Resources section on the landing page, inspired by FedEx's business tools layout.
 * Showcases SwiftDrop's merchant capabilities, direct business registration, and partner portal access.
 */
export function BusinessResources() {
  return (
    <section className="border-t border-border bg-card/60 py-20 sm:py-24 md:py-28" aria-labelledby="business-resources-heading">
      <div className="container mx-auto max-w-6xl px-4 sm:px-6">
        <Reveal className="mx-auto max-w-3xl space-y-3 text-center">
          <div className="inline-flex items-center gap-1.5 rounded-full border border-primary/20 bg-accent px-3 py-1 text-xs font-semibold uppercase tracking-wider text-primary">
            <span className="size-1.5 rounded-full bg-primary" aria-hidden="true" />
            Merchant Solutions
          </div>
          <h2 id="business-resources-heading" className="text-3xl font-extrabold tracking-tight text-foreground sm:text-4xl md:text-5xl">
            Grow Your Business with SwiftDrop
          </h2>
          <p className="text-base sm:text-lg text-muted-foreground leading-relaxed max-w-2xl mx-auto">
            Shipping solutions designed to help your business deliver more.
          </p>
        </Reveal>

        <div className="mt-14 sm:mt-16 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 lg:gap-8">
          {RESOURCES.map((resource, index) => {
            const Icon = resource.icon;
            return (
              <Reveal
                key={resource.id}
                delay={index * 0.1}
                className="group flex flex-col overflow-hidden rounded-2xl border border-border bg-card shadow-sm transition-all duration-300 hover:shadow-lg hover:border-primary/40 hover:-translate-y-1"
              >
                {/* Top Media Container */}
                <div className="relative aspect-[16/10] w-full overflow-hidden bg-muted">
                  <Image
                    src={resource.imageSrc}
                    alt={resource.imageAlt}
                    fill
                    sizes="(max-width: 768px) 100vw, (max-width: 1024px) 50vw, 33vw"
                    className="object-cover transition-transform duration-500 ease-out group-hover:scale-105"
                    priority={false}
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-transparent opacity-60" />
                  <div className="absolute bottom-3 left-3 inline-flex items-center gap-1.5 rounded-md bg-background/90 backdrop-blur-sm px-2.5 py-1 text-xs font-semibold text-foreground shadow-sm">
                    <Icon className="size-3.5 text-primary" aria-hidden="true" />
                    <span>{resource.tag}</span>
                  </div>
                </div>

                {/* Card Body */}
                <div className="flex flex-1 flex-col justify-between p-6 sm:p-7 space-y-6">
                  <div className="space-y-2.5">
                    <h3 className="text-xl font-bold tracking-tight text-foreground group-hover:text-primary transition-colors">
                      {resource.title}
                    </h3>
                    <p className="text-sm text-muted-foreground leading-relaxed">
                      {resource.description}
                    </p>
                  </div>

                  {/* Text CTA Link */}
                  <div className="pt-2 border-t border-border/50">
                    <Link
                      href={resource.href}
                      className="inline-flex items-center gap-1.5 text-sm font-semibold text-primary underline-offset-4 hover:underline transition-colors"
                    >
                      <span>{resource.ctaText}</span>
                      <ArrowRight className="size-4 transition-transform group-hover:translate-x-1" aria-hidden="true" />
                    </Link>
                  </div>
                </div>
              </Reveal>
            );
          })}
        </div>
      </div>
    </section>
  );
}
