import { AssistantWidget } from "@/components/assistant/assistant-widget";
import { Hero } from "@/components/landing/hero";
import { BoardingPass } from "@/components/landing/boarding-pass";
import { Journey } from "@/components/landing/journey";
import { Stamps } from "@/components/landing/stamps";
import { Faq } from "@/components/landing/faq";
import { BusinessResources } from "@/components/landing/business-resources";
import { Closing } from "@/components/landing/closing";

/** The public landing page. The portal quick links moved to the temporary `/portals` page. */
export default function Home() {
  return (
    <main className="flex-1">
      <Hero />
      <BoardingPass />
      <Journey />
      <Stamps />
      <Faq />
      <BusinessResources />
      <Closing />
      <AssistantWidget />
    </main>
  );
}
