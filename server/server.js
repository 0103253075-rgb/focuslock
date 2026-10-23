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

// Helper to get local network IPv4 address
function getLocalIpAddress() {
  const interfaces = os.networkInterfaces();
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
const PORT = process.env.PORT || 4000;

const MOTIVATIONAL_QUOTES = [
  'My lack of discipline is an insult to everyone who believes in me.',
  'Every minute you waste scrolling, someone else is taking your dream.',
  'Your future self is watching you right now through memories.',
  'Comfort is the enemy of greatness. Finish what you started.',
  'Did you make your parents proud today?'
];

// Multi-tenant Room Store: Map<roomCode, RoomState>
const rooms = new Map();

function getOrCreateRoom(rawCode = 'local') {
  const code = String(rawCode || 'local').trim().toLowerCase();
  if (!rooms.has(code)) {
    rooms.set(code, {
      code,
      isLocked: false,
      sessionDurationMinutes: 45,
      remainingSeconds: 0,
      lockReason: 'Deep Work Session',
      masterPin: '884422',
      accountabilityPrompt: 'My lack of discipline is an insult to everyone who believes in me.',
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
          id: `log-${Date.now()}`,
          timestamp: new Date().toLocaleTimeString(),
          type: 'system',
          message: `FocusLock Studio room [${code.toUpperCase()}] initialized.`
        }
      ],
      dashboardSockets: new Set(),
      phoneSockets: new Set(),
      timerInterval: null
    });
  }
  return rooms.get(code);
}

function addRoomLog(room, type, message) {
  const entry = {
    id: `log-${Date.now()}-${Math.random().toString(36).substr(2, 4)}`,
    timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
    type,
    message
  };
  room.activityLogs.unshift(entry);
  if (room.activityLogs.length > 50) room.activityLogs.pop();
}

function broadcastRoomState(room) {
  const payload = JSON.stringify({
    type: 'STATE_UPDATE',
    room: room.code,
    state: {
      isLocked: room.isLocked,
      sessionDurationMinutes: room.sessionDurationMinutes,
      remainingSeconds: room.remainingSeconds,
      lockReason: room.lockReason,
      masterPin: room.masterPin,
      accountabilityPrompt: room.accountabilityPrompt,
      device: room.device,
      callHistory: room.callHistory,
      activityLogs: room.activityLogs,
      serverIp: LOCAL_IP,
      serverPort: PORT,
      roomCode: room.code
    }
  });

  room.dashboardSockets.forEach((client) => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(payload);
    }
  });
  room.phoneSockets.forEach((client) => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(payload);
    }
  });
}

function startRoomTimer(room) {
  if (room.timerInterval) clearInterval(room.timerInterval);
  room.timerInterval = setInterval(() => {
    if (room.isLocked) {
      if (room.remainingSeconds > 0) {
        room.remainingSeconds--;
        broadcastRoomState(room);
      } else {
        room.isLocked = false;
        clearInterval(room.timerInterval);
        room.timerInterval = null;
        addRoomLog(room, 'unlock', '⏰ Focus session completed! Phone auto-unlocked safely.');
        broadcastRoomState(room);
      }
    } else {
      clearInterval(room.timerInterval);
      room.timerInterval = null;
    }
  }, 1000);
}

// WebSocket connections with Room assignment
wss.on('connection', (ws, req) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const roomCode = url.searchParams.get('room') || 'local';
  const role = url.searchParams.get('role') || 'dashboard'; // 'dashboard' or 'phone'
  const remoteIp = req.socket.remoteAddress;

  const room = getOrCreateRoom(roomCode);

  if (role === 'phone') {
    room.phoneSockets.add(ws);
    room.device.connected = true;
    room.device.ip = remoteIp;
    room.device.lastSeen = Date.now();
    addRoomLog(room, 'system', `📱 Android phone connected to room [${roomCode.toUpperCase()}].`);
    broadcastRoomState(room);
  } else {
    room.dashboardSockets.add(ws);
  }

  console.log(`[WS] ${role.toUpperCase()} connected to room [${roomCode}] from ${remoteIp}`);

  // Send current room state immediately on connect
  ws.send(JSON.stringify({
    type: 'STATE_UPDATE',
    room: room.code,
    state: {
      isLocked: room.isLocked,
      sessionDurationMinutes: room.sessionDurationMinutes,
      remainingSeconds: room.remainingSeconds,
      lockReason: room.lockReason,
      masterPin: room.masterPin,
      accountabilityPrompt: room.accountabilityPrompt,
      device: room.device,
      callHistory: room.callHistory,
      activityLogs: room.activityLogs,
      serverIp: LOCAL_IP,
      serverPort: PORT,
      roomCode: room.code
    }
  }));

  ws.on('message', (message) => {
    try {
      const data = JSON.parse(message);

      // Support dynamic room join from socket message if not in query
      if (data.type === 'JOIN_ROOM' && data.room) {
        const targetRoom = getOrCreateRoom(data.room);
        if (data.role === 'phone') {
          room.phoneSockets.delete(ws);
          targetRoom.phoneSockets.add(ws);
          targetRoom.device.connected = true;
          broadcastRoomState(targetRoom);
        }
        return;
      }

      // Phone Heartbeat
      if (data.type === 'DEVICE_HEARTBEAT') {
        room.device = {
          connected: true,
          deviceName: data.deviceName || 'Android Device',
          batteryLevel: data.batteryLevel ?? room.device.batteryLevel,
          isCharging: Boolean(data.isCharging),
          lastSeen: Date.now(),
          ip: remoteIp
        };
        broadcastRoomState(room);
      }

      // Incoming Call Interception report from Phone
      if (data.type === 'CALL_EVENT') {
        const { callerNumber, callerName, isRepeatCaller, action } = data;
        const entry = {
          id: `call-${Date.now()}`,
          timestamp: new Date().toLocaleTimeString(),
          caller: callerName || callerNumber || 'Unknown Caller',
          isRepeatCaller: Boolean(isRepeatCaller),
          action: action
        };
        room.callHistory.unshift(entry);
        if (isRepeatCaller) {
          addRoomLog(room, 'emergency_call', `🚨 Emergency bypass: ${entry.caller} called twice! Phone is RINGING.`);
        } else {
          addRoomLog(room, 'call', `🔕 Call muted from ${entry.caller} (1st attempt - silenced).`);
        }
        broadcastRoomState(room);
      }

      // Emergency Unlock from phone Master PIN
      if (data.type === 'PHONE_PIN_UNLOCK') {
        if (data.pin === room.masterPin) {
          room.isLocked = false;
          if (room.timerInterval) {
            clearInterval(room.timerInterval);
            room.timerInterval = null;
          }
          addRoomLog(room, 'emergency_unlock', '🔑 Emergency Master PIN entered on phone. Lockdown dismissed.');
          broadcastRoomState(room);
        }
      }
    } catch (err) {
      console.error('[WS] Error processing message:', err);
    }
  });

  ws.on('close', () => {
    if (role === 'phone') {
      room.phoneSockets.delete(ws);
      if (room.phoneSockets.size === 0) {
        room.device.connected = false;
        addRoomLog(room, 'system', `📱 Phone disconnected from room [${roomCode.toUpperCase()}].`);
        broadcastRoomState(room);
      }
    } else {
      room.dashboardSockets.delete(ws);
    }
  });
});

// REST Endpoints (Supports ?room=XXXX or body.room)
app.get('/api/status', (req, res) => {
  const room = getOrCreateRoom(req.query.room);
  res.json({
    success: true,
    state: {
      isLocked: room.isLocked,
      sessionDurationMinutes: room.sessionDurationMinutes,
      remainingSeconds: room.remainingSeconds,
      lockReason: room.lockReason,
      masterPin: room.masterPin,
      accountabilityPrompt: room.accountabilityPrompt,
      device: room.device,
      activityLogs: room.activityLogs,
      serverIp: LOCAL_IP,
      serverPort: PORT,
      roomCode: room.code
    }
  });
});

// QR Code for one-click phone pairing
app.get('/api/pair-qr', async (req, res) => {
  try {
    const roomCode = req.query.room || 'local';
    const pairUrl = `focuslock://pair?room=${roomCode}`;
    const qrDataUrl = await QRCode.toDataURL(pairUrl, {
      margin: 1,
      color: {
        dark: '#00F0FF',
        light: '#09090b'
      }
    });
    res.json({ success: true, qr: qrDataUrl, room: roomCode, ip: LOCAL_IP, port: PORT });
  } catch (err) {
    res.status(500).json({ error: 'Failed to generate QR code' });
  }
});

// Trigger Lock from PC
app.post('/api/lock', (req, res) => {
  const { room: roomCode = 'local', durationMinutes = 45, reason = 'Deep Work Session' } = req.body;
  const room = getOrCreateRoom(roomCode);

  room.isLocked = true;
  room.sessionDurationMinutes = durationMinutes;
  room.remainingSeconds = durationMinutes * 60;
  room.lockReason = reason;

  const randIdx = Math.floor(Math.random() * MOTIVATIONAL_QUOTES.length);
  room.accountabilityPrompt = MOTIVATIONAL_QUOTES[randIdx];

  startRoomTimer(room);
  addRoomLog(room, 'lock', `🔒 Focus Lock ACTIVATED for ${durationMinutes} minutes. Phone is now locked.`);
  broadcastRoomState(room);

  res.json({ success: true, state: room });
});

// Trigger Unlock from PC
app.post('/api/unlock', (req, res) => {
  const { room: roomCode = 'local', reason = 'Completed session early' } = req.body;
  const room = getOrCreateRoom(roomCode);

  room.isLocked = false;
  if (room.timerInterval) {
    clearInterval(room.timerInterval);
    room.timerInterval = null;
  }

  addRoomLog(room, 'unlock', `🔓 Focus Lock deactivated from laptop (${reason}).`);
  broadcastRoomState(room);

  res.json({ success: true, state: room });
});

// Emergency Unlock
app.post('/api/emergency-unlock', (req, res) => {
  const { room: roomCode = 'local' } = req.body;
  const room = getOrCreateRoom(roomCode);

  room.isLocked = false;
  if (room.timerInterval) {
    clearInterval(room.timerInterval);
    room.timerInterval = null;
  }

  addRoomLog(room, 'emergency_unlock', '⚠️ Instant Emergency Unlock triggered from dashboard.');
  broadcastRoomState(room);

  res.json({ success: true, state: room });
});

// SPA fallback for React Dashboard
app.get('*', (req, res, next) => {
  if (req.path.startsWith('/api')) return next();
  res.sendFile(path.join(distPath, 'index.html'));
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`\n======================================================`);
  console.log(`🛡️  FocusLock Cloud Multi-Room Studio Active!`);
  console.log(`📡 Port:          ${PORT}`);
  console.log(`🌐 Local Wi-Fi:   http://${LOCAL_IP}:${PORT}`);
  console.log(`======================================================\n`);
});
