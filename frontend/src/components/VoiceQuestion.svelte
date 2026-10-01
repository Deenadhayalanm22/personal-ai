<!-- FIN-EPIC-003: docs/jira/personal-expense/FIN-EPIC-003-insights.md -->
<script>
  import { onDestroy } from 'svelte';
  import { transcribeMoneyVoice } from '../lib/api.js';
  export let disabled = false;
  export let busy = false;
  export let onTranscript;
  let state = 'idle', error = '', seconds = 0;
  let recorder, stream, chunks = [], recording, timer, limitTimer, controller;
  let recordingStartedAt = 0;
  const MAX_RECORDING_MS = 30_000;
  let generation = 0, discarded = false;
  $: busy = ['permission', 'recording', 'transcribing'].includes(state);
  $: if (disabled && busy) cancel();
  function releaseMicrophone() {
    clearInterval(timer); clearTimeout(limitTimer);
    stream?.getTracks().forEach(track => track.stop());
    stream = null;
  }
  export function cancel() {
    generation++; discarded = true;
    controller?.abort();
    if (recorder?.state === 'recording') recorder.stop();
    releaseMicrophone(); recorder = null; chunks = []; recording = null;
    state = 'idle'; error = '';
  }
  async function start() {
    if (disabled || busy) return;
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
      error = 'Voice recording needs a supported browser and a secure HTTPS connection. You can still type your question.'; return;
    }
    cancel();
    const current = generation;
    state = 'permission'; discarded = false;
    try {
      const acquired = await navigator.mediaDevices.getUserMedia({ audio: true });
      if (current !== generation) { acquired.getTracks().forEach(track => track.stop()); return; }
      stream = acquired;
      const mime = ['audio/webm;codecs=opus', 'audio/mp4', 'audio/ogg;codecs=opus', 'audio/webm'].find(type => MediaRecorder.isTypeSupported(type));
      if (!mime) throw new Error('unsupported');
      recorder = new MediaRecorder(stream, { mimeType: mime });
      const activeRecorder = recorder;
      chunks = []; seconds = 0;
      recorder.ondataavailable = event => { if (current === generation && event.data.size) chunks.push(event.data); };
      recorder.onerror = () => {
        if (current !== generation) return;
        cancel(); error = 'Recording stopped unexpectedly. Please try again.';
      };
      recorder.onstop = () => {
        if (current !== generation || discarded) return;
        releaseMicrophone();
        recording = new Blob(chunks, { type: activeRecorder.mimeType });
        chunks = []; recorder = null;
        transcribe();
      };
      recorder.start(1000); state = 'recording'; recordingStartedAt = performance.now();
      timer = setInterval(() => {
        seconds = Math.floor((performance.now() - recordingStartedAt) / 1000);
        if (seconds >= 30) recordingLimitReached();
      }, 1000);
      limitTimer = setTimeout(recordingLimitReached, MAX_RECORDING_MS);
    } catch (cause) {
      if (current !== generation) return;
      releaseMicrophone(); state = 'idle';
      error = cause.name === 'NotAllowedError' ? 'Microphone access was denied. Allow it in your browser settings, or type your question.'
        : cause.name === 'NotFoundError' ? 'No microphone was found. You can type your question.' : 'Could not start recording in this browser. Please try again or type your question.';
    }
  }
  function recordingLimitReached() {
    cancel();
    error = 'Recording reached the 30-second limit. Please re-record a shorter question.';
  }
  function finish() {
    if (recorder?.state !== 'recording') return;
    if (performance.now() - recordingStartedAt >= MAX_RECORDING_MS) { recordingLimitReached(); return; }
    state = 'transcribing'; clearInterval(timer); clearTimeout(limitTimer); recorder.stop();
  }
  async function transcribe() {
    if (!recording?.size) { state = 'idle'; error = 'No audio was recorded. Try again.'; return; }
    if (recording.size > 8 * 1024 * 1024) { state = 'idle'; recording = null; error = 'That recording is too large. Record a shorter question.'; return; }
    const current = generation;
    state = 'transcribing'; error = ''; controller = new AbortController();
    const timeout = setTimeout(() => controller?.abort(), 55000);
    try {
      const result = await transcribeMoneyVoice(recording, controller.signal);
      if (current !== generation) return;
      if (!result?.text?.trim() || result.text.length > 2000) throw new Error('No usable transcript returned. Please record again.');
      recording = null; state = 'idle'; onTranscript(result.text.trim());
    } catch (cause) {
      if (current !== generation) return;
      state = 'retry'; error = cause.name === 'AbortError' ? 'Transcription took too long. Try again or type your question.' : cause.message || 'Could not transcribe. Try again.';
    } finally { clearTimeout(timeout); }
  }
  onDestroy(cancel);
</script>

<div class="voice-question">
  <button type="button" class="voice-button" class:active={state === 'recording'}
    aria-label={state === 'recording' ? 'Stop and transcribe' : 'Record voice question'}
    title={state === 'recording' ? 'Stop and transcribe' : 'Record voice question'}
    disabled={disabled || state === 'permission' || state === 'transcribing'}
    on:click={() => state === 'recording' ? finish() : start()}>
    {#if state === 'recording'}<span class="stop-icon" aria-hidden="true"></span>
    {:else}<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8"/></svg>{/if}
  </button>
  {#if state === 'recording'}
    <div class="voice-feedback recording-feedback">
      <div class="recording-wave" aria-hidden="true">{#each [0,1,2,3,4,5,6,7,8] as bar}<i style={`--bar:${bar}`}></i>{/each}</div>
      <span role="status" class="recording"><i class="recording-dot" aria-hidden="true"></i>Recording · {seconds}s / 30s</span>
      <button type="button" class="voice-action" on:click={cancel}>Cancel</button>
      <small>Tap stop to transcribe and review.</small>
    </div>
  {:else if state === 'permission' || state === 'transcribing'}
    <div class="voice-feedback">
      <span class="voice-spinner" aria-hidden="true"></span>
      <span role="status">{state === 'permission' ? 'Waiting for microphone permission…' : 'Transcribing your voice…'}</span>
      <button type="button" class="voice-action" on:click={cancel}>Cancel</button>
    </div>
  {:else if state === 'retry' || error}
    <div class="voice-feedback">
      {#if error}<p role="alert">{error}</p>{/if}
      {#if state === 'retry'}<button type="button" class="voice-action" {disabled} on:click={transcribe}>Retry transcription</button><button type="button" class="voice-action" on:click={cancel}>Discard recording</button>{/if}
    </div>
  {/if}
</div>

<style>
  .voice-question{display:contents}
  .voice-button{grid-column:3;grid-row:1;display:grid;place-items:center;width:44px;height:44px;padding:0;border:1px solid #d4dfcf;border-radius:9px;background:#eef4ed;color:#315b43;cursor:pointer}
  .voice-button.active{border-color:#e9b8af;background:#fff0eb;color:#a33e36}
  .voice-button:disabled{opacity:.45;cursor:default}
  .stop-icon{width:14px;height:14px;border-radius:3px;background:currentColor}
  .voice-feedback{grid-column:1/-1;display:flex;align-items:center;flex-wrap:wrap;gap:8px;padding:10px 3px 2px;border-top:1px solid #e5e9df;font-size:12px;line-height:1.5;color:#52685c;min-width:0}
  .recording-feedback{color:#a33e36}
  .recording{display:inline-flex;align-items:center;gap:6px;font-variant-numeric:tabular-nums}
  .recording-dot{width:6px;height:6px;border-radius:50%;background:currentColor;animation:recording-pulse 1.2s ease-in-out infinite}
  .recording-wave{display:flex;align-items:center;justify-content:center;gap:3px;height:28px;width:51px;flex:0 0 auto}
  .recording-wave i{width:3px;height:20px;border-radius:4px;background:currentColor;transform-origin:center;animation:recording-wave .85s ease-in-out infinite alternate;animation-delay:calc(var(--bar) * -135ms)}
  .voice-action{padding:5px 7px;border:0;border-radius:6px;background:transparent;color:inherit;font:inherit;text-decoration:underline;cursor:pointer}
  .voice-feedback>.voice-action:last-of-type{margin-left:auto}
  .voice-feedback small{flex-basis:100%;font-size:11px;color:#7b8276}
  .voice-feedback p{flex-basis:100%;margin:0;color:#975336;overflow-wrap:anywhere}
  .voice-spinner{width:14px;height:14px;flex:0 0 auto;border:2px solid #d4dfcf;border-top-color:#315b43;border-radius:50%;animation:voice-spin 1s linear infinite}
  @keyframes recording-wave{from{transform:scaleY(.25)}to{transform:scaleY(1)}}
  @keyframes recording-pulse{50%{opacity:.35}}
  @keyframes voice-spin{to{transform:rotate(360deg)}}
  @media(prefers-reduced-motion:reduce){.recording-wave i,.recording-dot,.voice-spinner{animation:none}.recording-wave i:nth-child(even){transform:scaleY(.45)}}
</style>
