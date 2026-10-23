import React, { useState, useEffect, useRef } from 'react';
import {
  Lock,
  Unlock,
  Shield,
  Smartphone,
  Battery,
  BatteryCharging,
  Wifi,
  WifiOff,
  Clock,
  PhoneCall,
  PhoneOff,
  AlertTriangle,
  QrCode,
  Flame,
  CheckCircle2,
  Sparkles,
  HelpCircle,
  KeyRound,
  RotateCcw
} from 'lucide-react';

interface DeviceState {
  connected: boolean;
  deviceName: string;
  batteryLevel: number;
  isCharging: boolean;
  lastSeen: number | null;
  ip: string | null;
}

interface ActivityLog {
  id: string;
  timestamp: string;
  type: string;
  message: string;
}

interface ServerState {
  isLocked: boolean;
  sessionDurationMinutes: number;
  remainingSeconds: number;
  lockReason: string;
  masterPin: string;
  accountabilityPrompt: string;
  device: DeviceState;
  activityLogs: ActivityLog[];
  serverIp: string;
  serverPort: number;
}

export function App() {
  const isPhoneMode = typeof window !== 'undefined' && window.location.search.includes('mode=phone');

  const [serverState, setServerState] = useState<ServerState | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [selectedDuration, setSelectedDuration] = useState<number>(45);
  const [customMinutes, setCustomMinutes] = useState<string>('45');
  const [sessionReason, setSessionReason] = useState<string>('Deep Coding & Building');
  
  // Friction / Accountability Modal State
  const [showFrictionModal, setShowFrictionModal] = useState(false);
  const [frictionCountdown, setFrictionCountdown] = useState(15);
  const [typedConfirmation, setTypedConfirmation] = useState('');
  const [showPairModal, setShowPairModal] = useState(false);
  const [pairQrData, setPairQrData] = useState<string | null>(null);
  const [pairUrl, setPairUrl] = useState<string>('');

  // Mobile Phone state
  const [phoneTapCount, setPhoneTapCount] = useState(0);
  const [showPhonePinPad, setShowPhonePinPad] = useState(false);
  const [phoneEnteredPin, setPhoneEnteredPin] = useState('');

  const wsRef = useRef<WebSocket | null>(null);
  const countdownIntervalRef = useRef<any>(null);
  const lastTapTimeRef = useRef<number>(0);

  // 4-Digit Room Code resolution
  const [roomCode] = useState<string>(() => {
    if (typeof window === 'undefined') return 'local';
    const params = new URLSearchParams(window.location.search);
    const fromUrl = params.get('room');
    if (fromUrl && fromUrl.length >= 4) return fromUrl.toUpperCase();
    const stored = localStorage.getItem('focuslock_room');
    if (stored && stored.length >= 4) return stored.toUpperCase();
    const generated = Math.floor(1000 + Math.random() * 9000).toString();
    localStorage.setItem('focuslock_room', generated);
    return generated;
  });

  // Dynamically resolve server host based on current URL
  const isHttps = typeof window !== 'undefined' && window.location.protocol === 'https:';
  let targetHost = typeof window !== 'undefined' ? window.location.host : 'localhost:4000';
  if (typeof window !== 'undefined' && window.location.port === '3000') {
    targetHost = `${window.location.hostname}:4000`;
  }
  const SERVER_URL = `${isHttps ? 'https:' : 'http:'}//${targetHost}`;
  const WS_URL = `${isHttps ? 'wss:' : 'ws:'}//${targetHost}/ws?room=${roomCode}&role=${isPhoneMode ? 'phone' : 'dashboard'}`;

  // Connect WebSocket
  useEffect(() => {
    let ws: WebSocket;

    function connect() {
      ws = new WebSocket(WS_URL);
      wsRef.current = ws;

      ws.onopen = () => {
        setIsConnected(true);
        // If in phone mode, report device info immediately
        if (isPhoneMode) {
          reportPhoneHeartbeat(ws);
        }
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.type === 'STATE_UPDATE') {
            setServerState(data.state);
            // If phone just got locked, vibrate!
            if (isPhoneMode && data.state.isLocked && 'vibrate' in navigator) {
              navigator.vibrate([200, 100, 200]);
            }
          }
        } catch (e) {
          console.error('WS parse error:', e);
        }
      };

      ws.onclose = () => {
        setIsConnected(false);
        setTimeout(connect, 2000); // Reconnect
      };

      ws.onerror = () => {
        setIsConnected(false);
      };
    }

    async function reportPhoneHeartbeat(socket: WebSocket) {
      let batteryPct = 85;
      let isCharging = false;
      try {
        if ('getBattery' in navigator) {
          const battery: any = await (navigator as any).getBattery();
          batteryPct = Math.round(battery.level * 100);
          isCharging = battery.charging;
        }
      } catch (e) {}

      const userAgent = navigator.userAgent;
      let deviceName = 'Mobile Device';
      if (/Android/i.test(userAgent)) deviceName = 'Android Phone';
      else if (/iPhone/i.test(userAgent)) deviceName = 'Apple iPhone';

      socket.send(JSON.stringify({
        type: 'DEVICE_HEARTBEAT',
        deviceName,
        batteryLevel: batteryPct,
        isCharging
      }));
    }

    connect();

    return () => {
      if (ws) ws.close();
    };
  }, [isPhoneMode]);

  // Friction modal timer (15s countdown before early unlock button activates)
  useEffect(() => {
    if (showFrictionModal) {
      setFrictionCountdown(15);
      setTypedConfirmation('');
      if (countdownIntervalRef.current) clearInterval(countdownIntervalRef.current);
      
      countdownIntervalRef.current = setInterval(() => {
        setFrictionCountdown((prev) => {
          if (prev <= 1) {
            clearInterval(countdownIntervalRef.current);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } else {
      if (countdownIntervalRef.current) clearInterval(countdownIntervalRef.current);
    }
  }, [showFrictionModal]);

  // Load Pair QR Code
  const handleOpenPairModal = async () => {
    setShowPairModal(true);
    try {
      const res = await fetch(`${SERVER_URL}/api/pair-qr?room=${roomCode}`);
      const data = await res.json();
      if (data.qr) {
        setPairQrData(data.qr);
        setPairUrl(data.url);
      }
    } catch (err) {
      console.error(err);
    }
  };

  // Lock Action
  const handleLockPhone = async () => {
    const duration = selectedDuration === -1 ? Math.max(5, parseInt(customMinutes) || 45) : selectedDuration;
    try {
      await fetch(`${SERVER_URL}/api/lock`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          room: roomCode,
          durationMinutes: duration,
          reason: sessionReason
        })
      });
    } catch (err) {
      console.error(err);
    }
  };

  // Safe Unlock with Confirmation
  const handleConfirmUnlock = async () => {
    try {
      await fetch(`${SERVER_URL}/api/unlock`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          room: roomCode,
          reason: typedConfirmation || 'Early unlock confirmed'
        })
      });
      setShowFrictionModal(false);
    } catch (err) {
      console.error(err);
    }
  };

  // Instant Emergency Unlock (Zero friction escape hatch)
  const handleEmergencyUnlock = async () => {
    try {
      await fetch(`${SERVER_URL}/api/emergency-unlock`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ room: roomCode })
      });
      setShowFrictionModal(false);
    } catch (err) {
      console.error(err);
    }
  };

  // Simulators for testing
  const handleSimulateDevice = async (connected: boolean) => {
    await fetch(`${SERVER_URL}/api/simulate-device`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        connected,
        battery: 89,
        deviceName: "Samsung Galaxy S24 Ultra"
      })
    });
  };

  const handleSimulateCall = async (caller: string) => {
    await fetch(`${SERVER_URL}/api/simulate-call`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ caller })
    });
  };

  const isLocked = serverState?.isLocked ?? false;
  const remainingSeconds = serverState?.remainingSeconds ?? 0;
  const minutesLeft = Math.floor(remainingSeconds / 60);
  const secondsLeft = remainingSeconds % 60;
  const formattedTime = `${String(minutesLeft).padStart(2, '0')}:${String(secondsLeft).padStart(2, '0')}`;
  
  const totalSeconds = (serverState?.sessionDurationMinutes ?? 45) * 60;
  const progressPercent = totalSeconds > 0 ? ((totalSeconds - remainingSeconds) / totalSeconds) * 100 : 0;

  // Phone screen tap handler (5 taps for emergency bypass)
  const handlePhoneTap = () => {
    const now = Date.now();
    if (now - lastTapTimeRef.current < 700) {
      const nextCount = phoneTapCount + 1;
      setPhoneTapCount(nextCount);
      if (nextCount >= 5) {
        setShowPhonePinPad(true);
        setPhoneTapCount(0);
      }
    } else {
      setPhoneTapCount(1);
    }
    lastTapTimeRef.current = now;
  };

  const handlePhonePinUnlock = () => {
    if (phoneEnteredPin.trim() === (serverState?.masterPin || '884422')) {
      handleEmergencyUnlock();
      setShowPhonePinPad(false);
      setPhoneEnteredPin('');
    } else {
      alert('Incorrect PIN! Default is 884422');
    }
  };

  // ==========================================
  // 📱 DEDICATED PHONE COMPANION VIEW
  // ==========================================
  if (isPhoneMode) {
    return (
      <div 
        onClick={isLocked ? handlePhoneTap : undefined}
        className="min-h-screen bg-black text-white flex flex-col items-center justify-between p-6 select-none relative overflow-hidden"
      >
        {/* Top Phone Header */}
        <div className="w-full flex items-center justify-between pt-2">
          <div className="flex items-center gap-2">
            <span className={`w-2.5 h-2.5 rounded-full ${isConnected ? 'bg-emerald-400 animate-pulse' : 'bg-red-500'}`} />
            <span className="text-[11px] font-mono text-zinc-400">
              {isConnected ? 'Synced with Laptop' : 'Connecting to Laptop...'}
            </span>
          </div>
          <div className="text-[11px] font-mono text-zinc-500">
            Fail-Safe Active
          </div>
        </div>

        {/* Center: Locked vs Unlocked on Phone */}
        {isLocked ? (
          <div className="flex flex-col items-center text-center my-auto">
            {/* Glowing Padlock */}
            <div className="w-20 h-20 rounded-3xl bg-red-950/40 border border-red-500/50 flex items-center justify-center text-red-400 mb-6 shadow-2xl shadow-red-500/20">
              <Lock className="w-10 h-10 animate-pulse" />
            </div>

            <div className="text-xs font-mono font-bold tracking-widest text-red-400 uppercase mb-2">
              Phone Blocked From Laptop
            </div>

            {/* Huge Countdown Timer */}
            <div className="text-6xl font-mono font-black tracking-tight text-white mb-4">
              {formattedTime}
            </div>

            {/* Emotional Punchline */}
            <div className="max-w-xs p-4 rounded-2xl bg-zinc-900/60 border border-zinc-800 text-sm font-bold text-rose-300 leading-snug mb-6">
              "Did you make your parents proud today?"
            </div>

            <p className="text-[11px] text-zinc-500 max-w-xs leading-relaxed">
              Stay focused. Your session is active.<br />
              Emergency callers will ring aloud on their 2nd call attempt.
            </p>
          </div>
        ) : (
          <div className="flex flex-col items-center text-center my-auto">
            <div className="w-20 h-20 rounded-3xl bg-cyan-950/40 border border-cyan-500/40 flex items-center justify-center text-cyan-400 mb-6 shadow-2xl shadow-cyan-500/20">
              <Smartphone className="w-10 h-10" />
            </div>

            <h2 className="text-xl font-extrabold text-white mb-2">
              Phone Paired & Ready
            </h2>
            <p className="text-xs text-zinc-400 max-w-xs leading-relaxed mb-6">
              Your phone is now connected wirelessly to your laptop. Tap <strong>Activate Phone Lockdown</strong> on your laptop whenever you want to work.
            </p>

            <div className="px-4 py-2 rounded-xl bg-zinc-900 border border-zinc-800 text-xs font-mono text-emerald-400 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-400" />
              <span>Ready for Remote Lock</span>
            </div>
          </div>
        )}

        {/* Bottom Escape Notice */}
        <div className="w-full text-center pb-2">
          {isLocked && (
            <p className="text-[10px] text-zinc-600 mb-3 font-mono">
              (Tap screen 5 times for emergency Master PIN keypad)
            </p>
          )}
          <a
            href="/"
            className="text-[11px] text-zinc-500 hover:text-zinc-300 font-mono underline"
          >
            Switch to Laptop Dashboard View
          </a>
        </div>

        {/* Emergency Pin Pad Modal on Phone */}
        {showPhonePinPad && (
          <div className="fixed inset-0 z-50 bg-black/95 flex flex-col items-center justify-center p-6 text-center">
            <div className="w-12 h-12 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400 mb-4">
              <KeyRound className="w-6 h-6" />
            </div>
            <h3 className="text-lg font-bold text-white mb-1">Emergency Master PIN</h3>
            <p className="text-xs text-zinc-400 max-w-xs mb-6">
              Enter your 6-digit emergency PIN to force unlock immediately without your laptop.
            </p>

            <input
              type="password"
              inputMode="numeric"
              maxLength={6}
              value={phoneEnteredPin}
              onChange={(e) => setPhoneEnteredPin(e.target.value)}
              placeholder="Default: 884422"
              className="w-48 bg-zinc-900 border border-zinc-700 rounded-xl px-4 py-3 text-center text-lg font-mono tracking-widest text-white mb-4 focus:outline-none focus:border-cyan-500"
            />

            <div className="flex flex-col gap-2 w-48">
              <button
                onClick={handlePhonePinUnlock}
                className="w-full py-3 rounded-xl bg-cyan-500 text-black font-bold text-xs uppercase tracking-wider"
              >
                Unlock Phone
              </button>
              <button
                onClick={() => setShowPhonePinPad(false)}
                className="w-full py-2 text-xs text-zinc-500"
              >
                Cancel
              </button>
            </div>
          </div>
        )}
      </div>
    );
  }

  // ==========================================
  // 💻 LAPTOP COCKPIT DASHBOARD VIEW
  // ==========================================
  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col antialiased">
      {/* Top Navbar */}
      <header className="border-b border-zinc-900 bg-zinc-950/80 backdrop-blur-md sticky top-0 z-30 px-6 py-3.5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-cyan-500/20 to-blue-600/10 border border-cyan-500/40 flex items-center justify-center shadow-lg shadow-cyan-500/10">
            <Lock className="w-5 h-5 text-cyan-400" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-extrabold text-base tracking-tight text-white">FocusLock</span>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-cyan-950/70 border border-cyan-500/30 text-cyan-300 font-semibold uppercase tracking-wider">
                V1.0 Wireless
              </span>
            </div>
            <p className="text-[11px] text-zinc-400 font-medium">Remote Physical Phone Lockdown Studio</p>
          </div>
        </div>

        {/* Device Status Bar */}
        <div className="flex items-center gap-3">
          {/* Bridge Connection status */}
          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-zinc-900/80 border border-zinc-800 text-xs">
            <span className={`w-2 h-2 rounded-full ${isConnected ? 'bg-emerald-400 shadow-sm shadow-emerald-400' : 'bg-red-400'}`} />
            <span className="text-zinc-300 font-mono text-[11px]">
              {isConnected ? 'Bridge Online' : 'Connecting...'}
            </span>
          </div>

          {/* Phone Status Pill */}
          <div className="flex items-center gap-2 px-3.5 py-1.5 rounded-xl bg-zinc-900/80 border border-zinc-800 text-xs">
            <Smartphone className="w-4 h-4 text-zinc-400" />
            {serverState?.device?.connected ? (
              <>
                <span className="font-semibold text-zinc-200">{serverState.device.deviceName}</span>
                <span className="text-zinc-600">•</span>
                <div className="flex items-center gap-1 text-emerald-400 font-mono text-[11px]">
                  {serverState.device.isCharging ? (
                    <BatteryCharging className="w-3.5 h-3.5" />
                  ) : (
                    <Battery className="w-3.5 h-3.5" />
                  )}
                  <span>{serverState.device.batteryLevel}%</span>
                </div>
              </>
            ) : (
              <span className="text-amber-400/90 font-medium text-[11px] flex items-center gap-1">
                <WifiOff className="w-3.5 h-3.5" /> Phone Not Paired Yet
              </span>
            )}
          </div>

          {/* Pair Code Display */}
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-cyan-950/40 border border-cyan-500/40 text-xs shadow-sm shadow-cyan-500/10">
            <span className="text-zinc-400 font-mono text-[11px]">Sync Code:</span>
            <span className="font-mono font-black text-cyan-300 text-sm tracking-wider">{roomCode}</span>
          </div>

          {/* Pair Phone Button */}
          <button
            onClick={handleOpenPairModal}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-zinc-900 hover:bg-zinc-800 border border-zinc-700/80 text-xs font-semibold text-zinc-200 transition-all cursor-pointer"
          >
            <QrCode className="w-3.5 h-3.5 text-cyan-400" />
            <span>Pair Phone</span>
          </button>
        </div>
      </header>

      {/* Main Workspace */}
      <main className="flex-1 max-w-6xl w-full mx-auto px-6 py-8 grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        
        {/* Left Column: Core Lock Cockpit (7 cols) */}
        <div className="lg:col-span-7 flex flex-col gap-6">
          
          {/* Main Card */}
          <div className={`rounded-3xl border transition-all p-8 relative overflow-hidden ${
            isLocked
              ? 'bg-gradient-to-b from-red-950/20 via-zinc-900/60 to-zinc-950 border-red-500/40 glow-red'
              : 'bg-gradient-to-b from-zinc-900/70 to-zinc-950 border-zinc-800 shadow-2xl'
          }`}>
            
            {/* Background ambient glow */}
            <div className={`absolute -top-24 -left-24 w-72 h-72 rounded-full blur-3xl pointer-events-none transition-all ${
              isLocked ? 'bg-red-500/15' : 'bg-cyan-500/10'
            }`} />

            {/* Header within card */}
            <div className="flex items-center justify-between mb-8 relative z-10">
              <div className="flex items-center gap-2.5">
                <div className={`w-3 h-3 rounded-full animate-ping ${isLocked ? 'bg-red-500' : 'bg-cyan-400'}`} />
                <span className="text-xs font-mono uppercase tracking-widest font-bold text-zinc-400">
                  {isLocked ? 'PHONE STATUS: 100% BLOCKED' : 'SYSTEM READY: UNLOCKED'}
                </span>
              </div>
              <div className="text-[11px] font-mono text-zinc-500">
                Fail-Safe Guard: Active
              </div>
            </div>

            {/* Locked vs Unlocked Display */}
            {isLocked ? (
              /* LOCKED ACTIVE STATE */
              <div className="flex flex-col items-center text-center relative z-10 py-4">
                
                {/* Massive Timer Circle */}
                <div className="relative w-64 h-64 flex items-center justify-center mb-6">
                  {/* Outer SVG Progress Ring */}
                  <svg className="w-full h-full transform -rotate-90">
                    <circle
                      cx="128"
                      cy="128"
                      r="112"
                      stroke="#27272a"
                      strokeWidth="10"
                      fill="transparent"
                    />
                    <circle
                      cx="128"
                      cy="128"
                      r="112"
                      stroke="#ef4444"
                      strokeWidth="10"
                      strokeDasharray={703.7}
                      strokeDashoffset={703.7 - (703.7 * progressPercent) / 100}
                      strokeLinecap="round"
                      fill="transparent"
                      className="transition-all duration-1000 ease-linear"
                    />
                  </svg>

                  {/* Inner text */}
                  <div className="absolute inset-0 flex flex-col items-center justify-center">
                    <div className="text-5xl font-mono font-extrabold text-white tracking-tighter">
                      {formattedTime}
                    </div>
                    <div className="text-xs font-semibold text-red-400 uppercase tracking-widest mt-1">
                      Time Left
                    </div>
                  </div>
                </div>

                {/* Session Reason */}
                <h2 className="text-xl font-bold text-zinc-100 mb-1">
                  "{serverState?.lockReason || 'Deep Focus Session'}"
                </h2>
                <p className="text-xs text-zinc-400 max-w-md leading-relaxed mb-8">
                  Your phone screen is currently covered with an immovable black overlay. All social apps, reels, and browser access are disabled.
                </p>

                {/* Safety Pill */}
                <div className="flex items-center gap-2 px-4 py-2 rounded-xl bg-zinc-900/90 border border-zinc-800 text-xs text-zinc-300 mb-8">
                  <Shield className="w-4 h-4 text-emerald-400 shrink-0" />
                  <span>
                    Emergency Call Bypass Active: <strong>Second call rings phone</strong>
                  </span>
                </div>

                {/* Turn OFF Button (triggers emotional friction modal) */}
                <button
                  onClick={() => setShowFrictionModal(true)}
                  className="w-full py-4 rounded-2xl bg-zinc-900 hover:bg-zinc-800 border border-red-500/40 text-red-400 hover:text-red-300 font-bold text-sm tracking-wide transition-all shadow-lg shadow-red-950/40 flex items-center justify-center gap-2 cursor-pointer group"
                >
                  <Unlock className="w-4 h-4 group-hover:scale-110 transition-transform" />
                  <span>Request Early Phone Unlock</span>
                </button>
              </div>
            ) : (
              /* UNLOCKED IDLE STATE */
              <div className="flex flex-col relative z-10">
                <div className="mb-6">
                  <h1 className="text-2xl font-extrabold text-white tracking-tight mb-2">
                    Start High-Stakes Focus Session
                  </h1>
                  <p className="text-xs text-zinc-400 leading-relaxed">
                    Once activated, your phone will instantly lock. No Instagram, no YouTube Shorts, no distracting notifications. Only emergency repeat callers can get through.
                  </p>
                </div>

                {/* Duration Presets */}
                <div className="mb-6">
                  <label className="text-xs font-semibold text-zinc-300 block mb-2.5 flex items-center gap-1.5">
                    <Clock className="w-3.5 h-3.5 text-cyan-400" />
                    Select Lock Duration
                  </label>
                  <div className="grid grid-cols-5 gap-2">
                    {[25, 45, 60, 90].map((mins) => (
                      <button
                        key={mins}
                        onClick={() => setSelectedDuration(mins)}
                        className={`py-3 rounded-2xl border text-center transition-all cursor-pointer font-mono ${
                          selectedDuration === mins
                            ? 'bg-cyan-500/15 border-cyan-400 text-cyan-300 shadow-md shadow-cyan-500/10 font-bold'
                            : 'bg-zinc-900/80 border-zinc-800 text-zinc-400 hover:border-zinc-700 hover:text-zinc-200'
                        }`}
                      >
                        <div className="text-sm">{mins}m</div>
                        <div className="text-[10px] text-zinc-500 font-sans mt-0.5">
                          {mins === 25 ? 'Pomodoro' : mins === 45 ? 'Sprint' : mins === 60 ? 'Standard' : 'Ultra'}
                        </div>
                      </button>
                    ))}
                    <button
                      onClick={() => setSelectedDuration(-1)}
                      className={`py-3 rounded-2xl border text-center transition-all cursor-pointer font-mono ${
                        selectedDuration === -1
                          ? 'bg-cyan-500/15 border-cyan-400 text-cyan-300 shadow-md shadow-cyan-500/10 font-bold'
                          : 'bg-zinc-900/80 border-zinc-800 text-zinc-400 hover:border-zinc-700 hover:text-zinc-200'
                      }`}
                    >
                      <div className="text-sm">Custom</div>
                      <div className="text-[10px] text-zinc-500 font-sans mt-0.5">
                        Any mins
                      </div>
                    </button>
                  </div>

                  {selectedDuration === -1 && (
                    <div className="mt-3 p-3.5 rounded-2xl bg-cyan-950/20 border border-cyan-500/40 flex items-center gap-3">
                      <span className="text-xs text-zinc-300 font-medium">Custom Minutes:</span>
                      <input
                        type="number"
                        min="1"
                        max="720"
                        value={customMinutes}
                        onChange={(e) => setCustomMinutes(e.target.value)}
                        placeholder="e.g. 100"
                        className="w-24 bg-zinc-950 border border-cyan-500/50 rounded-xl px-3 py-1.5 text-center font-mono font-bold text-cyan-300 text-sm focus:outline-none focus:ring-1 focus:ring-cyan-400"
                      />
                      <span className="text-xs text-zinc-400">
                        {parseInt(customMinutes) > 0 ? `${customMinutes} minutes (${Math.floor((parseInt(customMinutes) || 0) / 60)}h ${(parseInt(customMinutes) || 0) % 60}m)` : 'Enter any duration (e.g. 100)'}
                      </span>
                    </div>
                  )}
                </div>

                {/* Session Goal / Commitment */}
                <div className="mb-8">
                  <label className="text-xs font-semibold text-zinc-300 block mb-1.5 flex items-center gap-1.5">
                    <Flame className="w-3.5 h-3.5 text-amber-400" />
                    What are you working on?
                  </label>
                  <input
                    type="text"
                    value={sessionReason}
                    onChange={(e) => setSessionReason(e.target.value)}
                    placeholder="e.g. Finish project code, Read Chapter 4..."
                    className="w-full bg-zinc-950 border border-zinc-800 rounded-xl px-4 py-2.5 text-xs text-zinc-200 placeholder-zinc-600 focus:outline-none focus:border-cyan-500"
                  />
                </div>

                {/* BIG LOCK BUTTON */}
                <button
                  onClick={handleLockPhone}
                  className="w-full py-4 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-zinc-950 font-extrabold text-sm tracking-wide shadow-xl shadow-cyan-500/20 transition-all flex items-center justify-center gap-2 cursor-pointer active:scale-[0.99]"
                >
                  <Lock className="w-4 h-4" />
                  <span>ACTIVATE WIRELESS PHONE LOCKDOWN ({selectedDuration === -1 ? (parseInt(customMinutes) || 45) : selectedDuration}m)</span>
                </button>
              </div>
            )}
          </div>

          {/* Safety Safeguards Card */}
          <div className="p-5 rounded-2xl bg-zinc-900/40 border border-zinc-800/80 flex items-start gap-3.5">
            <div className="w-8 h-8 rounded-xl bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center shrink-0 text-emerald-400">
              <Shield className="w-4 h-4" />
            </div>
            <div className="text-xs">
              <h4 className="font-semibold text-zinc-200 mb-1 flex items-center gap-2">
                100% Fail-Safe Anti-Bricking Guarantee
              </h4>
              <p className="text-zinc-400 leading-relaxed text-[11px]">
                Your phone operates on an independent hardware timer. If your laptop loses power or internet disconnects, the phone automatically unlocks when the session ends.
                Emergency Master PIN on phone: <span className="font-mono text-cyan-300 font-bold bg-cyan-950/60 px-1.5 py-0.5 rounded border border-cyan-500/20">884422</span> (tap lock screen 5 times to reveal).
              </p>
            </div>
          </div>
        </div>

        {/* Right Column: Simulators & Telemetry Stream (5 cols) */}
        <div className="lg:col-span-5 flex flex-col gap-6">

          {/* Interactive Testing Cockpit (Simulate Phone before APK install) */}
          <div className="p-5 rounded-2xl bg-zinc-900/50 border border-zinc-800 flex flex-col gap-3.5">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-bold text-zinc-200 flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-cyan-400" />
                Phone Simulator & Tester
              </h3>
              <span className="text-[10px] font-mono text-zinc-500 bg-zinc-800 px-2 py-0.5 rounded">
                Developer Sandbox
              </span>
            </div>
            <p className="text-[11px] text-zinc-400 leading-normal">
              Test the wireless lock and the Smart Call Filter before deploying to your physical Android device:
            </p>

            <div className="grid grid-cols-2 gap-2 pt-1">
              <button
                onClick={() => handleSimulateDevice(true)}
                className="p-2.5 rounded-xl bg-zinc-950 border border-zinc-800 hover:border-zinc-700 text-left transition-all"
              >
                <div className="text-xs font-semibold text-emerald-400 flex items-center gap-1.5 mb-0.5">
                  <Smartphone className="w-3.5 h-3.5" />
                  Simulate Connect
                </div>
                <div className="text-[10px] text-zinc-500">Pairs mock phone</div>
              </button>

              <button
                onClick={() => handleSimulateCall("Mom")}
                className="p-2.5 rounded-xl bg-zinc-950 border border-zinc-800 hover:border-zinc-700 text-left transition-all"
              >
                <div className="text-xs font-semibold text-amber-400 flex items-center gap-1.5 mb-0.5">
                  <PhoneOff className="w-3.5 h-3.5" />
                  Simulate Call #1
                </div>
                <div className="text-[10px] text-zinc-500">Mutes 1st attempt</div>
              </button>

              <button
                onClick={() => handleSimulateCall("Mom")}
                className="p-2.5 rounded-xl bg-zinc-950 border border-zinc-800 hover:border-zinc-700 text-left transition-all"
              >
                <div className="text-xs font-semibold text-red-400 flex items-center gap-1.5 mb-0.5">
                  <PhoneCall className="w-3.5 h-3.5" />
                  Simulate Call #2
                </div>
                <div className="text-[10px] text-zinc-500">Emergency loud ring</div>
              </button>

              <button
                onClick={handleEmergencyUnlock}
                className="p-2.5 rounded-xl bg-zinc-950 border border-zinc-800 hover:border-zinc-700 text-left transition-all"
              >
                <div className="text-xs font-semibold text-cyan-400 flex items-center gap-1.5 mb-0.5">
                  <RotateCcw className="w-3.5 h-3.5" />
                  Instant Reset
                </div>
                <div className="text-[10px] text-zinc-500">Emergency unlock</div>
              </button>
            </div>
          </div>

          {/* Real-time Activity Telemetry */}
          <div className="p-5 rounded-2xl bg-zinc-900/50 border border-zinc-800 flex flex-col h-[380px]">
            <div className="flex items-center justify-between pb-3 border-b border-zinc-800/80 mb-3">
              <h3 className="text-xs font-bold text-zinc-200 flex items-center gap-2">
                <Wifi className="w-4 h-4 text-cyan-400" />
                Live Sync Activity Stream
              </h3>
              <span className="text-[10px] font-mono text-zinc-500">Real-time</span>
            </div>

            <div className="flex-1 overflow-y-auto space-y-2 pr-1 font-mono text-[11px]">
              {serverState?.activityLogs && serverState.activityLogs.length > 0 ? (
                serverState.activityLogs.map((log) => (
                  <div
                    key={log.id}
                    className="p-2 rounded-lg bg-zinc-950/70 border border-zinc-800/60 flex items-start gap-2"
                  >
                    <span className="text-zinc-600 text-[10px] shrink-0">{log.timestamp}</span>
                    <span className={`leading-tight ${
                      log.type === 'lock'
                        ? 'text-cyan-300 font-semibold'
                        : log.type === 'unlock'
                        ? 'text-emerald-400'
                        : log.type === 'emergency_call'
                        ? 'text-red-400 font-bold'
                        : log.type === 'call'
                        ? 'text-amber-300'
                        : 'text-zinc-400'
                    }`}>
                      {log.message}
                    </span>
                  </div>
                ))
              ) : (
                <div className="text-zinc-600 text-center py-12">No activity logged yet.</div>
              )}
            </div>
          </div>

        </div>
      </main>

      {/* ========================================================================= */}
      {/* 🛑 THE ACCOUNTABILITY FRICTION MODAL ("Did you make your parents proud?") */}
      {/* ========================================================================= */}
      {showFrictionModal && (
        <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-md flex items-center justify-center p-4">
          <div className="max-w-lg w-full bg-zinc-950 border border-zinc-800 rounded-3xl p-7 shadow-2xl relative overflow-hidden flex flex-col items-center text-center">
            
            {/* Top red warning halo */}
            <div className="absolute -top-24 w-64 h-64 bg-red-600/20 rounded-full blur-3xl pointer-events-none" />

            <div className="w-14 h-14 rounded-2xl bg-red-500/10 border border-red-500/30 flex items-center justify-center mb-5 text-red-400">
              <AlertTriangle className="w-7 h-7" />
            </div>

            {/* THE PROMPT */}
            <h2 className="text-2xl font-extrabold text-white tracking-tight mb-2">
              Did you make your parents proud today?
            </h2>
            <p className="text-xs text-zinc-400 leading-relaxed max-w-sm mb-6">
              You still have <strong className="text-white">{minutesLeft} minutes</strong> remaining in this session. If you unlock now, you are quitting your commitment to go back to scrolling.
            </p>

            {/* Reflection Countdown Box */}
            <div className="w-full bg-zinc-900/80 border border-zinc-800 rounded-2xl p-4 mb-6 flex items-center justify-between">
              <div className="text-left">
                <div className="text-xs font-semibold text-zinc-200">Mandatory Reflection Delay</div>
                <div className="text-[11px] text-zinc-500">Take a breath before making an impulsive decision</div>
              </div>
              <div className={`w-10 h-10 rounded-xl font-mono font-bold text-sm flex items-center justify-center border ${
                frictionCountdown > 0
                  ? 'bg-red-950/60 border-red-500/40 text-red-400 animate-pulse'
                  : 'bg-emerald-950/60 border-emerald-500/40 text-emerald-400'
              }`}>
                {frictionCountdown > 0 ? `${frictionCountdown}s` : '0s'}
              </div>
            </div>

            {/* Type commitment */}
            <div className="w-full mb-6 text-left">
              <label className="text-[11px] font-medium text-zinc-400 block mb-1.5">
                Type <span className="font-mono text-zinc-200 bg-zinc-900 px-1.5 py-0.5 rounded border border-zinc-800">"I am quitting"</span> to confirm unlock:
              </label>
              <input
                type="text"
                value={typedConfirmation}
                onChange={(e) => setTypedConfirmation(e.target.value)}
                placeholder="Type 'I am quitting' here..."
                className="w-full bg-zinc-900 border border-zinc-800 rounded-xl px-3.5 py-2 text-xs text-zinc-200 placeholder-zinc-600 focus:outline-none focus:border-red-500"
              />
            </div>

            {/* Action Buttons */}
            <div className="w-full flex flex-col gap-2.5">
              {/* PRIMARY CHOICE: STAY FOCUSED */}
              <button
                onClick={() => setShowFrictionModal(false)}
                className="w-full py-3.5 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 text-zinc-950 font-bold text-xs tracking-wider uppercase transition-all shadow-lg shadow-cyan-500/20 cursor-pointer flex items-center justify-center gap-2"
              >
                <CheckCircle2 className="w-4 h-4" />
                <span>NEVER MIND — KEEP ME LOCKED & FOCUSED</span>
              </button>

              {/* SECONDARY CHOICE: UNLOCK (Only enabled when countdown hits 0 AND typed text matches) */}
              <button
                disabled={frictionCountdown > 0 || typedConfirmation.trim().toLowerCase() !== 'i am quitting'}
                onClick={handleConfirmUnlock}
                className="w-full py-3 rounded-xl border text-xs font-semibold transition-all cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed bg-zinc-900 border-zinc-800 text-red-400 hover:bg-red-950/30 hover:border-red-500/40"
              >
                {frictionCountdown > 0
                  ? `Wait ${frictionCountdown}s to unlock...`
                  : typedConfirmation.trim().toLowerCase() !== 'i am quitting'
                  ? 'Type confirmation phrase above...'
                  : 'Confirm Early Unlock'}
              </button>
            </div>

            {/* Emergency fallback */}
            <div className="mt-4 pt-4 border-t border-zinc-900 w-full flex items-center justify-center">
              <button
                onClick={handleEmergencyUnlock}
                className="text-[10px] text-zinc-500 hover:text-red-400 font-mono transition-colors"
              >
                ⚠️ Real Life Emergency? Force Instant Unlock
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* 📱 PAIR PHONE MODAL (QR CODE) */}
      {/* ========================================================================= */}
      {showPairModal && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="max-w-md w-full bg-zinc-950 border border-zinc-800 rounded-3xl p-6 shadow-2xl relative flex flex-col items-center text-center">
            
            <h3 className="text-lg font-bold text-white mb-1">Pair Your Phone</h3>
            <p className="text-xs text-zinc-400 mb-4">
              Enter this 4-digit code in your FocusLock app to pair instantly over the internet or Wi-Fi.
            </p>

            {/* Big 4-Digit Code Badge */}
            <div className="w-full bg-cyan-950/40 border border-cyan-500/50 rounded-2xl p-5 mb-4 text-center shadow-lg shadow-cyan-500/10">
              <div className="text-[11px] font-mono text-zinc-400 uppercase tracking-widest mb-1">
                Your 4-Digit Sync Code
              </div>
              <div className="text-4xl font-mono font-black text-cyan-300 tracking-[0.3em]">
                {roomCode}
              </div>
              <div className="text-[11px] text-zinc-400 mt-2">
                Type this code on your phone & tap <span className="text-cyan-400 font-bold">Connect Wireless Sync</span>
              </div>
            </div>

            <div className="w-full bg-zinc-900/80 border border-zinc-800 rounded-xl p-2.5 text-xs font-mono text-zinc-300 mb-5 flex items-center justify-between">
              <span className="text-zinc-500">Local Wi-Fi Server IP:</span>
              <span className="text-cyan-400 font-bold">{serverState?.serverIp || '127.0.0.1'}:4000</span>
            </div>

            <button
              onClick={() => setShowPairModal(false)}
              className="w-full py-2.5 rounded-xl bg-zinc-900 hover:bg-zinc-800 text-xs font-semibold text-zinc-300 border border-zinc-700/80 cursor-pointer"
            >
              Close
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
