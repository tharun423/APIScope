/**
 * Prompt templates used across the UI to generate AI questions.
 *
 * Centralising them here means:
 *  - Wording is consistent across components.
 *  - Copy changes require edits in exactly one file.
 *  - Templates can be unit-tested independently.
 */

/**
 * Builds the "Ask AI" prompt for a specific endpoint.
 *
 * @param {string} httpMethod - e.g. 'GET', 'POST'
 * @param {string} path       - e.g. '/api/users/{id}'
 * @returns {string}
 */
export const buildEndpointAiPrompt = (httpMethod, path) =>
  `Explain the ${httpMethod} ${path} endpoint and show me a code example`
