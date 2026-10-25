import type { ArtPack, Asset } from '@mempolis/engine';

/** The asset selected in the URL hash, or the first one. */
export function selectedAsset(pack: ArtPack): Asset {
  const id = decodeURIComponent(location.hash.slice(1));
  return pack.assets.find(a => a.id === id) ?? pack.assets[0];
}

const GROUP_ORDER = ['Sprawl · Neo N3', 'Array · Neo X', 'Both regions', 'Landmarks', 'Archive'];

function groupOf(a: Asset): string {
  if (a.group) return a.group;
  if (a.batch === 0) return 'Archive';
  if (a.regions?.length === 1) return a.regions[0] === 'neox' ? 'Array · Neo X' : 'Sprawl · Neo N3';
  return 'Both regions';
}

/** Render the sidebar list grouped by region, with archived assets last. */
export function renderNav(nav: HTMLElement, pack: ArtPack) {
  const groups = new Map<string, Asset[]>();
  for (const a of pack.assets) groups.set(groupOf(a), [...(groups.get(groupOf(a)) ?? []), a]);
  nav.replaceChildren();
  for (const name of [...GROUP_ORDER, ...[...groups.keys()].filter(g => !GROUP_ORDER.includes(g))]) {
    const assets = groups.get(name);
    if (!assets) continue;
    const heading = document.createElement('h3');
    heading.textContent = name;
    nav.append(heading);
    for (const a of assets) {
      const link = document.createElement('a');
      link.href = `#${a.id}`; link.textContent = a.name; link.dataset.id = a.id;
      nav.append(link);
    }
  }
}

/** Mark the selected asset in the sidebar. */
export function markSelected(nav: HTMLElement, id: string) {
  nav.querySelectorAll<HTMLAnchorElement>('a').forEach(l => l.setAttribute('aria-current', String(l.dataset.id === id)));
}
