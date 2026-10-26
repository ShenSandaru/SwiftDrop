import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export default function AdminPortalPage() {
  return (
    <main className="container mx-auto flex-1 p-6 max-w-4xl">
      <Card>
        <CardHeader>
          <CardTitle>Administration Portal</CardTitle>
          <CardDescription>
            System administration, user management, pricing rules, and reports (WS1 & WS8).
          </CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            This route group is reserved for administrative capabilities.
          </p>
        </CardContent>
      </Card>
    </main>
  );
}
