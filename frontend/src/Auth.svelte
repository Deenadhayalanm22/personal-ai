<script>
  import { onDestroy } from 'svelte';
  import { ApiError, requestLoginLink } from './lib/api.js';

  let phoneNumber = '';
  let state = 'form';
  let error = '';
  let genericMessage = '';
  let seconds = 0;
  let timer;
  let resending = false;
  const signedOutMessage = new URLSearchParams(location.search).get('message');

  const validPhone = (value) => /^\+[1-9]\d{7,14}$/.test(value.replace(/[\s()-]/g, ''));
  function startCountdown() {
    clearInterval(timer); seconds = 60;
    timer = setInterval(() => { seconds -= 1; if (seconds <= 0) clearInterval(timer); }, 1000);
  }
  async function submit() {
    error = '';
    if (!phoneNumber.trim()) { error = 'Enter your phone number, including the country code.'; return; }
    if (!validPhone(phoneNumber)) { error = 'Enter a valid international number, such as +91 98765 43210.'; return; }
    state = 'submitting';
    try {
      const result = await requestLoginLink(phoneNumber.trim());
      genericMessage = result?.message || 'If this number is registered, we sent a login link to its WhatsApp account.';
      state = 'sent'; startCountdown();
    } catch (cause) {
      state = 'form';
      error = !navigator.onLine ? 'You appear to be offline. Check your connection and try again.' : 'We couldn’t send the link right now. Please try again shortly.';
    }
  }
  function reset() { clearInterval(timer); phoneNumber = ''; error = ''; genericMessage = ''; state = 'form'; }
  async function resend() { if (seconds > 0 || resending) return; resending = true; try { const result = await requestLoginLink(phoneNumber.trim()); genericMessage = result?.message || genericMessage; startCountdown(); } catch (cause) { error = !navigator.onLine ? 'You appear to be offline. Check your connection and try again.' : 'We couldn’t resend the link right now. Please try again shortly.'; } finally { resending = false; } }
  onDestroy(() => clearInterval(timer));
</script>

<main class="auth-page">
  <a class="auth-brand" href="/portal" aria-label="Expense AI portal"><span>₹</span>Expense AI</a>
  <section class="auth-card" aria-live="polite">
    {#if signedOutMessage && state === 'form'}<p class="signed-out-notice" role="status">✓ {signedOutMessage}</p>{/if}
    {#if state === 'sent'}
      <div class="auth-icon sent-icon" aria-hidden="true">✓</div>
      <p class="eyebrow">Link sent</p>
      <h1>Check your WhatsApp</h1>
      <p>If this number is registered, we sent a secure sign-in link. Open it on this device to continue.</p>
      <p class="generic-message" role="status">{genericMessage}</p>
      <div class="security-note"><span aria-hidden="true">↗</span><p>Links expire shortly and can only be used once.</p></div>
      {#if error}<p class="auth-error" role="alert">{error}</p>{/if}
      <button class="auth-primary" on:click={resend} disabled={seconds > 0 || resending}>{resending ? 'Resending…' : seconds > 0 ? `Resend link in ${seconds}s` : 'Resend link'}</button>
      <button class="auth-secondary" on:click={reset}>Use another number</button>
    {:else}
      <div class="auth-icon" aria-hidden="true">↗</div>
      <p class="eyebrow">Private access</p>
      <h1>Sign in to your portal</h1>
      <p>Enter the phone number connected to your WhatsApp account. We’ll send you a secure sign-in link.</p>
      <form on:submit|preventDefault={submit} novalidate>
        <label for="phone">Phone number</label>
        <div class:error-field={error} class="phone-field"><input id="phone" type="tel" inputmode="tel" autocomplete="tel" bind:value={phoneNumber} placeholder="+91 98765 43210" aria-describedby="phone-hint phone-error" aria-invalid={Boolean(error)} on:input={() => error = ''} /></div>
        <small id="phone-hint">Include your country code, for example +91.</small>
        {#if error}<p id="phone-error" class="auth-error" role="alert">{error}</p>{/if}
        <button class="auth-primary" type="submit" disabled={state === 'submitting'}>{state === 'submitting' ? 'Sending securely…' : 'Send link on WhatsApp'}</button>
      </form>
      <p class="privacy-copy">We’ll only use this number to securely find your account. We never reveal whether a number is registered.</p>
    {/if}
  </section>
  <p class="auth-footer"><span></span>Protected with secure, cookie-based access</p>
</main>
