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
  {#if state === 'recording'}
    <span role="status" class="recording">● Recording · {seconds}s / 30s</span>
    <button type="button" on:click={finish}>Stop and transcribe</button>
    <button type="button" on:click={cancel}>Cancel</button>
  {:else if state === 'permission' || state === 'transcribing'}
    <span role="status">{state === 'permission' ? 'Waiting for microphone permission…' : 'Transcribing your voice…'}</span>
    <button type="button" on:click={cancel}>Cancel</button>
  {:else}
    <button type="button" class="voice-button" aria-label="Record voice question" {disabled} on:click={start}>
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8"/></svg>Voice
    </button>
    {#if state === 'retry'}<button type="button" {disabled} on:click={transcribe}>Retry transcription</button><button type="button" on:click={cancel}>Discard recording</button>{/if}
  {/if}
  {#if error}<p role="alert">{error}</p>{/if}
</div>

<style>
  .voice-question{display:flex;align-items:center;flex-wrap:wrap;gap:8px;margin-bottom:8px;font-size:12px;color:#52685c}.voice-question button{display:inline-flex;align-items:center;gap:6px;min-height:36px;padding:7px 10px;border:1px solid #d4dfcf;border-radius:9px;background:#f5f7f1;color:#315b43;font:inherit;cursor:pointer}.voice-question button:disabled{opacity:.45;cursor:default}.recording{color:#a33e36}.voice-question p{flex-basis:100%;margin:0;color:#975336;line-height:1.5}
</style>
