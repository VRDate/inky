/**
 * Inky PWA Popup — Quick player and server connector.
 */
const $ = (sel) => document.querySelector(sel);
let currentSessionId = null;

// Init
document.addEventListener('DOMContentLoaded', async () => {
  const stored = await chrome.storage.local.get(['serverUrl', 'connected']);
  if (stored.serverUrl) $('#serverUrl').value = stored.serverUrl;

  // Auto-connect check
  const resp = await chrome.runtime.sendMessage({ type: 'health' });
  updateStatus(resp.connected);

  // Button handlers
  $('#connectBtn').addEventListener('click', async () => {
    const url = $('#serverUrl').value.trim();
    const resp = await chrome.runtime.sendMessage({ type: 'connect', url });
    updateStatus(resp.connected);
  });

  $('#openPanelBtn').addEventListener('click', () => {
    if (chrome.sidePanel) {
      chrome.sidePanel.open({ windowId: chrome.windows?.WINDOW_ID_CURRENT });
    }
  });

  $('#compileBtn').addEventListener('click', async () => {
    const source = $('#inkSource').value;
    if (!source.trim()) return;
    const resp = await chrome.runtime.sendMessage({ type: 'compile', source });
    showOutput(JSON.stringify(resp, null, 2));
  });

  $('#playBtn').addEventListener('click', async () => {
    const source = $('#inkSource').value;
    if (!source.trim()) return;
    const resp = await chrome.runtime.sendMessage({ type: 'start_story', source });
    handleStoryResponse(resp);
  });
});

function updateStatus(connected) {
  $('#statusDot').classList.toggle('connected', connected);
  $('#statusText').textContent = connected ? 'Connected' : 'Disconnected';
}

function showOutput(text) {
  $('#outputSection').style.display = 'block';
  $('#output').textContent = text;
}

function handleStoryResponse(resp) {
  if (!resp || resp.error) {
    showOutput(resp?.error || 'Error');
    return;
  }

  // Parse MCP response
  let data;
  try {
    const content = resp.result?.content?.[0]?.text || JSON.stringify(resp);
    data = JSON.parse(content);
  } catch {
    data = resp;
  }

  currentSessionId = data.session_id;
  showOutput(data.text || '');

  // Render choices
  const choicesEl = $('#choices');
  choicesEl.innerHTML = '';
  if (data.choices && data.choices.length > 0) {
    data.choices.forEach(choice => {
      const btn = document.createElement('button');
      btn.className = 'choice-btn';
      btn.textContent = `${choice.index + 1}. ${choice.text}`;
      btn.addEventListener('click', async () => {
        const resp = await chrome.runtime.sendMessage({
          type: 'choose',
          sessionId: currentSessionId,
          choiceIndex: choice.index
        });
        handleStoryResponse(resp);
      });
      choicesEl.appendChild(btn);
    });
  }
}
