import { ZodError } from "zod";

export function getValidationErrorMessage(error: unknown) {
  if (error instanceof ZodError) {
    return error.issues[0]?.message ?? null;
  }

  const message =
    error instanceof Error
      ? error.message
      : typeof error === "object" && error !== null
        ? (error as { message?: unknown }).message
        : null;

  if (typeof message !== "string" || !message.trim()) return null;

  const trimmed = message.trim();
  if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
    try {
      const parsed = JSON.parse(trimmed) as Array<{ message?: unknown }>;
      const firstMessage = parsed.find(
        (item) => typeof item?.message === "string"
      )?.message;
      return typeof firstMessage === "string" ? firstMessage : null;
    } catch {
      return null;
    }
  }

  return null;
}

export function looksTechnicalErrorMessage(message: string) {
  const value = message.trim();
  return (
    value.startsWith("[") ||
    value.startsWith("{") ||
    /(?:postgres|supabase|schema|constraint|violates|SQLSTATE|PGRST|JWT|stack|TypeError|ReferenceError)/i.test(
      value
    )
  );
}
