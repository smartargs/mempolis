import type { Asset, DecayRole } from '@mempolis/engine';

const DECAY_TEXT: Record<DecayRole, string> = {
  activity: 'Shows recent activity',
  maintained: 'Gets lightly neglected',
  none: 'Never decays',
};

const row = (term: string, value: string, wide = false) => `<div${wide ? ' class="wide"' : ''}><dt>${term}</dt><dd>${value}</dd></div>`;

/** Show what `a` represents: its on-chain source, visibility, update cadence and decay role. */
export function renderMapping(host: HTMLElement, a: Asset) {
  host.innerHTML = `<dl style="display:contents">`
    + row('Represents', a.mapping.source, true)
    + row('Visible', a.mapping.visibility)
    + row('Updates', a.mapping.cadence.join(', '))
    + row('Decay', DECAY_TEXT[a.decayRole])
    + `</dl><p class="hint">Stage thresholds are drafts; final values live in the rules engine.</p>`;
}
