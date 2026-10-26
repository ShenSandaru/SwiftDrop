import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";

export default function TrackPage() {
  return (
    <main className="container mx-auto flex-1 p-6 max-w-2xl">
      <Card>
        <CardHeader>
          <CardTitle>Public Parcel Tracking</CardTitle>
          <CardDescription>
            Island-wide real-time tracking for customers and recipients (WS10).
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
              />
            </div>
            <Button type="submit">Track Parcel</Button>
          </form>
          <p className="text-xs text-muted-foreground">
            Enter your tracking reference to check pickup, transit, and delivery status.
          </p>
        </CardContent>
      </Card>
    </main>
  );
}

