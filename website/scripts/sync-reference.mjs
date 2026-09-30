// Generate the site's English technical reference from categorized canonical docs.
import { mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const website = resolve(here, '..')
const repo = resolve(website, '..')
const categories = ['language', 'jvm', 'projects', 'tooling']
const projectCategories = ['contributing', 'releases']
const pages = []
for (const category of categories) {
  for (const file of readdirSync(join(repo, 'docs', category)).filter((name) => name.endsWith('.md')).sort()) {
    pages.push({ source: `docs/${category}/${file}`, target: `generated/en/reference/${category}/${file}` })
  }
}
for (const category of projectCategories) {
  for (const file of readdirSync(join(repo, 'docs', category)).filter((name) => name.endsWith('.md')).sort()) {
    pages.push({ source: `docs/${category}/${file}`, target: `generated/en/project/${category}/${file}` })
  }
}
pages.push({ source: 'CONTRIBUTING.md', target: 'generated/en/project/contributing.md' })
pages.push({ source: 'THIRD_PARTY_NOTICES.md', target: 'generated/en/project/contributing/third-party-notices.md' })
pages.push({ source: 'grammar/README.md', target: 'generated/en/reference/tooling/grammar.md' })

const routeFor = (target) => '/' + target.replace(/^generated\//, '').replace(/\.md$/, '')
const linkMap = new Map([
  ['README.md', '/en/'],
  ['LICENSE', 'https://github.com/ColinHouse/Sprig/blob/main/LICENSE'],
  ['NOTICE', 'https://github.com/ColinHouse/Sprig/blob/main/NOTICE'],
  ['CONTRIBUTING.md', '/en/project/contributing/ai-disclosure'],
  ['docs/contributing/ai-disclosure.md', '/en/project/contributing/ai-disclosure'],
  ['docs/contributing/license-status.md', '/en/project/contributing/license-status'],
  ['THIRD_PARTY_NOTICES.md', '/en/project/contributing/third-party-notices']
])
for (const page of pages) linkMap.set(page.source, routeFor(page.target))
const banner = (source) => `<!-- Generated from ${source}. Edit the canonical file in docs/. -->\n`
const rewriteLinks = (content, source) => content.replace(/\]\(([^)\s]+)\)/g, (match, href) => {
  const [target, anchor = ''] = href.split('#')
  if (!target || /^[a-z][a-z0-9+.-]*:/i.test(target) || target.startsWith('/')) return match
  const local = resolve(repo, dirname(source), target)
  const normalized = local.slice(repo.length + 1).replaceAll('\\', '/')
  const route = linkMap.get(normalized) || `https://github.com/ColinHouse/Sprig/blob/main/${normalized}`
  return `](${route}${anchor ? `#${anchor}` : ''})`
})

rmSync(join(website, 'generated'), { recursive: true, force: true })
for (const page of pages) {
  const destination = join(website, page.target)
  mkdirSync(dirname(destination), { recursive: true })
  writeFileSync(destination, banner(page.source) + rewriteLinks(readFileSync(join(repo, page.source), 'utf8'), page.source), 'utf8')
}
console.log(`synced ${pages.length} canonical English reference/project pages`)
