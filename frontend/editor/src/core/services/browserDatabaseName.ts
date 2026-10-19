/** Storage layers may isolate browser documents by authenticated account without copying PDF blobs. */
export function browserDatabaseName(name: string): string {
  return name;
}
