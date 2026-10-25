# SwiftDrop frontend

Next.js App Router frontend for SwiftDrop. It is responsible for presentation only; business rules,
authorization and persistence live in the Spring Boot backend.

See the root [README](../README.md), the
[project rules](../docs/ai-agent-guidelines.md#project-rules) and
[ADR 0002](../docs/adr/0002-technology-and-version-baseline.md) for the stack and conventions.
[AGENTS.md](AGENTS.md) is maintained by Next.js and points to the docs bundled with the installed version.

## Requirements

- Node.js 24 LTS and npm (see [development setup](../docs/development-setup.md))

## Commands

Run from this directory:

```bash
npm ci          # install exact versions from package-lock.json
npm run dev     # development server on http://localhost:3000
npm run lint    # ESLint
npm run build   # production build (includes type checking)
npm start       # serve the production build
```

CI (`frontend` job in `../.github/workflows/ci.yml`) runs `npm ci`, `npm run lint` and `npm run build`
on Node.js 24 for every pull request to `main`; run the same commands before opening one.

## Structure

```text
app/              Routes, layouts and global styles (App Router)
components/ui/    shadcn/ui components (source copied into the repo; edit freely)
lib/              Shared helpers (e.g. `cn` for class names)
public/           Static assets
```

Add shadcn/ui components only when needed. The project uses the `base-nova` preset, so components
are built on Base UI, not Radix. Use the shadcn CLI installed from `package-lock.json` (not
`shadcn@latest`, which could download a different major version):

```bash
npx --no-install shadcn add <component>
```

## Known npm warnings

- `npm warn allow-scripts ... unrs-resolver ... postinstall`: expected and harmless. The skipped
  script is only a fallback for installing a missing native binding. The binding is installed from
  the lockfile anyway. Do not approve it. If lint reports a missing native binding, delete
  `node_modules` and run `npm ci`.
- `npm warn deprecated eslint@9...`: ESLint 9 is end-of-life, but it is kept until the Next.js
  ESLint plugins support ESLint 10. See
  [ADR 0002](../docs/adr/0002-technology-and-version-baseline.md).
