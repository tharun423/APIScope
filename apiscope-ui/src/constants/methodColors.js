/**
 * Tailwind CSS colour tokens for each HTTP method badge.
 * Centralised here so every component uses the same palette.
 */
export const METHOD_COLORS = {
  GET:    { bg: 'bg-emerald-600', text: 'text-white', border: 'border-emerald-500', light: 'bg-emerald-900/20 border-emerald-700/40' },
  POST:   { bg: 'bg-blue-600',   text: 'text-white', border: 'border-blue-500',   light: 'bg-blue-900/20 border-blue-700/40' },
  PUT:    { bg: 'bg-amber-600',  text: 'text-white', border: 'border-amber-500',  light: 'bg-amber-900/20 border-amber-700/40' },
  PATCH:  { bg: 'bg-orange-600', text: 'text-white', border: 'border-orange-500', light: 'bg-orange-900/20 border-orange-700/40' },
  DELETE: { bg: 'bg-red-600',    text: 'text-white', border: 'border-red-500',    light: 'bg-red-900/20 border-red-700/40' },
}

/** Returns the colour token for {@code method}, defaulting to GET colours. */
export const methodColor = (m) => METHOD_COLORS[m?.toUpperCase()] ?? METHOD_COLORS.GET
