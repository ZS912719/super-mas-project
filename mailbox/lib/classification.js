/** Replace this adapter when the agent output schema is agreed. Unknown fields survive. */
export function normalizeClassification(payload) {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) throw new Error('Classification must be a JSON object.');
  const labels = payload.labels ?? payload.categories ?? (payload.category ? [payload.category] : []);
  if (!Array.isArray(labels) || labels.length > 12 || labels.some(label => typeof label !== 'string' || !label.trim() || label.length > 60)) {
    throw new Error('Classification labels must be an array of up to 12 nonempty strings.');
  }
  if (payload.confidence != null && (typeof payload.confidence !== 'number' || payload.confidence < 0 || payload.confidence > 1)) {
    throw new Error('Classification confidence must be between 0 and 1.');
  }
  return { schemaVersion: String(payload.schemaVersion ?? '1'), labels: [...new Set(labels.map(label => label.trim()))],
    confidence: payload.confidence ?? null, reason: typeof payload.reason === 'string' ? payload.reason : '', raw: structuredClone(payload) };
}
