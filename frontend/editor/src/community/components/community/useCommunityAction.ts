import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

/** Refreshes Community directories after a successful write and leaves failed edits visible for correction. */
export function useCommunityAction() {
  const client = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const run = async (operation: () => Promise<unknown>): Promise<boolean> => {
    setBusy(true);
    setError(null);
    try {
      await operation();
      await client.invalidateQueries({ queryKey: ["community"] });
      return true;
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Request failed");
      return false;
    } finally {
      setBusy(false);
    }
  };
  return { run, busy, error };
}
