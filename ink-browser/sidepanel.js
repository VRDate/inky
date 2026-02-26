/**
 * Inky PWA Side Panel — Full editor + player + debug.
 */
const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);
let sessionId = null;
let storyHistory = '';

// Tab switching
$$('.tab').forEach(tab => {
  tab.addEventListener('click', () => {
    $$('.tab').forEach(t => t.classList.remove('active'));
    $$('.panel').forEach(p => p.classList.remove('active'));
    tab.classList.add('active');
    document.getElementById(tab.dataset.panel).classList.add('active');
  });
});

// Compile & Run
$('#compileRunBtn').addEventListener('click', async () => {
  const source = $('#editor').value;
  if (!source.trim()) return;

  storyHistory = '';
  const resp = await chrome.runtime.sendMessage({ type: 'start_story', source });
  handleStory(resp);

  // Switch to player tab
  $$('.tab')[1].click();
});

// Reset
$('#resetBtn').addEventListener('click', () => {
  sessionId = null;
  storyHistory = '';
  $('#storyText').innerHTML = '';
  $('#storyChoices').innerHTML = '';
  $('#debugSession').textContent = 'No active session';
  $('#debugVars').innerHTML = '';
  $('#debugTrace').innerHTML = '';
});

function handleStory(resp) {
  let data;
  try {
    const content = resp?.result?.content?.[0]?.text || JSON.stringify(resp);
    data = JSON.parse(content);
  } catch {
    data = resp;
  }

  if (data.error) {
    $('#storyText').innerHTML = `<p style="color:red">${data.error}</p>`;
    return;
  }

  sessionId = data.session_id || sessionId;

  // Append text
  if (data.text) {
    storyHistory += data.text;
    const paragraphs = storyHistory.split('\n').filter(l => l.trim()).map(l => `<p>${l}</p>`).join('');
    $('#storyText').innerHTML = paragraphs;
    $('#storyText').scrollTop = $('#storyText').scrollHeight;
  }

  // Render choices
  const choicesEl = $('#storyChoices');
  choicesEl.innerHTML = '';
  if (data.choices?.length > 0) {
    data.choices.forEach(choice => {
      const btn = document.createElement('button');
      btn.className = 'choice';
      btn.textContent = choice.text;
      btn.addEventListener('click', async () => {
        storyHistory += `> ${choice.text}\n`;
        const resp = await chrome.runtime.sendMessage({
          type: 'choose',
          sessionId,
          choiceIndex: choice.index
        });
        handleStory(resp);
      });
      choicesEl.appendChild(btn);
    });
  } else if (!data.can_continue) {
    choicesEl.innerHTML = '<p style="color:#999;text-align:center;margin-top:20px;">— The End —</p>';
  }

  // Update debug info
  $('#debugSession').textContent = `Session: ${sessionId}`;
}
