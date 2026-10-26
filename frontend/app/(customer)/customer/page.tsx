import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export default function CustomerPortalPage() {
  return (
    <main className="container mx-auto flex-1 p-6 max-w-4xl">
      <Card>
        <CardHeader>
          <CardTitle>Customer Portal</CardTitle>
          <CardDescription>
            Manage deliveries, request fee quotes, and view parcel history (WS2).
          </CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            This route group is reserved for customer delivery workflows.
          </p>
        </CardContent>
      </Card>
    </main>
  );
}
