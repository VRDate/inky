/**
 * SillyTavern Extension: Inky Interactive Fiction
 *
 * Integrates the Inky MCP server with SillyTavern to enable:
 *   - Playing ink stories through SillyTavern's chat interface
 *   - Converting ink stories to character cards
 *   - Using ink story state as world info / lore
 *   - Compiling and debugging ink scripts from within SillyTavern
 *
 * The MCP server must be running at http://localhost:3001
 *
 * Installation:
 *   Copy this folder to: SillyTavern/public/scripts/extensions/third-party/inky/
 */

import { extension_settings, getContext } from '../../../extensions.js';
import { saveSettingsDebounced } from '../../../../script.js';

const extensionName = 'inky';
const extensionFolderPath = `scripts/extensions/third-party/${extensionName}`;

const defaultSettings = {
    mcpServerUrl: 'http://localhost:3001',
    autoPlay: false,
    showChoicesAsButtons: true,
    injectStoryAsSystemPrompt: false,
    currentSessionId: null,
};

// Initialize settings
if (!extension_settings[extensionName]) {
    extension_settings[extensionName] = {};
}
Object.assign(extension_settings[extensionName], {
    ...defaultSettings,
    ...extension_settings[extensionName],
});

const settings = extension_settings[extensionName];

// ── MCP Server Communication ──

async function callMcpTool(toolName, args = {}) {
    const url = `${settings.mcpServerUrl}/message`;
    try {
        const resp = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                jsonrpc: '2.0',
                id: Date.now(),
                method: 'tools/call',
                params: { name: toolName, arguments: args },
            }),
        });
        const data = await resp.json();
        if (data.result?.content?.[0]?.text) {
            return JSON.parse(data.result.content[0].text);
        }
        return data;
    } catch (err) {
        console.error(`[Inky] MCP call failed: ${toolName}`, err);
        return { error: err.message };
    }
}

async function callRestApi(endpoint, body = null) {
    const url = `${settings.mcpServerUrl}/api${endpoint}`;
    const options = body
        ? { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
        : { method: 'GET' };
    const resp = await fetch(url, options);
    return resp.json();
}

// ── Ink Story Integration ──

async function compileInk(source) {
    return callMcpTool('compile_ink', { source });
}

async function startStory(source) {
    const result = await callMcpTool('start_story', { source });
    if (result.session_id) {
        settings.currentSessionId = result.session_id;
        saveSettingsDebounced();
    }
    return result;
}

async function makeChoice(choiceIndex) {
    if (!settings.currentSessionId) return { error: 'No active story' };
    return callMcpTool('choose', {
        session_id: settings.currentSessionId,
        choice_index: choiceIndex,
    });
}

async function continueStory() {
    if (!settings.currentSessionId) return { error: 'No active story' };
    return callMcpTool('continue_story', {
        session_id: settings.currentSessionId,
    });
}

// ── UI Integration ──

function renderStoryOutput(result) {
    let html = '';

    if (result.text) {
        html += `<div class="inky-story-text">${escapeHtml(result.text)}</div>`;
    }

    if (result.choices && result.choices.length > 0) {
        html += '<div class="inky-choices">';
        result.choices.forEach((choice) => {
            html += `<button class="inky-choice-btn" data-index="${choice.index}">${escapeHtml(choice.text)}</button>`;
        });
        html += '</div>';
    } else if (!result.can_continue) {
        html += '<div class="inky-end">— The End —</div>';
    }

    return html;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ── Settings UI ──

function renderSettings() {
    const html = `
        <div class="inky-settings">
            <div class="inline-drawer">
                <div class="inline-drawer-toggle inline-drawer-header">
                    <b>Inky Interactive Fiction</b>
                    <div class="inline-drawer-icon fa-solid fa-circle-chevron-down down"></div>
                </div>
                <div class="inline-drawer-content">
                    <label>MCP Server URL
                        <input id="inky_server_url" class="text_pole" type="text"
                            value="${settings.mcpServerUrl}" placeholder="http://localhost:3001">
                    </label>
                    <label class="checkbox_label">
                        <input id="inky_auto_play" type="checkbox" ${settings.autoPlay ? 'checked' : ''}>
                        Auto-play ink stories in chat
                    </label>
                    <label class="checkbox_label">
                        <input id="inky_show_buttons" type="checkbox" ${settings.showChoicesAsButtons ? 'checked' : ''}>
                        Show choices as buttons
                    </label>
                    <label class="checkbox_label">
                        <input id="inky_system_prompt" type="checkbox" ${settings.injectStoryAsSystemPrompt ? 'checked' : ''}>
                        Inject story state as system prompt
                    </label>
                    <div class="inky-actions">
                        <button id="inky_test_connection" class="menu_button">Test Connection</button>
                        <button id="inky_list_sessions" class="menu_button">List Sessions</button>
                    </div>
                    <div id="inky_status" class="inky-status"></div>
                </div>
            </div>
        </div>`;

    document.getElementById('extensions_settings').insertAdjacentHTML('beforeend', html);

    // Event handlers
    document.getElementById('inky_server_url')?.addEventListener('change', (e) => {
        settings.mcpServerUrl = e.target.value;
        saveSettingsDebounced();
    });

    document.getElementById('inky_auto_play')?.addEventListener('change', (e) => {
        settings.autoPlay = e.target.checked;
        saveSettingsDebounced();
    });

    document.getElementById('inky_show_buttons')?.addEventListener('change', (e) => {
        settings.showChoicesAsButtons = e.target.checked;
        saveSettingsDebounced();
    });

    document.getElementById('inky_system_prompt')?.addEventListener('change', (e) => {
        settings.injectStoryAsSystemPrompt = e.target.checked;
        saveSettingsDebounced();
    });

    document.getElementById('inky_test_connection')?.addEventListener('click', async () => {
        const status = document.getElementById('inky_status');
        try {
            const resp = await fetch(`${settings.mcpServerUrl}/health`);
            const data = await resp.json();
            status.textContent = `Connected: ${JSON.stringify(data)}`;
            status.className = 'inky-status connected';
        } catch (err) {
            status.textContent = `Failed: ${err.message}`;
            status.className = 'inky-status error';
        }
    });

    document.getElementById('inky_list_sessions')?.addEventListener('click', async () => {
        const result = await callMcpTool('list_sessions');
        const status = document.getElementById('inky_status');
        status.textContent = `Sessions: ${JSON.stringify(result.sessions || [])}`;
    });
}

// ── Extension Lifecycle ──

jQuery(async () => {
    renderSettings();
    console.log('[Inky] Extension loaded');
});
