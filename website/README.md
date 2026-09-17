# KeyJawn website

This Astro site publishes `https://keyjawn.amditis.tech`.

Use Node.js 22.19 or newer. The GitHub Pages workflow builds with Node.js 24.

## Commands

Run commands from `website/`:

```sh
npm ci
npm run dev
npm run check
npm run build
npm run preview
npm audit --omit=dev
```

`npm run check` performs the production build. The build writes the static site to `dist/`.
`npm audit --omit=dev` checks production dependencies for published advisories.

## Public routes

- `/` — platform overview and downloads
- `/features` — Android and iOS feature differences
- `/manual` — setup and usage
- `/support` — FAQ and known limits
- `/privacy` — Android and iOS privacy details
- `/pricing` — Android pricing and iOS App Store status
- `/changelog` — platform release status
- `/about` — project information
- `/thanks` — Android purchase confirmation
- `/blog/why-i-built-keyjawn` — dated Android product history

## Release rules

- Describe Android and iOS separately when behavior differs.
- State that the iOS terminal sends input to a remote SSH server. It does not run commands or browse files on the device.
- State that iOS image upload reads one copied image from the pasteboard. It does not request Photos or Files access.
- State that basic iOS keyboard typing works without Full Access.
- Verify every TestFlight or store URL before publication.
- Keep one source of FAQ data for visible answers and FAQ JSON-LD.
- Use the shared SVG favicon and provide Open Graph image alt text.

App Store screenshots are release metadata. Do not place them in `public/`
unless a website page uses them. The current inventory is in
[`../ios/AppStore/Screenshots/README.md`](../ios/AppStore/Screenshots/README.md).
