const { build } = require('../package.json');

const gmail = process.env.GOOGLE_DESKTOP_CLIENT_ID?.trim();
const outlook = process.env.MICROSOFT_DESKTOP_CLIENT_ID?.trim();
if (!/^[a-z0-9-]+\.apps\.googleusercontent\.com$/i.test(gmail ?? '')) {
  throw new Error('Set GOOGLE_DESKTOP_CLIENT_ID to the registered Google Desktop OAuth client ID.');
}
if (!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(outlook ?? '')) {
  throw new Error('Set MICROSOFT_DESKTOP_CLIENT_ID to the registered Microsoft application client ID.');
}

module.exports = { ...build, extraMetadata: { desktopOAuthClients: { gmail, outlook } } };
