/**
 * Normalizes an API URL to ensure it has a proper http:// or https:// protocol scheme.
 * This prevents browsers from treating bare hostnames as relative paths (which causes 404 errors).
 */
export const normalizeUrl = (url, defaultUrl) => {
  if (!url || url === 'undefined' || url === 'null') {
    return defaultUrl;
  }
  const trimmed = url.trim().replace(/\/+$/, ''); // trim whitespace and trailing slash
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
    return trimmed;
  }
  if (trimmed.startsWith('localhost') || trimmed.startsWith('127.0.0.1')) {
    return `http://${trimmed}`;
  }
  return `https://${trimmed}`;
};

export const USER_SERVICE_URL = normalizeUrl(import.meta.env.VITE_USER_SERVICE_URL, 'http://localhost:8081');
export const BANK_SERVICE_URL = normalizeUrl(import.meta.env.VITE_BANK_SERVICE_URL, 'http://localhost:8085');
export const TRANSACTION_SERVICE_URL = normalizeUrl(import.meta.env.VITE_TRANSACTION_SERVICE_URL, 'http://localhost:8086');
