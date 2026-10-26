import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export default function MerchantPortalPage() {
  return (
    <main className="container mx-auto flex-1 p-6 max-w-4xl">
      <Card>
        <CardHeader>
          <CardTitle>Merchant Portal</CardTitle>
          <CardDescription>
            Merchant dashboard, bulk orders, API keys, and webhook endpoints (WS4 & WS5).
          </CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            This route group is reserved for merchant operations.
          </p>
        </CardContent>
      </Card>
    </main>
  );
}
