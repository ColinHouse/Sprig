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
    'generated/en/project/contributing.md': 'en/project/contributing.md',
    'generated/en/reference/:category/:page': 'en/reference/:category/:page',
    'generated/en/project/:category/:page': 'en/project/:category/:page'
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
        'Sprig 是一门跑在 JVM 上的静态类型小语言，语法像 Python。写错了，编译器会告诉你错在哪、为什么错、怎么改。当前为实验性 Beta。',
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
          { text: '入门教程', link: '/tutorial/', activeMatch: '^/tutorial' },
          { text: '指南', link: '/guide/getting-started', activeMatch: '^/guide/' },
          { text: '示例', link: '/examples', activeMatch: '^/examples' },
          {
            text: '参考',
            link: '/reference/index',
            activeMatch: '^/reference/'
          },
          { text: '项目', link: '/project/release-status', activeMatch: '^/project/' }
        ],
        sidebar: {
          '/tutorial': [
            { text: 'Sprig 程序设计语言', items: [{ text: '前言', link: '/tutorial/' }] },
            {
              text: '第一部分：从零开始写程序',
              items: [
                { text: '1. 准备工具', link: '/tutorial/ch01-tools' },
                { text: '2. 第一个程序与读懂报错', link: '/tutorial/ch02-first-program' },
                { text: '3. 值、变量和算术', link: '/tutorial/ch03-values' },
                { text: '4. 文字', link: '/tutorial/ch04-text' },
                { text: '5. 做决定：if', link: '/tutorial/ch05-if' },
                { text: '6. 重复：循环', link: '/tutorial/ch06-loops' },
                { text: '7. 函数', link: '/tutorial/ch07-functions' },
                { text: '8. 列表', link: '/tutorial/ch08-lists' },
                { text: '9. 映射和集合', link: '/tutorial/ch09-maps-sets' },
                { text: '10. 小项目：猜数字', link: '/tutorial/ch10-project-guess' }
              ]
            },
            {
              text: '第二部分：用类型描述世界',
              items: [
                { text: '11. 类和对象', link: '/tutorial/ch11-classes' },
                { text: '12. 枚举、variant 和 match', link: '/tutorial/ch12-enums-variants' },
                { text: '13. 可空值', link: '/tutorial/ch13-nullable' },
                { text: '14. 错误处理', link: '/tutorial/ch14-errors' },
                { text: '15. 函数作为值', link: '/tutorial/ch15-functions-as-values' },
                { text: '16. 泛型与契约类', link: '/tutorial/ch16-generics-contracts' },
                { text: '17. 数字进阶', link: '/tutorial/ch17-numbers' }
              ]
            },
            {
              text: '第三部分：做真正的程序',
              items: [
                { text: '18. 模块、项目和依赖', link: '/tutorial/ch18-modules-projects' },
                { text: '19. 测试', link: '/tutorial/ch19-testing' },
                { text: '20. 标准库实用篇', link: '/tutorial/ch20-stdlib' },
                { text: '21. 调用 Java', link: '/tutorial/ch21-java' },
                { text: '22. 并发', link: '/tutorial/ch22-concurrency' },
                { text: '23. 工具链与 AI 助手', link: '/tutorial/ch23-tooling' },
                { text: '24. 综合项目：记账', link: '/tutorial/ch24-project-ledger' }
              ]
            },
            {
              text: '附录',
              items: [
                { text: 'A. 运算符与优先级', link: '/tutorial/appendix-a-operators' },
                { text: 'B. 报错码速查', link: '/tutorial/appendix-b-error-codes' },
                { text: 'C. 关键字和内置函数', link: '/tutorial/appendix-c-keywords' },
                { text: 'D. 从 Python、JavaScript、Java 过来', link: '/tutorial/appendix-d-from-other-languages' },
                { text: 'E. Sprig 没有的东西', link: '/tutorial/appendix-e-not-in-sprig' },
                { text: 'F. Java 互操作进阶', link: '/tutorial/appendix-f-advanced-java' }
              ]
            }
          ],
          '/guide/': [
            {
              text: '指南',
              items: [
                { text: '快速开始', link: '/guide/getting-started' },
                { text: '语言速查', link: '/guide/language-tour' },
                { text: '泛型', link: '/guide/generics' },
                { text: '项目', link: '/guide/projects' },
                { text: '工具与 JSON', link: '/guide/tooling' },
                { text: 'VS Code 插件', link: '/guide/editor' },
                { text: '和 AI 助手一起写代码', link: '/guide/agent-workflow' },
                { text: 'JVM 互操作', link: '/guide/jvm-interop' },
                { text: 'Gradle 集成', link: '/guide/gradle' },
                { text: 'Fabric 模组', link: '/guide/fabric' },
                { text: 'Web 与 SQLite', link: '/guide/web-sqlite' },
                { text: '并发', link: '/guide/concurrency' },
                { text: '项目测试（英文）', link: '/en/reference/tooling/testing' }
              ]
            }
          ],
          '/examples': [
            { text: '示例', items: [{ text: '经过验证的示例', link: '/examples' }] }
          ],
          '/reference/': [
            { text: '技术参考', items: [{ text: '分类索引', link: '/reference/index' }] }
          ],
          '/project/': [
            {
              text: '项目',
              items: [
                { text: '发布状态', link: '/project/release-status' },
                { text: '参与贡献（英文）', link: '/en/project/contributing' },
                { text: 'AI 辅助开发（英文）', link: '/en/project/contributing/ai-disclosure' },
                { text: '许可证（英文）', link: '/en/project/contributing/license-status' },
                { text: '第三方说明（英文）', link: '/en/project/contributing/third-party-notices' }
              ]
            }
          ]
        },
        footer: {
          message: 'Sprig 采用 Apache-2.0 许可证 · v0.7.1-beta.1 已作为 prerelease 发布',
          copyright: 'Copyright 2026 ColinHouse and Sprig contributors'
        }
      }
    },
    en: {
      label: 'English',
      lang: 'en-US',
      title: 'Sprig',
      description:
        'A small, statically typed JVM language with Python-like syntax. When something is wrong, the compiler tells you where, why and how to fix it. Experimental Beta.',
      head: [['meta', { property: 'og:locale', content: 'en_US' }]],
      themeConfig: {
        nav: [
          { text: 'Tutorial', link: '/en/tutorial/', activeMatch: '^/en/tutorial' },
          {
            text: 'Guide',
            link: '/en/guide/getting-started',
            activeMatch: '^/en/guide/'
          },
          { text: 'Examples', link: '/en/examples', activeMatch: '^/en/examples' },
          {
            text: 'Reference',
            link: '/en/reference/language/feature-status',
            activeMatch: '^/en/reference/'
          },
          {
            text: 'Project',
            link: '/en/project/release-status',
            activeMatch: '^/en/project/'
          }
        ],
        sidebar: {
          '/en/tutorial/': [
            { text: 'The Sprig Programming Language', items: [{ text: 'Preface', link: '/en/tutorial/' }] },
            {
              text: 'Part 1: Writing programs from scratch',
              items: [
                { text: '1. Getting set up', link: '/en/tutorial/ch01-tools' },
                { text: '2. Your first program, and reading errors', link: '/en/tutorial/ch02-first-program' },
                { text: '3. Values, variables and arithmetic', link: '/en/tutorial/ch03-values' },
                { text: '4. Text', link: '/en/tutorial/ch04-text' },
                { text: '5. Making decisions: if', link: '/en/tutorial/ch05-if' },
                { text: '6. Repeating: loops', link: '/en/tutorial/ch06-loops' },
                { text: '7. Functions', link: '/en/tutorial/ch07-functions' },
                { text: '8. Lists', link: '/en/tutorial/ch08-lists' },
                { text: '9. Maps and sets', link: '/en/tutorial/ch09-maps-sets' },
                { text: '10. Project: guess the number', link: '/en/tutorial/ch10-project-guess' }
              ]
            },
            {
              text: 'Part 2: Describing the world with types',
              items: [
                { text: '11. Classes and objects', link: '/en/tutorial/ch11-classes' },
                { text: '12. Enums, variants and match', link: '/en/tutorial/ch12-enums-variants' },
                { text: '13. Values that may be missing', link: '/en/tutorial/ch13-nullable' },
                { text: '14. Handling errors', link: '/en/tutorial/ch14-errors' },
                { text: '15. Functions as values', link: '/en/tutorial/ch15-functions-as-values' },
                { text: '16. Generics and contract classes', link: '/en/tutorial/ch16-generics-contracts' },
                { text: '17. More about numbers', link: '/en/tutorial/ch17-numbers' }
              ]
            },
            {
              text: 'Part 3: Building real programs',
              items: [
                { text: '18. Modules, projects and dependencies', link: '/en/tutorial/ch18-modules-projects' },
                { text: '19. Testing', link: '/en/tutorial/ch19-testing' },
                { text: '20. The standard library at work', link: '/en/tutorial/ch20-stdlib' },
                { text: '21. Calling Java', link: '/en/tutorial/ch21-java' },
                { text: '22. Concurrency', link: '/en/tutorial/ch22-concurrency' },
                { text: '23. Tools and AI assistants', link: '/en/tutorial/ch23-tooling' },
                { text: '24. Project: an expense tracker', link: '/en/tutorial/ch24-project-ledger' }
              ]
            },
            {
              text: 'Appendices',
              items: [
                { text: 'A. Operators and precedence', link: '/en/tutorial/appendix-a-operators' },
                { text: 'B. Error codes and where to read about them', link: '/en/tutorial/appendix-b-error-codes' },
                { text: 'C. Keywords and built-in functions', link: '/en/tutorial/appendix-c-keywords' },
                { text: 'D. Coming from Python, JavaScript or Java', link: '/en/tutorial/appendix-d-from-other-languages' },
                { text: 'E. What Sprig leaves out', link: '/en/tutorial/appendix-e-not-in-sprig' },
                { text: 'F. Java interop in depth', link: '/en/tutorial/appendix-f-advanced-java' }
              ]
            }
          ],
          '/en/guide/': [
            {
              text: 'Guide',
              items: [
                { text: 'Getting started', link: '/en/guide/getting-started' },
                { text: 'Install and upgrade', link: '/en/reference/projects/install' },
                { text: 'Language quick reference', link: '/en/guide/language-tour' },
                { text: 'Generics', link: '/en/guide/generics' },
                { text: 'Projects', link: '/en/guide/projects' },
                { text: 'Tools and JSON', link: '/en/guide/tooling' },
                { text: 'VS Code extension', link: '/en/guide/editor' },
                { text: 'Working with AI assistants', link: '/en/guide/agent-workflow' },
                { text: 'JVM interop', link: '/en/guide/jvm-interop' },
                { text: 'Gradle integration', link: '/en/guide/gradle' },
                { text: 'Fabric mods', link: '/en/guide/fabric' },
                { text: 'Web and SQLite', link: '/en/guide/web-sqlite' },
                { text: 'Concurrency', link: '/en/guide/concurrency' },
                { text: 'Testing projects', link: '/en/reference/tooling/testing' }
              ]
            }
          ],
          '/en/examples': [
            { text: 'Examples', items: [{ text: 'Verified examples', link: '/en/examples' }] }
          ],
          '/en/reference/': [
            { text: 'Canonical reference', items: [
              { text: 'Language and types', link: '/en/reference/language/feature-status' },
              { text: 'JVM', link: '/en/reference/jvm/interop' },
              { text: 'Projects and dependencies', link: '/en/reference/projects/projects' },
              { text: 'Tooling and tests', link: '/en/reference/tooling/testing' }
            ] }
          ],
          '/en/project/': [
            {
              text: 'Project',
              items: [
                { text: 'Contributing', link: '/en/project/contributing' },
                { text: 'AI-assisted development', link: '/en/project/contributing/ai-disclosure' },
                { text: 'License', link: '/en/project/contributing/license-status' },
                { text: 'Release status', link: '/en/project/release-status' },
                { text: 'Third-party notices', link: '/en/project/contributing/third-party-notices' }
              ]
            }
          ]
        },
        footer: {
          message:
            'Sprig is licensed under Apache-2.0 · v0.7.1-beta.1 published as a prerelease',
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
