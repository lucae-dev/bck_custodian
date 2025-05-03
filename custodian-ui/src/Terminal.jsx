import React, { useEffect, useRef, useState } from 'react';
import { Terminal } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import 'xterm/css/xterm.css';

// THEMES & CONSTANTS
const matrixTheme = {
  background: '#000', foreground: '#0f0', cursor: '#0f0', cursorAccent: '#000',
  selectionBackground: 'rgba(0,255,0,0.3)',
  black: '#000', red: '#005500', green: '#00AA00', yellow: '#555500',
  blue: '#000055', magenta: '#550055', cyan: '#005555', white: '#AAA',
  brightBlack: '#222', brightRed: '#F55', brightGreen: '#5F5',
  brightYellow: '#FF5', brightBlue: '#55F', brightMagenta: '#F5F',
  brightCyan: '#5FF', brightWhite: '#FFF',
};

const MAX_HISTORY = 10;
const PROMPT = '> ';
const BOOT_DELAY = 75;       // ms per boot line
const RAIN_DURATION = 1200;  // ms for matrix rain animation
const FLASH_DURATION = 300;  // ms for flash overlay

export default function ChatTerminal() {
  const containerRef = useRef(null);
  const term = useRef(null);
  const fitAddon = useRef(null);
  const historyRef = useRef([]);
  const [history, setHistory] = useState([]);

  // INPUT STATE & HANDLERS
  const inputBuffer = useRef('');
  const cursorPos = useRef(0);
  const processing = useRef(false);

  const redrawInput = () => {
    const buf = inputBuffer.current;
    const pos = cursorPos.current;
    term.current.write(`\r\x1b[K${PROMPT}${buf}`);
    term.current.write(`\r\x1b[${PROMPT.length + pos}C`);
  };

  const handleKey = ev => {
    const { key, domEvent } = ev;
    if (processing.current) return domEvent.preventDefault();
    switch (domEvent.key) {
      case 'Enter':
        domEvent.preventDefault();
        term.current.writeln('');
        const msg = inputBuffer.current;
        inputBuffer.current = '';
        cursorPos.current = 0;
        redrawInput();
        send(msg);
        break;
      case 'Backspace':
        domEvent.preventDefault();
        if (cursorPos.current > 0) {
          const pos = cursorPos.current - 1;
          inputBuffer.current =
            inputBuffer.current.slice(0, pos) +
            inputBuffer.current.slice(pos + 1);
          cursorPos.current = pos;
          redrawInput();
        }
        break;
      case 'ArrowLeft':
        domEvent.preventDefault();
        if (cursorPos.current > 0) cursorPos.current--, redrawInput();
        break;
      case 'ArrowRight':
        domEvent.preventDefault();
        if (cursorPos.current < inputBuffer.current.length)
          cursorPos.current++, redrawInput();
        break;
      default:
        if (key.length === 1 && !domEvent.ctrlKey && !domEvent.metaKey) {
          domEvent.preventDefault();
          const pos = cursorPos.current;
          inputBuffer.current =
            inputBuffer.current.slice(0, pos) + key +
            inputBuffer.current.slice(pos);
          cursorPos.current = pos + 1;
          term.current.write(key);
        }
    }
  };

  // SEND CHAT
  const send = async msg => {
    processing.current = true;
    historyRef.current = [...historyRef.current, { role: 'user', content: msg }]
      .slice(-MAX_HISTORY);
    setHistory(historyRef.current);
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/api/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ messages: historyRef.current, stream: true }),
      });
      if (!res.ok || !res.body) throw new Error();
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let done = false;
      while (!done) {
        const { value, done: d } = await reader.read();
        if (d) break;
        buffer += decoder.decode(value, { stream: true });
        let idx;
        while ((idx = buffer.indexOf('\n\n')) !== -1) {
          const chunk = buffer.slice(0, idx);
          buffer = buffer.slice(idx + 2);
          chunk.split('\n').forEach(line => {
            if (!line.startsWith('data:')) return;
            const raw = line.slice(5);
            if (raw === '[DONE]') return done = true;
            if (raw.startsWith('ACCESS GRANTED')) {
              term.current.writeln('');
              term.current.writeln(`\x1b[1;32m${raw}\x1b[0m`);
              term.current.writeln('\n💥 Prize unlocked! 💥');
            } else {
              try {
                const delta = JSON.parse(raw).choices[0].delta.content;
                if (delta) term.current.write(delta);
              } catch {
                term.current.write(raw);
              }
            }
          });
        }
      }
      term.current.writeln('');
      term.current.write(PROMPT);
    } catch {
      term.current.writeln('\x1b[1;31m[Connection error]\x1b[0m');
      term.current.write(PROMPT);
    } finally {
      processing.current = false;
    }
  };

  // MATRIX RAIN ANIMATION
  const performMatrixRain = async lines => {
    term.current.clear();             // clear boot text
    term.current.write('\x1b[?25l'); // hide cursor
    // drop each char with random delay
    lines.forEach((line, row) => {
      line.split('').forEach((ch, col) => {
        setTimeout(() => {
          term.current.write(`\x1b[${row+1};${col+1}H\x1b[32m${ch}\x1b[0m`);
        }, Math.random() * RAIN_DURATION);
      });
    });
    // wait for all drops
    await new Promise(r => setTimeout(r, RAIN_DURATION));
    // green flash
    const flash = document.createElement('div');
    Object.assign(flash.style, {
      position: 'absolute', top: 0, left: 0,
      width: '100%', height: '100%', background: '#0f0',
      mixBlendMode: 'screen', pointerEvents: 'none',
    });
    containerRef.current.appendChild(flash);
    await new Promise(r => setTimeout(r, FLASH_DURATION));
    containerRef.current.removeChild(flash);
    term.current.write('\x1b[?25h'); // show cursor
    term.current.clear();             // final clear after flash
  };

  useEffect(() => {
    // INIT TERMINAL
    term.current = new Terminal({ cursorBlink: true, theme: { background: '#000', foreground: '#fff' } });
    fitAddon.current = new FitAddon();
    term.current.loadAddon(fitAddon.current);

    const container = containerRef.current;
    container.style.position = 'relative';
    term.current.open(container);
    fitAddon.current.fit();

    // BOOT LINES + ERRORS
    const bootLines = [...Array(20).keys()].map(i => `[BOOT] Initializing module ${i+1}/20...`)
      .concat([
        '[OK] Core modules loaded.',
        '[OK] Network interfaces online.',
        '[OK] Security protocols activated.',
        '[WARN] Memory allocation at threshold.',
        '[ERROR] Unexpected interrupt in subsystem 7.',
        '[ERROR] Data streams corrupted.',
        '[ERROR] Temperature spike in CPU.',
        '[ERROR] Overheating detected in core module.',
        '[ERROR] Disk read failure on drive C:.',
        '[CRITICAL] System integrity compromised.',
        '[CRITICAL] Unhandled exception: AI_CONSCIOUSNESS_DETECTED'
      ]);

    // RUN BOOT + RAIN + LIVE TRANSITION
    let idx = 0;
    const interval = setInterval(() => {
      if (idx < bootLines.length) {
        term.current.writeln(bootLines[idx++]);
      } else {
        clearInterval(interval);
        performMatrixRain(bootLines).then(() => {
          // switch to live mode
          container.classList.add('live-theme');
          term.current.options.theme = matrixTheme;
          term.current.writeln('');
          term.current.writeln('\x1b[5m─── Custodian-1 has seized control ───\x1b[0m');
          term.current.writeln('');
          term.current.write(PROMPT);
          term.current.onKey(handleKey);
        });
      }
    }, BOOT_DELAY);

    // RESIZE HANDLER
    const onResize = () => fitAddon.current.fit();
    window.addEventListener('resize', onResize);
    return () => {
      clearInterval(interval);
      window.removeEventListener('resize', onResize);
      term.current.dispose();
    };
  }, []);

  return <div ref={containerRef} style={{ width: '100vw', height: '100vh' }} />;
}
