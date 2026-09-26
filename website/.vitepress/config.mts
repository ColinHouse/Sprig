import { defineConfig } from 'vitepress'

// GitHub Pages project site: https://colinhouse.github.io/Sprig/.
// The deployment workflow passes DOCS_BASE from actions/configure-pages, so
// this default is only a local fallback. A custom domain should use DOCS_BASE=/.
const base = process.env.DOCS_BASE ?? '/Sprig/'

const SITE = 'https://colinhouse.github.io/Sprig/'
const REPO = 'https://github.com/ColinHouse/Sprig'

const zhSearchTranslations = {
  translations: {
    button: { buttonText: '搜索', buttonAriaLabel: '搜索' },
    modal: {
      displayDetails: '显示详细列表',
      resetButtonTitle: '重置搜索',
      backButtonTitle: '关闭搜索',
      noResultsText: '没有找到结果',
      footer: {
        selectText: '选择',
        selectKeyAriaLabel: '回车',
        navigateText: '切换',
        navigateUpKeyAriaLabel: '上箭头',
        navigateDownKeyAriaLabel: '下箭头',
        closeText: '关闭',
        closeKeyAriaLabel: 'Esc'
      }
    }
  }
}

export default defineConfig({
  base,
  cleanUrls: true,
  lastUpdated: true,
  srcExclude: ['README.md'],
  // Reference/Project pages are generated from the authoritative root
  // documents by website/scripts/sync-reference.mjs (gitignored).
  rewrites: {
    'generated/en/reference/:page': 'en/reference/:page',
    'generated/en/project/:page': 'en/project/:page'
  },
  head: [
    ['link', { rel: 'icon', type: 'image/png', sizes: '32x32', href: `${base}favicon-32.png` }],
    ['link', { rel: 'icon', type: 'image/png', sizes: '16x16', href: `${base}favicon-16.png` }],
    ['link', { rel: 'apple-touch-icon', sizes: '180x180', href: `${base}apple-touch-icon.png` }],
    ['link', { rel: 'alternate', hreflang: 'zh-CN', href: SITE }],
    ['link', { rel: 'alternate', hreflang: 'en-US', href: `${SITE}en/` }],
    ['link', { rel: 'alternate', hreflang: 'x-default', href: SITE }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:title', content: 'Sprig' }],
    ['meta', { property: 'og:site_name', content: 'Sprig' }],
    ['meta', { property: 'og:image', content: `${SITE}og-image.png` }],
    ['meta', { property: 'og:url', content: SITE }],
    ['meta', { name: 'twitter:card', content: 'summary' }],
    ['meta', { name: 'theme-color', content: '#4c5165' }]
  ],
  markdown: {
    languageAlias: { sprig: 'python', spr: 'python' }
  },
  locales: {
    root: {
      label: '简体中文',
      lang: 'zh-CN',
      title: 'Sprig',
      description:
        'Sprig 是一门面向 JVM 的缩进式静态类型语言，拥有 sealed variant、穷尽 match、受检数值与显式 JVM 互操作；当前由 Java stage-0 编译器实现。',
      head: [['meta', { property: 'og:locale', content: 'zh_CN' }]],
      markdown: {
        container: {
          tipLabel: '提示',
          warningLabel: '注意',
          dangerLabel: '警告',
          infoLabel: '说明',
          detailsLabel: '详细信息'
        },
        codeCopyButton: { tooltipText: '复制代码', copiedText: '已复制' }
      },
      themeConfig: {
        nav: [
          { text: '指南', link: '/guide/getting-started', activeMatch: '^/guide/' },
          { text: '示例', link: '/examples', activeMatch: '^/examples' },
          {
            text: '参考',
            link: '/reference/implementation-status',
            activeMatch: '^/reference/'
          },
          { text: '项目', link: '/project/release-status', activeMatch: '^/project/' }
        ],
        sidebar: {
          '/guide/': [
            {
              text: '指南',
              items: [
                { text: '快速开始', link: '/guide/getting-started' },
                { text: '语言导览', link: '/guide/language-tour' },
                { text: '泛型（v0.8）', link: '/guide/generics' },
                { text: 'JVM 互操作', link: '/guide/jvm-interop' },
                { text: '工具与 JSON', link: '/guide/tooling' }
              ]
            }
          ],
          '/examples': [
            { text: '示例', items: [{ text: '经过验证的示例', link: '/examples' }] }
          ],
          '/reference/': [
            {
              text: '实现状态',
              items: [
                { text: '实现状态摘要', link: '/reference/implementation-status' },
                { text: '已知限制', link: '/reference/known-limitations' },
                { text: '英文技术参考', link: '/reference/index' }
              ]
            }
          ],
          '/project/': [
            {
              text: '项目',
              items: [
                { text: '发布状态', link: '/project/release-status' },
                { text: '参与贡献（英文）', link: '/en/project/contributing' },
                { text: 'AI 辅助开发（英文）', link: '/en/project/ai-disclosure' },
                { text: '许可证（英文）', link: '/en/project/license-status' },
                { text: '第三方说明（英文）', link: '/en/project/third-party-notices' }
              ]
            }
          ]
        },
        footer: {
          message: 'Sprig 采用 Apache-2.0 许可证 · v0.1.0-alpha.1 已作为 prerelease 发布',
          copyright: 'Copyright 2026 ColinHouse and Sprig contributors'
        }
      }
    },
    en: {
      label: 'English',
      lang: 'en-US',
      title: 'Sprig',
      description:
        'Sprig is an indentation-based, statically typed JVM language with sealed variants, exhaustive match, checked numerics and explicit JVM interop, implemented by a Java stage-0 compiler.',
      head: [['meta', { property: 'og:locale', content: 'en_US' }]],
      themeConfig: {
        nav: [
          {
            text: 'Guide',
            link: '/en/guide/getting-started',
            activeMatch: '^/en/guide/'
          },
          { text: 'Examples', link: '/en/examples', activeMatch: '^/en/examples' },
          {
            text: 'Reference',
            link: '/en/reference/FEATURE_STATUS_IMPLEMENTED',
            activeMatch: '^/en/reference/'
          },
          {
            text: 'Project',
            link: '/en/project/release-status',
            activeMatch: '^/en/project/'
          }
        ],
        sidebar: {
          '/en/guide/': [
            {
              text: 'Guide',
              items: [
                { text: 'Getting Started', link: '/en/guide/getting-started' },
                { text: 'Language Tour', link: '/en/guide/language-tour' },
                { text: 'Generics (v0.8)', link: '/en/guide/generics' },
                { text: 'JVM Interoperability', link: '/en/guide/jvm-interop' },
                { text: 'Tooling and JSON', link: '/en/guide/tooling' }
              ]
            }
          ],
          '/en/examples': [
            { text: 'Examples', items: [{ text: 'Verified examples', link: '/en/examples' }] }
          ],
          '/en/reference/': [
            {
              text: 'Language contract',
              items: [
                { text: 'Language spec (v0.7 design)', link: '/en/reference/LANGUAGE_SPEC' },
                { text: 'Generics contract (v0.8)', link: '/en/reference/GENERICS' },
                { text: 'Quick reference', link: '/en/reference/QUICK_REFERENCE' },
                { text: 'Numerical semantics', link: '/en/reference/NUMERIC_SEMANTICS' },
                {
                  text: 'Numeric design decisions',
                  link: '/en/reference/NUMERIC_DESIGN_DECISIONS'
                },
                { text: 'Grammar', link: '/en/reference/grammar' }
              ]
            },
            {
              text: 'Implementation status',
              items: [
                {
                  text: 'Implemented features',
                  link: '/en/reference/FEATURE_STATUS_IMPLEMENTED'
                },
                { text: 'Known limitations', link: '/en/reference/KNOWN_LIMITATIONS' },
                { text: 'JVM interop', link: '/en/reference/JVM_INTEROP' },
                { text: 'Diagnostic codes', link: '/en/reference/DIAGNOSTIC_CODES' },
                { text: 'Stage-1 roadmap', link: '/en/reference/STAGE1_ROADMAP' },
                {
                  text: 'Agent tool protocol (proposed)',
                  link: '/en/reference/AGENT_TOOL_PROTOCOL'
                }
              ]
            }
          ],
          '/en/project/': [
            {
              text: 'Project',
              items: [
                { text: 'Contributing', link: '/en/project/contributing' },
                { text: 'AI-assisted development', link: '/en/project/ai-disclosure' },
                { text: 'License', link: '/en/project/license-status' },
                { text: 'Release status', link: '/en/project/release-status' },
                { text: 'Third-party notices', link: '/en/project/third-party-notices' }
              ]
            }
          ]
        },
        footer: {
          message:
            'Sprig is licensed under Apache-2.0 · v0.1.0-alpha.1 published as a prerelease',
          copyright: 'Copyright 2026 ColinHouse and Sprig contributors'
        }
      }
    }
  },
  themeConfig: {
    logo: '/logo-mono.png',
    outline: { level: [2, 3] },
    socialLinks: [{ icon: 'github', link: REPO }],
    search: {
      provider: 'local',
      options: {
        locales: { root: zhSearchTranslations }
      }
    }
  }
})
