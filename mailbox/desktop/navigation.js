/** Allow only our local application and the two provider authorization entry points. */
export function navigationTarget(value, applicationOrigin) {
  let url;
  try { url = new URL(value); } catch { return 'blocked'; }
  if (url.username || url.password) return 'blocked';
  if (url.origin === applicationOrigin) return 'application';
  if (url.protocol !== 'https:' || url.port) return 'blocked';
  if (url.hostname === 'accounts.google.com' && url.pathname === '/o/oauth2/v2/auth') return 'authorization';
  if (url.hostname === 'login.microsoftonline.com' && url.pathname === '/common/oauth2/v2.0/authorize') return 'authorization';
  return 'blocked';
}
