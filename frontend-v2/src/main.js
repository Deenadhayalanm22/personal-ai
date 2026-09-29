import { mount } from 'svelte';
import './app.css';
if (import.meta.env.VITE_V2_MODE === 'live') import('../../frontend/src/app.css');
const component = import.meta.env.VITE_V2_MODE === 'live'
  ? import('./LiveApp.svelte') : import('./App.svelte');
component.then(({ default: App }) => mount(App, { target: document.getElementById('app') }));
