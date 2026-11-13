import { redirect } from "next/navigation";

/**
 * Redirects legacy /signup route directly to customer registration.
 * Customer signup is direct, and merchant registration has been relocated
 * to the homepage footer ("For Businesses" -> /signup/merchant).
 */
export default function SignupChooserPage() {
  redirect("/signup/customer");
}
