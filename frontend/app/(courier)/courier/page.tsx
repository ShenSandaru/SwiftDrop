import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export default function CourierPortalPage() {
  return (
    <main className="container mx-auto flex-1 p-6 max-w-4xl">
      <Card>
        <CardHeader>
          <CardTitle>Courier Portal</CardTitle>
          <CardDescription>
            Assigned deliveries, status updates, and proof of delivery (WS6).
          </CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            This route group is reserved for courier operations.
          </p>
        </CardContent>
      </Card>
    </main>
  );
}
