import React, { useState, useEffect, useMemo } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axios from 'axios';

export default function TransactionTracker({ transactionId, onClose }) {
  const [connectionStatus, setConnectionStatus] = useState('CONNECTING');
  const [events, setEvents] = useState([]);

  const steps = [
    { key: 'CREATED', label: 'Created' },
    { key: 'ENCRYPTED', label: 'Encrypted' },
    { key: 'RELAY_1', label: 'Relay 1' },
    { key: 'RELAY_2', label: 'Relay 2' },
    { key: 'GATEWAY', label: 'Gateway' },
    { key: 'BANK', label: 'Bank' },
    { key: 'COMPLETED', label: 'Completed' }
  ];

  // Derive the "best" status from ALL events rather than relying on the last WS event.
  const derivedStatus = useMemo(() => {
    if (events.length === 0) return null;

    // Stage priority order (higher index = more advanced)
    const stagePriority = { 'CREATED': 0, 'ENCRYPTED': 1, 'RELAY_1': 2, 'RELAY_2': 3, 'GATEWAY': 4, 'BANK': 5, 'COMPLETED': 6 };
    // Terminal statuses always win
    const terminalEvent = events.find(e =>
      e.transactionStatus === 'SUCCESS' || e.transactionStatus === 'FAILED' || e.transactionStatus === 'EXPIRED' || e.currentStage === 'COMPLETED'
    );
    if (terminalEvent) return terminalEvent;

    // Otherwise, return the event with the highest stage priority
    let best = events[0];
    for (const evt of events) {
      const evtPriority = stagePriority[evt.currentStage] ?? -1;
      const bestPriority = stagePriority[best.currentStage] ?? -1;
      if (evtPriority > bestPriority) {
        best = evt;
      }
    }
    return best;
  }, [events]);

  // Derive the fullest route history from all events
  const fullRouteHistory = useMemo(() => {
    if (events.length === 0) return ['Transaction-Service'];
    
    // Build route from step stages we've seen
    const stageOrder = ['CREATED', 'ENCRYPTED', 'RELAY_1', 'RELAY_2', 'GATEWAY', 'BANK', 'COMPLETED'];
    const stageToNode = {
      'CREATED': 'Transaction-Service',
      'ENCRYPTED': 'Transaction-Service',
      'RELAY_1': 'Relay-1',
      'RELAY_2': 'Relay-2',
      'GATEWAY': 'Gateway',
      'BANK': 'Bank-Service',
      'COMPLETED': 'Bank-Service'
    };
    
    const seenStages = new Set(events.map(e => e.currentStage));
    const route = [];
    const addedNodes = new Set();
    
    for (const stage of stageOrder) {
      if (seenStages.has(stage)) {
        const node = stageToNode[stage];
        if (!addedNodes.has(node)) {
          route.push(node);
          addedNodes.add(node);
        }
      }
    }
    
    return route.length > 0 ? route : ['Transaction-Service'];
  }, [events]);

  useEffect(() => {
    if (!transactionId) return;

    setConnectionStatus('CONNECTING');
    setEvents([]);
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
        console.log('[Tracker] WS Event received:', event.currentStage, event.transactionStatus, event.message);

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
    const isFailed = events.some(e => e.transactionStatus === 'FAILED');
    const isExpired = events.some(e => e.transactionStatus === 'EXPIRED');
    const isSuccess = events.some(e => e.transactionStatus === 'SUCCESS' || e.currentStage === 'COMPLETED');
    
    // Find if we have an event for this stepKey (search in reverse to get the most recent message)
    const stepEvent = [...events].reverse().find(e => e.currentStage === stepKey);
    const hasEvent = !!stepEvent;

    // If the overall transaction failed, find where it failed
    if (isFailed || isExpired) {
      const failedEvent = events.find(e => e.transactionStatus === 'FAILED' || e.transactionStatus === 'EXPIRED');
      if (failedEvent && failedEvent.currentStage === stepKey) {
        return { status: 'FAILED', event: failedEvent };
      }
      if (hasEvent && stepEvent.transactionStatus !== 'FAILED' && stepEvent.transactionStatus !== 'EXPIRED') {
        return { status: 'SUCCESS', event: stepEvent };
      }
    }

    if (hasEvent) {
      if (stepEvent.transactionStatus === 'FAILED' || stepEvent.transactionStatus === 'EXPIRED') {
        return { status: 'FAILED', event: stepEvent };
      }
      return { status: 'SUCCESS', event: stepEvent };
    }

    if (isSuccess) {
      return { status: 'PENDING', event: null };
    }

    if (derivedStatus && derivedStatus.currentStage === stepKey) {
      return { status: 'ACTIVE', event: derivedStatus };
    }

    return { status: 'PENDING', event: null };
  };

  const getStatusColor = () => {
    const txnStatus = derivedStatus?.transactionStatus;
    if (txnStatus === 'SUCCESS') return 'text-emerald-600';
    if (txnStatus === 'FAILED') return 'text-red-600';
    if (txnStatus === 'EXPIRED') return 'text-amber-600';
    return 'text-blue-600';
  };

  const getDisplayStatus = () => {
    if (!derivedStatus) return 'ENCRYPTED';
    const status = derivedStatus.transactionStatus;
    if (status === 'SUCCESS') return 'SUCCESS';
    if (status === 'FAILED') return 'FAILED';
    if (status === 'EXPIRED') return 'EXPIRED';
    return status || 'ROUTING';
  };

  return (
    <div className="bg-white border border-slate-200 rounded-3xl p-6 shadow-md relative overflow-hidden text-slate-900 mb-6">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-slate-200 pb-5 mb-6">
        <div>
          <h2 className="text-xl font-bold text-slate-900 flex items-center gap-2">
            Live Transaction Tracker
            <span className="text-xs font-normal text-slate-500 font-mono">#{transactionId.substring(0, 8)}</span>
          </h2>
          <p className="text-xs text-slate-500 mt-1">Real-time Bluetooth mesh propagation metrics</p>
        </div>
        <div className="flex items-center gap-3">
          {onClose && (
            <button 
              onClick={onClose}
              className="px-3 py-1 text-xs bg-slate-100 border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-200 transition-all cursor-pointer"
            >
              Clear Tracker
            </button>
          )}
        </div>
      </div>

      {/* Horizontal Packet Routing Timeline */}
      <div className="mb-8">
        <h3 className="text-xs font-bold text-slate-500 uppercase tracking-widest mb-6">Packet Routing Timeline</h3>
        <div className="relative flex flex-col md:flex-row justify-between items-center w-full gap-4 md:gap-2 px-2">
          {/* Connector Line */}
          <div className="absolute left-0 right-0 top-1/2 h-0.5 bg-slate-200 -translate-y-1/2 hidden md:block z-0" />
          
          {steps.map((step, idx) => {
            const { status } = getStepState(step.key);
            
            let circleStyle = 'bg-white border-slate-200 text-slate-400';
            let labelStyle = 'text-slate-400';
            let ringPulse = null;

            if (status === 'SUCCESS') {
              circleStyle = 'bg-emerald-500 border-emerald-500 text-white font-bold';
              labelStyle = 'text-emerald-600 font-semibold';
            } else if (status === 'ACTIVE') {
              circleStyle = 'bg-blue-600 border-blue-600 text-white font-bold';
              labelStyle = 'text-blue-600 font-bold';
              ringPulse = <span className="absolute inset-0 rounded-full bg-blue-500/30 animate-ping" />;
            } else if (status === 'FAILED') {
              circleStyle = 'bg-red-500 border-red-500 text-white font-bold';
              labelStyle = 'text-red-600 font-bold';
            }

            return (
              <div key={step.key} className="flex flex-col items-center z-10 bg-white px-3 relative w-full md:w-auto">
                <div className={`relative w-8 h-8 rounded-full border-2 flex items-center justify-center text-xs transition-all duration-300 ${circleStyle}`}>
                  {ringPulse}
                  {status === 'SUCCESS' ? (
                    <svg className="w-4.5 h-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="3">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
                    </svg>
                  ) : (
                    <span>{idx + 1}</span>
                  )}
                </div>
                <span className={`text-[11px] mt-2 whitespace-nowrap text-center ${labelStyle}`}>{step.label}</span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Live Metrics Row */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        
        <div className="bg-slate-50 p-4 rounded-2xl border border-slate-200">
          <span className="text-[10px] text-slate-500 uppercase font-semibold">Transaction Status</span>
          <div className={`text-xl font-extrabold mt-1 uppercase ${getStatusColor()}`}>
            {getDisplayStatus()}
          </div>
        </div>

        <div className="bg-slate-50 p-4 rounded-2xl border border-slate-200">
          <span className="text-[10px] text-slate-500 uppercase font-semibold">Bluetooth Route Trace</span>
          <div className="text-sm font-mono text-slate-700 mt-1.5 select-all">
            {fullRouteHistory.join(' ➔ ')}
          </div>
        </div>

      </div>
      
    </div>
  );
}
