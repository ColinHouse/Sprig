import { defineConfig } from 'vitepress'

// GitHub Pages project sites are served from /<repo>/. The deployment workflow
// passes DOCS_BASE from actions/configure-pages, so this default is only a
// local fallback. A custom domain or a user/org site should use DOCS_BASE=/.
const base = process.env.DOCS_BASE ?? '/sprig/'

export default defineConfig({
  base,
  lang: 'en-US',
  title: 'Sprig',
  description:
    'Sprig is an indentation-based, statically typed programming language that compiles to the JVM through a Java stage-0 compiler.',
  cleanUrls: true,
  lastUpdated: true,
  // Reference/Project pages are generated from the authoritative root
  // documents by website/scripts/sync-reference.mjs (gitignored).
  rewrites: {
    'generated/reference/:page': 'reference/:page',
    'generated/project/:page': 'project/:page'
  },
  head: [
    ['link', { rel: 'icon', type: 'image/png', sizes: '32x32', href: `${base}favicon-32.png` }],
    ['link', { rel: 'icon', type: 'image/png', sizes: '16x16', href: `${base}favicon-16.png` }],
    ['link', { rel: 'apple-touch-icon', sizes: '180x180', href: `${base}apple-touch-icon.png` }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:title', content: 'Sprig language' }],
    ['meta', { property: 'og:site_name', content: 'Sprig' }],
    ['meta', { property: 'og:image', content: `${base}og-image.png` }],
    ['meta', { name: 'theme-color', content: '#4c5165' }]
  ],
  markdown: {
    languageAlias: { sprig: 'python', spr: 'python' }
  },
  themeConfig: {
    logo: '/logo.png',
    siteTitle: 'Sprig',
    outline: { level: [2, 3] },
    search: { provider: 'local' },
    nav: [
      { text: 'Guide', link: '/guide/getting-started', activeMatch: '^/guide/' },
      { text: 'Examples', link: '/examples', activeMatch: '^/examples' },
      { text: 'Reference', link: '/reference/FEATURE_STATUS_IMPLEMENTED', activeMatch: '^/reference/' },
      { text: 'Project', link: '/project/contributing', activeMatch: '^/project/' }
    ],
    sidebar: {
      '/guide/': [
        {
          text: 'Guide',
          items: [
            { text: 'Getting Started', link: '/guide/getting-started' },
            { text: 'Language Tour', link: '/guide/language-tour' },
            { text: 'JVM Interoperability', link: '/guide/jvm-interop' },
            { text: 'Tooling and JSON', link: '/guide/tooling' },
            { text: 'Numerical Semantics', link: '/reference/NUMERIC_SEMANTICS' }
          ]
        }
      ],
      '/examples': [
        {
          text: 'Examples',
          items: [{ text: 'Verified examples', link: '/examples' }]
        }
      ],
      '/reference/': [
        {
          text: 'Language contract',
          items: [
            { text: 'Language spec (v0.7 design)', link: '/reference/LANGUAGE_SPEC' },
            { text: 'Quick reference', link: '/reference/QUICK_REFERENCE' },
            { text: 'Numerical semantics', link: '/reference/NUMERIC_SEMANTICS' },
            { text: 'Numeric design decisions', link: '/reference/NUMERIC_DESIGN_DECISIONS' },
            { text: 'Grammar', link: '/reference/grammar' }
          ]
        },
        {
          text: 'Implementation status',
          items: [
            { text: 'Implemented features', link: '/reference/FEATURE_STATUS_IMPLEMENTED' },
            { text: 'Known limitations', link: '/reference/KNOWN_LIMITATIONS' },
            { text: 'Diagnostic codes', link: '/reference/DIAGNOSTIC_CODES' },
            { text: 'Stage-1 roadmap', link: '/reference/STAGE1_ROADMAP' },
            { text: 'Agent tool protocol (proposed)', link: '/reference/AGENT_TOOL_PROTOCOL' }
          ]
        }
      ],
      '/project/': [
        {
          text: 'Project',
          items: [
            { text: 'Contributing', link: '/project/contributing' },
            { text: 'AI-assisted development', link: '/project/ai-disclosure' },
            { text: 'License status', link: '/project/license-status' },
            { text: 'Release status', link: '/project/release-status' },
            { text: 'Third-party notices', link: '/project/third-party-notices' }
          ]
        }
      ]
    },
    footer: {
      message:
        'Sprig is a v0.7 design with a working stage-0 alpha compiler. No project license has been selected yet.',
      copyright: 'Sprig project contributors'
    }
  }
})
