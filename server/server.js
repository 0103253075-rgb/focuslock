import express from 'express';
import http from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import cors from 'cors';
import os from 'os';
import path from 'path';
import { fileURLToPath } from 'url';
import QRCode from 'qrcode';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const distPath = path.join(__dirname, '../dashboard/dist');

const app = express();
app.use(cors());
app.use(express.json());
app.use(express.static(distPath));

const server = http.createServer(app);
const wss = new WebSocketServer({ server });

// Helper to get local network IPv4 address (prioritizes active Wi-Fi)
function getLocalIpAddress() {
  const interfaces = os.networkInterfaces();
  // Check Wi-Fi interface first
  for (const name of Object.keys(interfaces)) {
    if (/wi-?fi/i.test(name)) {
      for (const net of interfaces[name]) {
        if (net.family === 'IPv4' && !net.internal) {
          return net.address;
        }
      }
    }
  }
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name]) {
      if (net.family === 'IPv4' && !net.internal && !net.address.startsWith('192.168.137.')) {
        return net.address;
      }
    }
  }
  return '192.168.29.197';
}

const LOCAL_IP = getLocalIpAddress();
const PORT = 4000;

// Application State
const state = {
  isLocked: false,
  sessionDurationMinutes: 45,
  remainingSeconds: 0,
  lockReason: 'Deep Work Session',
  masterPin: '884422', // Emergency fail-safe PIN
  accountabilityPrompt: 'Did you make your parents proud today?',
  motivationalQuotes: [
    'Did you make your parents proud today?',
    'Every minute you waste scrolling, someone else is taking your dream.',
    'Your future self is watching you right now through memories.',
    'Comfort is the enemy of greatness. Finish what you started.',
    'Imagine looking back 5 years from now and regretting this exact moment.'
  ],
  device: {
    connected: false,
    deviceName: 'Not Paired',
    batteryLevel: 85,
    isCharging: false,
    lastSeen: null,
    ip: null
  },
  callHistory: [],
  activityLogs: [
    {
      id: 'log-1',
      timestamp: new Date().toLocaleTimeString(),
      type: 'system',
      message: 'FocusLock Sync Bridge initialized.'
    }
  ]
};

let timerInterval = null;

function addLog(type, message) {
  const entry = {
    id: `log-${Date.now()}-${Math.random().toString(36).substr(2, 4)}`,
    timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
    type,
    message
  };
  state.activityLogs.unshift(entry);
  if (state.activityLogs.length > 50) state.activityLogs.pop();
}

function broadcastState() {
  const payload = JSON.stringify({
    type: 'STATE_UPDATE',
    state: {
      ...state,
      serverIp: LOCAL_IP,
      serverPort: PORT
    }
  });

  wss.clients.forEach((client) => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(payload);
    }
  });
}

function startTimer() {
  if (timerInterval) clearInterval(timerInterval);
  timerInterval = setInterval(() => {
    if (state.isLocked) {
      if (state.remainingSeconds > 0) {
        state.remainingSeconds--;
        broadcastState();
      } else {
        // Auto-unlock (Dead Man's Fail-safe)
        state.isLocked = false;
        clearInterval(timerInterval);
        addLog('unlock', '⏰ Focus session completed! Phone auto-unlocked safely.');
        broadcastState();
      }
    } else {
      clearInterval(timerInterval);
    }
  }, 1000);
}

// WebSocket connections
wss.on('connection', (ws, req) => {
  const remoteIp = req.socket.remoteAddress;
  console.log(`[WS] Client connected from ${remoteIp}`);

  // Send current state immediately on connect
  ws.send(JSON.stringify({
    type: 'STATE_UPDATE',
    state: {
      ...state,
      serverIp: LOCAL_IP,
      serverPort: PORT
    }
  }));

  ws.on('message', (message) => {
    try {
      const data = JSON.parse(message);
      
      // Phone Heartbeat
      if (data.type === 'DEVICE_HEARTBEAT') {
        state.device = {
          connected: true,
          deviceName: data.deviceName || 'Android Device',
          batteryLevel: data.batteryLevel ?? state.device.batteryLevel,
          isCharging: Boolean(data.isCharging),
          lastSeen: Date.now(),
          ip: remoteIp
        };
        broadcastState();
      }

      // Incoming Call Interception report from Android Phone
      if (data.type === 'CALL_EVENT') {
        const { callerNumber, callerName, isRepeatCaller, action } = data;
        const entry = {
          id: `call-${Date.now()}`,
          timestamp: new Date().toLocaleTimeString(),
          caller: callerName || callerNumber || 'Unknown Caller',
          isRepeatCaller: Boolean(isRepeatCaller),
          action: action // 'MUTED' or 'RING_ALLOWED'
        };
        state.callHistory.unshift(entry);
        
        if (isRepeatCaller) {
          addLog('emergency_call', `🚨 Emergency bypass: ${entry.caller} called twice! Phone is RINGING.`);
        } else {
          addLog('call', `🔕 Call muted from ${entry.caller} (First attempt - silenced).`);
        }
        broadcastState();
      }

      // Emergency Unlock from phone Master PIN
      if (data.type === 'PHONE_PIN_UNLOCK') {
        if (data.pin === state.masterPin) {
          state.isLocked = false;
          if (timerInterval) clearInterval(timerInterval);
          addLog('emergency_unlock', '🔑 Emergency Master PIN entered on phone. Lockdown dismissed.');
          broadcastState();
        }
      }
    } catch (err) {
      console.error('[WS] Error processing message:', err);
    }
  });

  ws.on('close', () => {
    console.log('[WS] Client disconnected');
  });
});

// REST Endpoints
app.get('/api/status', (req, res) => {
  res.json({
    success: true,
    state: {
      ...state,
      serverIp: LOCAL_IP,
      serverPort: PORT
    }
  });
});

// QR Code for one-click phone pairing
app.get('/api/pair-qr', async (req, res) => {
  try {
    // Primary: Cloudflare Global HTTPS Tunnel (Works on any phone/network without firewall issues)
    const tunnelUrl = 'https://catalogs-alex-voted-invision.trycloudflare.com/?mode=phone';
    const pairUrl = tunnelUrl;
    const qrDataUrl = await QRCode.toDataURL(pairUrl, {
      margin: 1,
      color: {
        dark: '#00F0FF',
        light: '#09090b'
      }
    });
    res.json({ success: true, qr: qrDataUrl, url: pairUrl, tunnelUrl, ip: LOCAL_IP, port: PORT });
  } catch (err) {
    res.status(500).json({ error: 'Failed to generate QR code' });
  }
});

// SPA fallback
app.get('*', (req, res, next) => {
  if (req.path.startsWith('/api')) return next();
  res.sendFile(path.join(distPath, 'index.html'));
});

// Trigger Lock from PC
app.post('/api/lock', (req, res) => {
  const { durationMinutes = 45, reason = 'Deep Work Session' } = req.body;
  state.isLocked = true;
  state.sessionDurationMinutes = durationMinutes;
  state.remainingSeconds = durationMinutes * 60;
  state.lockReason = reason;

  // Pick a fresh motivational prompt
  const randIdx = Math.floor(Math.random() * state.motivationalQuotes.length);
  state.accountabilityPrompt = state.motivationalQuotes[randIdx];

  startTimer();
  addLog('lock', `🔒 Focus Lock ACTIVATED for ${durationMinutes} minutes. Phone is now locked.`);
  broadcastState();

  res.json({ success: true, state });
});

// Trigger Unlock from PC (after passing friction modal)
app.post('/api/unlock', (req, res) => {
  const { reason = 'Completed session early' } = req.body;
  state.isLocked = false;
  if (timerInterval) clearInterval(timerInterval);

  addLog('unlock', `🔓 Focus Lock deactivated from laptop (${reason}).`);
  broadcastState();

  res.json({ success: true, state });
});

// Emergency Unlock (zero friction, immediate fail-safe)
app.post('/api/emergency-unlock', (req, res) => {
  state.isLocked = false;
  if (timerInterval) clearInterval(timerInterval);

  addLog('emergency_unlock', '⚠️ Instant Emergency Unlock triggered from dashboard.');
  broadcastState();

  res.json({ success: true, state });
});

// Device Simulation Endpoint (for testing before physical Android pairing)
app.post('/api/simulate-device', (req, res) => {
  const { connected = true, battery = 88, isCharging = false, deviceName = "Pixel 8 Pro" } = req.body;
  state.device = {
    connected,
    deviceName,
    batteryLevel: battery,
    isCharging,
    lastSeen: Date.now(),
    ip: '192.168.1.105'
  };
  addLog('system', `📱 Device '${deviceName}' connected and synced.`);
  broadcastState();
  res.json({ success: true, device: state.device });
});

// Simulate Incoming Call (for testing the Repeat-Caller filter)
app.post('/api/simulate-call', (req, res) => {
  const { caller = "Mom", number = "+91 98765 43210" } = req.body;
  
  // Check if called within last 5 minutes
  const now = Date.now();
  const recentCall = state.callHistory.find(
    (c) => c.caller === caller && (now - (c.timeMs || now)) < 300000
  );

  const isRepeat = Boolean(recentCall);
  const entry = {
    id: `call-${now}`,
    timeMs: now,
    timestamp: new Date().toLocaleTimeString(),
    caller,
    number,
    isRepeatCaller: isRepeat,
    action: isRepeat ? 'RING_ALLOWED' : 'MUTED'
  };

  state.callHistory.unshift(entry);
  if (isRepeat) {
    addLog('emergency_call', `🚨 Emergency Bypass Triggered: ${caller} called twice! Ringer sound ACTIVATED.`);
  } else {
    addLog('call', `🔕 Incoming call from ${caller} - Muted silently (1st call).`);
  }

  broadcastState();
  res.json({ success: true, call: entry });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`\n======================================================`);
  console.log(`🛡️  FocusLock Real-time Sync Server Active!`);
  console.log(`📡 Local Wi-Fi IP: http://${LOCAL_IP}:${PORT}`);
  console.log(`🌐 Localhost:     http://localhost:${PORT}`);
  console.log(`======================================================\n`);
});
