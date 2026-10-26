import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";

export default function TrackPage() {
  return (
    <main className="container mx-auto flex-1 px-4 py-12 max-w-2xl space-y-4">
      <div className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-accent px-3 py-1 text-xs font-semibold text-primary">
        <span className="size-1.5 rounded-full bg-primary" />
        Live Tracking · WS10
      </div>

      <Card className="rounded-xl bg-card border-border shadow-xs">
        <CardHeader>
          <CardTitle className="text-xl font-bold text-foreground">Public Parcel Tracking</CardTitle>
          <CardDescription className="text-sm text-muted-foreground">
            Island-wide real-time tracking for customers and recipients.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <form action="/track" method="GET" className="flex flex-col sm:flex-row gap-2">
            <div className="flex-1 space-y-1">
              <Label htmlFor="trackingNumber" className="sr-only">
                Tracking Number
              </Label>
              <Input
                id="trackingNumber"
                name="code"
                placeholder="Enter tracking number (e.g. SWD-12345678)"
                aria-label="Tracking Number"
                className="bg-background text-sm"
              />
            </div>
            <Button type="submit" className="font-semibold shadow-xs">
              Track Parcel
            </Button>
          </form>
          <p className="text-xs text-muted-foreground">
            Enter your tracking reference to check pickup, transit, and delivery status in real time.
          </p>
        </CardContent>
      </Card>
    </main>
  );
}

