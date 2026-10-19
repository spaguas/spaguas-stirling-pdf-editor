let activeAccount: string | null = null;

/** Set before the editor is imported; an account change requires a document reload to close its workers and databases. */
export function setCommunityIdentity(id: string): void {
  if (activeAccount !== null && activeAccount !== id)
    throw new Error("Reload the application before switching accounts");
  activeAccount = id;
}

/** Prevent file databases from being opened before an authenticated identity has been resolved. */
export function communityDatabaseName(name: string): string {
  if (!activeAccount)
    throw new Error(
      "Resolve the Community identity before opening document storage",
    );
  const suffix = `::community::${activeAccount}`;
  return name.endsWith(suffix) ? name : `${name}${suffix}`;
}
