import React, { useState, useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axios from 'axios';

export default function TransactionTracker({ transactionId, onClose }) {
  const [connectionStatus, setConnectionStatus] = useState('CONNECTING');
  const [events, setEvents] = useState([]);
  const [latestStatus, setLatestStatus] = useState(null);
  const consoleEndRef = useRef(null);

  const steps = [
    { key: 'CREATED', label: 'Transaction Created', service: 'Transaction-Service' },
    { key: 'ENCRYPTED', label: 'Encrypted', service: 'Transaction-Service' },
    { key: 'RELAY_1', label: 'Relay 1', service: 'Relay-1' },
    { key: 'RELAY_2', label: 'Relay 2', service: 'Relay-2' },
    { key: 'GATEWAY', label: 'Gateway', service: 'Gateway' },
    { key: 'BANK', label: 'Bank', service: 'Bank-Service' },
    { key: 'COMPLETED', label: 'Completed', service: 'Bank-Service' }
  ];

  useEffect(() => {
    if (consoleEndRef.current) {
      consoleEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [events]);

  useEffect(() => {
    if (!transactionId) return;

    setConnectionStatus('CONNECTING');
    setEvents([]);
    setLatestStatus(null);
    console.log('[Tracker] Mounting for transaction:', transactionId);

    // Helper: merge new events into existing, dedup by message + currentStage
    const mergeEvents = (existing, incoming) => {
      const merged = [...existing];
      for (const evt of incoming) {
        const isDupe = merged.some(
          e => e.message === evt.message && e.currentStage === evt.currentStage
        );
        if (!isDupe) {
          merged.push(evt);
        }
      }
      // Sort by timestamp so timeline order is correct
      merged.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
      return merged;
    };

    // Fetch event history and merge into state
    const token = localStorage.getItem('token');
    let isTerminal = false;

    const fetchHistory = () => {
      console.log('[Tracker] Fetching event history...');
      axios.get(`http://localhost:8086/api/transactions/${transactionId}/events`, {
        headers: { Authorization: `Bearer ${token}` }
      })
        .then(res => {
          console.log('[Tracker] History response:', res.data?.length, 'events', res.data);
          if (res.data && res.data.length > 0) {
            setEvents(prev => {
              const merged = mergeEvents(prev, res.data);
              console.log('[Tracker] After merge:', merged.length, 'total events');
              return merged;
            });
            const lastEvent = res.data[res.data.length - 1];
            setLatestStatus(prev => {
              if (!prev) return lastEvent;
              return new Date(lastEvent.timestamp) > new Date(prev.timestamp) ? lastEvent : prev;
            });
            // Check if transaction reached terminal state
            if (res.data.some(e => e.currentStage === 'COMPLETED' || e.transactionStatus === 'SUCCESS' || e.transactionStatus === 'FAILED')) {
              console.log('[Tracker] Transaction reached terminal state, stopping polling');
              isTerminal = true;
            }
          }
        })
        .catch(err => {
          console.error('[Tracker] Failed to fetch history:', err);
        });
    };

    // Fetch immediately
    fetchHistory();

    // Poll every 1.5 seconds until terminal state or 30 seconds elapsed
    let pollCount = 0;
    const pollInterval = setInterval(() => {
      pollCount++;
      if (isTerminal || pollCount > 20) {
        console.log('[Tracker] Stopping history polling (terminal:', isTerminal, 'count:', pollCount, ')');
        clearInterval(pollInterval);
        return;
      }
      fetchHistory();
    }, 1500);

    const stompClient = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8086/ws'),
      debug: (str) => console.log('[STOMP] ' + str),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    stompClient.onConnect = (frame) => {
      setConnectionStatus('CONNECTED');
      console.log('[Tracker] WS Connected:', frame);

      stompClient.subscribe(`/topic/transactions/${transactionId}`, (message) => {
        const event = JSON.parse(message.body);
        console.log('[Tracker] WS Event received:', event.currentStage, event.message);

        // Merge WS event into existing events (dedup by message + stage)
        setEvents((prev) => {
          const isDupe = prev.some(
            e => e.message === event.message && e.currentStage === event.currentStage
          );
          if (isDupe) {
            console.log('[Tracker] WS Event deduped:', event.currentStage);
            return prev;
          }
          const updated = [...prev, event];
          updated.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
          return updated;
        });

        // Set latest state
        setLatestStatus(event);
      });
    };

    stompClient.onWebSocketClose = () => {
      console.log('[Tracker] WS Disconnected');
      setConnectionStatus('DISCONNECTED');
    };

    stompClient.onStompError = (frame) => {
      console.error('[Tracker] STOMP Error:', frame);
      setConnectionStatus('ERROR');
    };

    stompClient.activate();

    return () => {
      console.log('[Tracker] Cleanup');
      clearInterval(pollInterval);
      stompClient.deactivate();
    };
  }, [transactionId]);

  const getStepState = (stepKey) => {
    const isFailed = latestStatus?.transactionStatus === 'FAILED' || events.some(e => e.transactionStatus === 'FAILED');
    const isExpired = latestStatus?.transactionStatus === 'EXPIRED' || events.some(e => e.transactionStatus === 'EXPIRED');
    
    // Find if we have an event for this stepKey
    const stepEvent = events.find(e => e.currentStage === stepKey);
    const hasEvent = !!stepEvent;

    // Check if this step is failed
    if ((isFailed || isExpired) && latestStatus?.currentStage === stepKey) {
      return { status: 'FAILED', event: latestStatus };
    }

    if (hasEvent) {
      // If we have an event for a subsequent stage, then this stage is SUCCESS
      if (stepEvent.transactionStatus === 'FAILED' || stepEvent.transactionStatus === 'EXPIRED') {
        return { status: 'FAILED', event: stepEvent };
      }
      return { status: 'SUCCESS', event: stepEvent };
    }

    // Is it currently active?
    const isActive = latestStatus?.currentStage === stepKey && !(latestStatus?.transactionStatus === 'SUCCESS' || latestStatus?.transactionStatus === 'FAILED');
    if (isActive) {
      return { status: 'ACTIVE', event: latestStatus };
    }

    return { status: 'PENDING', event: null };
  };

  const getStatusColor = () => {
    if (latestStatus?.transactionStatus === 'SUCCESS') return 'text-emerald-400';
    if (latestStatus?.transactionStatus === 'FAILED') return 'text-red-400';
    if (latestStatus?.transactionStatus === 'EXPIRED') return 'text-amber-400';
    return 'text-sky-400';
  };

  return (
    <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 shadow-2xl relative overflow-hidden backdrop-blur">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-slate-850 pb-5 mb-6">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            Live Transaction Tracker
            <span className="text-xs font-normal text-slate-500 font-mono">#{transactionId.substring(0, 8)}</span>
          </h2>
          <p className="text-xs text-slate-400 mt-1">Real-time Bluetooth mesh propagation metrics</p>
        </div>
        <div className="flex items-center gap-3">
          <span className={`px-3 py-1 rounded-full text-xs font-bold font-mono flex items-center gap-1.5 border ${
            connectionStatus === 'CONNECTED' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 
            connectionStatus === 'CONNECTING' ? 'bg-amber-500/10 text-amber-400 border-amber-500/20 animate-pulse' : 
            'bg-red-500/10 text-red-400 border-red-500/20'
          }`}>
            <span className={`w-1.5 h-1.5 rounded-full ${
              connectionStatus === 'CONNECTED' ? 'bg-emerald-400' : 
              connectionStatus === 'CONNECTING' ? 'bg-amber-400' : 'bg-red-400'
            }`} />
            WS: {connectionStatus}
          </span>
          {onClose && (
            <button 
              onClick={onClose}
              className="px-3 py-1 text-xs bg-slate-800 border border-slate-700 hover:bg-slate-700 text-slate-200 rounded-lg transition-all"
            >
              Clear Tracker
            </button>
          )}
        </div>
      </div>

      {/* Main Grid: Left Timeline, Right Dashboard */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Timeline (Left 1 Span) */}
        <div className="lg:col-span-1 border-r border-slate-850/40 pr-0 lg:pr-6">
          <h3 className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-6">Packet Routing Timeline</h3>
          <div className="relative pl-6 space-y-6">
            
            {/* Timeline Vertical Bar */}
            <div className="absolute left-[30px] top-3 bottom-3 w-[2px] bg-slate-800" />

            {steps.map((step) => {
              const { status, event } = getStepState(step.key);
              
              // Determine status styles
              let iconBg = 'bg-slate-950 border-slate-800 text-slate-600';
              let titleColor = 'text-slate-500';
              let lineDot = null;

              if (status === 'SUCCESS') {
                iconBg = 'bg-emerald-500/10 border-emerald-500 text-emerald-400';
                titleColor = 'text-slate-200';
              } else if (status === 'ACTIVE') {
                iconBg = 'bg-sky-500/20 border-sky-500 text-sky-400 ring-2 ring-sky-500/10';
                titleColor = 'text-white font-semibold';
                lineDot = <span className="absolute -left-[5px] -top-[5px] w-5 h-5 bg-sky-500 rounded-full animate-ping opacity-30" />;
              } else if (status === 'FAILED') {
                iconBg = 'bg-red-500/10 border-red-500 text-red-400';
                titleColor = 'text-red-400 font-semibold';
              }

              return (
                <div key={step.key} className="flex gap-4 relative">
                  
                  {/* Step Dot */}
                  <div className="relative z-10">
                    <div className={`w-[14px] h-[14px] rounded-full border-2 flex items-center justify-center transition-all ${iconBg}`}>
                      {lineDot}
                      {status === 'SUCCESS' && <span className="w-1.5 h-1.5 bg-emerald-400 rounded-full" />}
                      {status === 'ACTIVE' && <span className="w-1.5 h-1.5 bg-sky-400 rounded-full animate-pulse" />}
                      {status === 'FAILED' && <span className="w-1.5 h-1.5 bg-red-400 rounded-full" />}
                    </div>
                  </div>

                  {/* Step Details */}
                  <div className="flex-1 -mt-1">
                    <div className="flex justify-between items-start">
                      <span className={`text-xs ${titleColor}`}>{step.label}</span>
                      {event && (
                        <span className="text-[10px] text-slate-500 font-mono">
                          {new Date(event.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                        </span>
                      )}
                    </div>
                    {event && (
                      <p className="text-[10px] text-slate-400 mt-0.5 leading-relaxed font-mono">
                        {event.message}
                      </p>
                    )}
                  </div>
                </div>
              );
            })}

          </div>
        </div>

        {/* Live Metrics & Terminal Console (Right 2 Spans) */}
        <div className="lg:col-span-2 flex flex-col justify-between space-y-6">
          
          {/* Live Metrics Board */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            
            <div className="bg-slate-950/60 p-4 rounded-2xl border border-slate-850">
              <span className="text-[10px] text-slate-500 uppercase font-semibold">Transaction Status</span>
              <div className={`text-sm font-extrabold mt-1 uppercase ${getStatusColor()}`}>
                {latestStatus?.transactionStatus || 'ENCRYPTED'}
              </div>
            </div>

            <div className="bg-slate-950/60 p-4 rounded-2xl border border-slate-850">
              <span className="text-[10px] text-slate-500 uppercase font-semibold">Current Hop Count</span>
              <div className="text-sm font-extrabold text-white mt-1 font-mono">
                {latestStatus ? `${latestStatus.hopCount} / 5` : '0 / 5'}
              </div>
            </div>

            <div className="bg-slate-950/60 p-4 rounded-2xl border border-slate-850">
              <span className="text-[10px] text-slate-500 uppercase font-semibold">Service stage</span>
              <div className="text-sm font-extrabold text-sky-400 mt-1 uppercase font-mono">
                {latestStatus?.serviceName || 'User-Service'}
              </div>
            </div>

            <div className="bg-slate-950/60 p-4 rounded-2xl border border-slate-850 col-span-2 sm:col-span-1">
              <span className="text-[10px] text-slate-500 uppercase font-semibold">Current Node Status</span>
              <div className="text-sm font-extrabold text-white mt-1">
                {latestStatus?.packetStatus || 'CREATED'}
              </div>
            </div>

            <div className="bg-slate-950/60 p-4 rounded-2xl border border-slate-850 col-span-2 sm:col-span-4">
              <span className="text-[10px] text-slate-500 uppercase font-semibold">Bluetooth Route Trace</span>
              <div className="text-xs font-mono text-emerald-400 mt-1 select-all">
                {latestStatus && latestStatus.routeHistory && latestStatus.routeHistory.length > 0 ? (
                  latestStatus.routeHistory.join(' ➔ ')
                ) : (
                  'Transaction-Service'
                )}
              </div>
            </div>

          </div>

          {/* Terminal Console Log */}
          <div>
            <h4 className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-3">Service Trace Log</h4>
            <div className="bg-slate-950 rounded-2xl p-4 font-mono text-[11px] border border-slate-850 h-56 overflow-y-auto space-y-2">
              {events.length === 0 ? (
                <div className="text-slate-600 italic">Awaiting transaction WebSocket stream packages...</div>
              ) : (
                events.map((evt, idx) => (
                  <div key={idx} className="flex gap-2 items-start text-sky-350">
                    <span className="text-slate-600 select-none">[{idx + 1}]</span>
                    <span className="text-slate-500">[{new Date(evt.timestamp).toLocaleTimeString()}]</span>
                    <span className="text-sky-500">[{evt.serviceName}]</span>
                    <span className={
                      evt.transactionStatus === 'SUCCESS' ? 'text-emerald-400' :
                      evt.transactionStatus === 'FAILED' ? 'text-red-400' :
                      evt.message.includes('Retry') ? 'text-amber-400 animate-pulse' : 'text-slate-350'
                    }>
                      {evt.message}
                    </span>
                  </div>
                ))
              )}
              <div ref={consoleEndRef} />
            </div>
          </div>

        </div>

      </div>
      
    </div>
  );
}
