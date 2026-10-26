import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export default function DispatchPortalPage() {
  return (
    <main className="container mx-auto flex-1 p-6 max-w-4xl">
      <Card>
        <CardHeader>
          <CardTitle>Dispatcher Portal</CardTitle>
          <CardDescription>
            Courier workload management and parcel assignment (WS7).
          </CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            This route group is reserved for dispatcher operations.
          </p>
        </CardContent>
      </Card>
    </main>
  );
}
