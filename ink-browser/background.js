/**
 * Inky PWA Browser Extension — Background Service Worker
 *
 * Manages connection to the Inky MCP server and provides:
 *   - Story session management
 *   - MCP tool invocation (compile, play, debug)
 *   - Side panel / popup communication
 *   - Offline ink playback via cached inkjs
 */

const DEFAULT_SERVER = 'http://localhost:3001';

// Connection state
let serverUrl = DEFAULT_SERVER;
let connected = false;

// Initialize
chrome.runtime.onInstalled.addListener(() => {
  chrome.storage.local.set({
    serverUrl: DEFAULT_SERVER,
    sessions: [],
    lastStory: null
  });
});

// Message handler for popup/sidepanel communication
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  handleMessage(message).then(sendResponse).catch(err => {
    sendResponse({ error: err.message });
  });
  return true; // async response
});

async function handleMessage(msg) {
  switch (msg.type) {
    case 'connect':
      return await connectToServer(msg.url);
    case 'health':
      return await checkHealth();
    case 'compile':
      return await callTool('compile_ink', { source: msg.source });
    case 'start_story':
      return await callTool('start_story', { source: msg.source });
    case 'choose':
      return await callTool('choose', {
        session_id: msg.sessionId,
        choice_index: msg.choiceIndex
      });
    case 'continue':
      return await callTool('continue_story', { session_id: msg.sessionId });
    case 'list_sessions':
      return await callTool('list_sessions', {});
    case 'list_services':
      return await callTool('list_services', {});
    case 'connect_service':
      return await callTool('connect_service', {
        service_id: msg.serviceId,
        api_key: msg.apiKey,
        model: msg.model
      });
    default:
      return { error: `Unknown message type: ${msg.type}` };
  }
}

async function connectToServer(url) {
  serverUrl = url || DEFAULT_SERVER;
  try {
    const resp = await fetch(`${serverUrl}/health`);
    const data = await resp.json();
    connected = data.status === 'ok';
    chrome.storage.local.set({ serverUrl, connected });
    return { connected, serverUrl, ...data };
  } catch (err) {
    connected = false;
    return { connected: false, error: err.message };
  }
}

async function checkHealth() {
  try {
    const resp = await fetch(`${serverUrl}/health`);
    const data = await resp.json();
    connected = data.status === 'ok';
    return { connected, ...data };
  } catch {
    connected = false;
    return { connected: false };
  }
}

async function callTool(toolName, args) {
  const resp = await fetch(`${serverUrl}/message`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      jsonrpc: '2.0',
      id: Date.now(),
      method: 'tools/call',
      params: { name: toolName, arguments: args }
    })
  });
  return await resp.json();
}
