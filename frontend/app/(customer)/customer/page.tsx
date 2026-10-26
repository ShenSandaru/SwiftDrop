import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export default function CustomerPortalPage() {
  return (
    <main className="container mx-auto flex-1 px-4 py-12 max-w-4xl space-y-4">
      <div className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-accent px-3 py-1 text-xs font-semibold text-primary">
        <span className="size-1.5 rounded-full bg-primary" />
        Customer Workspace · WS2
      </div>
      <Card className="rounded-xl bg-card border-border shadow-xs">
        <CardHeader>
          <CardTitle className="text-xl font-bold text-foreground">Customer Portal</CardTitle>
          <CardDescription className="text-sm text-muted-foreground">
            Manage deliveries, request fee quotes, and view parcel history.
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
