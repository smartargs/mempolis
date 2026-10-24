import type { Asset } from '@neoworld/engine';

const chosen = new Map<string, number[]>();

/** The selected option index per variant axis of `a` (defaults to the first option). */
export function selection(a: Asset): number[] {
  const sel = chosen.get(a.id) ?? (a.variants ?? []).map(() => 0);
  chosen.set(a.id, sel);
  return sel;
}

/** Params from the selected variant options, merged in axis order. */
export function variantParams(a: Asset): Record<string, unknown> {
  const sel = selection(a);
  return Object.assign({}, ...(a.variants ?? []).map((axis, i) => axis.options[sel[i]]?.params ?? {}));
}

/** Render one segmented control per variant axis; `onChange` runs after a pick. */
export function renderVariants(host: HTMLElement, a: Asset, onChange: () => void) {
  const axes = a.variants ?? [];
  host.hidden = axes.length === 0;
  const sel = selection(a);
  host.replaceChildren(...axes.map((axis, i) => {
    const group = document.createElement('div');
    group.className = 'variant';
    const name = document.createElement('span');
    name.className = 'vname'; name.textContent = axis.name;
    const seg = document.createElement('div');
    seg.className = 'seg'; seg.setAttribute('role', 'group'); seg.setAttribute('aria-label', axis.name);
    axis.options.forEach((opt, j) => {
      const btn = document.createElement('button');
      btn.textContent = opt.label;
      btn.setAttribute('aria-pressed', String(sel[i] === j));
      btn.addEventListener('click', () => { sel[i] = j; onChange(); });
      seg.append(btn);
    });
    const means = document.createElement('span');
    means.className = 'vmeans'; means.textContent = axis.options[sel[i]]?.means ?? '';
    group.append(name, seg, means);
    return group;
  }));
}
