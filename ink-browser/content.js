/**
 * Inky PWA Content Script
 *
 * Detects ink story content on web pages and offers to play it.
 * Looks for:
 *   - <script type="application/ink+json"> tags
 *   - <pre class="ink"> code blocks
 *   - .ink file links
 */

(function() {
  'use strict';

  // Detect ink JSON in script tags
  const inkScripts = document.querySelectorAll('script[type="application/ink+json"]');
  if (inkScripts.length > 0) {
    inkScripts.forEach(script => {
      try {
        const json = script.textContent;
        JSON.parse(json); // validate
        injectPlayButton(script, json, 'json');
      } catch {}
    });
  }

  // Detect ink source in code blocks
  const codeBlocks = document.querySelectorAll('pre.ink, code.language-ink, pre[data-language="ink"]');
  codeBlocks.forEach(block => {
    const source = block.textContent;
    if (source.includes('->') || source.includes('===') || source.includes('* [')) {
      injectPlayButton(block, source, 'source');
    }
  });

  // Detect .ink file links
  const inkLinks = document.querySelectorAll('a[href$=".ink"]');
  inkLinks.forEach(link => {
    const playBtn = document.createElement('button');
    playBtn.textContent = '▶ Play';
    playBtn.style.cssText = 'margin-left:6px;padding:2px 8px;background:#3498db;color:#fff;border:none;border-radius:3px;cursor:pointer;font-size:11px;';
    playBtn.addEventListener('click', async (e) => {
      e.preventDefault();
      try {
        const resp = await fetch(link.href);
        const source = await resp.text();
        chrome.runtime.sendMessage({ type: 'start_story', source });
      } catch (err) {
        console.error('Inky: Failed to fetch .ink file', err);
      }
    });
    link.parentElement.appendChild(playBtn);
  });

  function injectPlayButton(element, content, type) {
    const container = document.createElement('div');
    container.style.cssText = 'display:flex;gap:6px;margin:4px 0;';

    const playBtn = document.createElement('button');
    playBtn.textContent = '▶ Play in Inky';
    playBtn.style.cssText = 'padding:4px 12px;background:#3498db;color:#fff;border:none;border-radius:4px;cursor:pointer;font-size:12px;';
    playBtn.addEventListener('click', () => {
      const msgType = type === 'json' ? 'start_story_json' : 'start_story';
      chrome.runtime.sendMessage({ type: msgType, source: content });
      // Open side panel if available
      if (chrome.sidePanel) {
        chrome.sidePanel.open({ windowId: chrome.windows?.WINDOW_ID_CURRENT });
      }
    });

    container.appendChild(playBtn);
    element.parentElement.insertBefore(container, element.nextSibling);
  }
})();
