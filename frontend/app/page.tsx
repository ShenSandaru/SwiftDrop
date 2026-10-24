import { Button } from "@/components/ui/button";

export default function Home() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 p-8 text-center">
      <h1 className="text-4xl font-semibold tracking-tight">SwiftDrop</h1>
      <p className="max-w-md text-muted-foreground">
        Parcel delivery and courier management. The frontend scaffold is ready.
      </p>
      <Button disabled>Coming soon</Button>
    </main>
  );
}
