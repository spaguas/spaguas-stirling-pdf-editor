import type { ToolRecommendationDto } from "@core/api/toolRecommendations";
export type { ToolRecommendationDto };

/** Community uses the editor's static recommendations without sending usage to commercial endpoints. */
export async function fetchToolRecommendations(
  _currentTool: string | null,
  _limit = 6,
): Promise<ToolRecommendationDto[] | null> {
  return null;
}

export async function recordToolUsage(
  _toolKey: string,
  _priorChains: string[][] = [],
): Promise<void> {}
