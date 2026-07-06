'use client';

import { useState } from 'react';
import { chat, streamChat } from './lib/api';

const TEAMS = [
  { key: 'sk-enterprise-key', label: 'ACME Enterprise', hint: 'all models · $25/day · compliance enrichment' },
  { key: 'sk-internal-key', label: 'Internal Tools', hint: 'llama3.1 · gemma3:1b · $5/day' },
  { key: 'sk-free-key', label: 'Free Tier', hint: 'gemma3:1b only · 5 rpm · easy to throttle' },
  { key: '__custom__', label: 'Custom key…', hint: 'enter any team API key' },
];
const MODELS = ['llama3.1', 'gemma3', 'gemma3:1b'];

export default function Playground() {


  return (
   
  );
}
