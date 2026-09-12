import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import { notifier } from 'notifier';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid';
import '@vaadin/button';
import '@vaadin/select';
import '@vaadin/details';

/**
 * Network faults: one row per proxied outbound connection, with fault toggles and an event log.
 */
export class QwcNetworkFaults extends LitElement {
    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host { display: flex; flex-direction: column; gap: 10px; padding: 10px; }
        .toolbar { display: flex; gap: 10px; align-items: center; }
        .hint { color: var(--lumo-secondary-text-color); }
        .active { color: var(--lumo-error-text-color); font-weight: bold; }
        .none { color: var(--lumo-success-text-color); }
        .row { display: flex; gap: 6px; align-items: center; }
        pre { font-size: var(--lumo-font-size-xs); margin: 0; }
        code { font-size: var(--lumo-font-size-s); }
    `;

    static properties = {
        _status: { state: true },
        _faults: { state: true },
        _selected: { state: true },
        _events: { state: true },
        _eventsTarget: { state: true }
    };

    constructor() {
        super();
        this._status = null;
        this._faults = [];
        this._selected = {};
        this._events = [];
        this._eventsTarget = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getFaults().then(r => { this._faults = r.result; });
        this._refresh();
        this._timer = setInterval(() => this._refresh(), 2000);
    }

    disconnectedCallback() {
        clearInterval(this._timer);
        super.disconnectedCallback();
    }

    _refresh() {
        this.jsonRpc.getStatus().then(r => { this._status = r.result; });
        if (this._eventsTarget) {
            this.jsonRpc.getEvents({ target: this._eventsTarget }).then(r => { this._events = r.result; });
        }
    }

    render() {
        if (!this._status) {
            return html`<span class="hint">Loading…</span>`;
        }
        if (!this._status.enabled) {
            return html`<span class="hint">${this._status.hint}</span>`;
        }
        return html`
            <div class="toolbar">
                <vaadin-button theme="small" @click=${() => this._call('resetAll', {})}>End all faults</vaadin-button>
                <span class="hint">${this._status.targets.length} target(s); refreshed every 2s</span>
            </div>
            <vaadin-grid .items=${this._status.targets} all-rows-visible>
                <vaadin-grid-column header="Target" ${columnBodyRenderer(t => html`<code>${t.name}</code><br/><span class="hint">${t.sourceProperty}</span>`, [])}></vaadin-grid-column>
                <vaadin-grid-column header="Proxy → upstream" ${columnBodyRenderer(t => html`127.0.0.1:${t.proxyPort} → ${t.upstream}${t.listening ? '' : ' (not listening)'}`, [])}></vaadin-grid-column>
                <vaadin-grid-column header="Fault" ${columnBodyRenderer(t => html`<span class="${t.fault === 'none' ? 'none' : 'active'}">${t.fault}</span>`, [])}></vaadin-grid-column>
                <vaadin-grid-column header="Connections" ${columnBodyRenderer(t => html`${t.connections.length} live, ${t.connectionsSeen} seen, ${t.connectionsReset} reset`, [])}></vaadin-grid-column>
                <vaadin-grid-column header="Bytes ↑ / ↓" ${columnBodyRenderer(t => html`${t.bytesUpstream} / ${t.bytesDownstream}`, [])}></vaadin-grid-column>
                <vaadin-grid-column header="Actions" ${columnBodyRenderer(t => this._actions(t), [])}></vaadin-grid-column>
            </vaadin-grid>
            ${this._eventsTarget ? html`
                <vaadin-details summary="Events of ${this._eventsTarget}" opened>
                    <pre>${this._events.map(e => `${e.time}  ${e.type}${e.connectionId >= 0 ? ' #' + e.connectionId : ''}  ${e.detail}`).join('\n')}</pre>
                </vaadin-details>` : ''}
        `;
    }

    _actions(t) {
        const selected = this._selected[t.name] || this._faults[0];
        return html`
            <div class="row">
                <vaadin-select theme="small" .items=${this._faults.map(s => ({ label: s, value: s }))} .value=${selected}
                    @value-changed=${e => { this._selected = { ...this._selected, [t.name]: e.detail.value }; }}></vaadin-select>
                <vaadin-button theme="small primary" @click=${() => this._call('apply', { target: t.name, fault: selected })}>Apply</vaadin-button>
                <vaadin-button theme="small" ?disabled=${t.fault === 'none'} @click=${() => this._call('reset', { target: t.name })}>End</vaadin-button>
                <vaadin-button theme="small" @click=${() => this._call('dropConnections', { target: t.name })}>Drop connections</vaadin-button>
                <vaadin-button theme="small tertiary" @click=${() => { this._eventsTarget = t.name; this._refresh(); }}>Events</vaadin-button>
            </div>`;
    }

    _call(method, params) {
        this.jsonRpc[method](params).then(r => {
            notifier.showInfoMessage(r.result);
            this._refresh();
        }).catch(e => notifier.showErrorMessage(String(e)));
    }
}
customElements.define('qwc-network-faults', QwcNetworkFaults);
