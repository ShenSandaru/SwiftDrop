import { Hero } from "@/components/landing/hero";
import { BoardingPass } from "@/components/landing/boarding-pass";
import { Journey } from "@/components/landing/journey";
import { Stamps } from "@/components/landing/stamps";
import { Faq } from "@/components/landing/faq";
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
      <Closing />
    </main>
  );
}
