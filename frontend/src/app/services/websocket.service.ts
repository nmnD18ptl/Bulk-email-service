import { Injectable } from '@angular/core';
import { BehaviorSubject, Subject } from 'rxjs';
import { Client } from '@stomp/stompjs';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class WebSocketService {

  private client: Client | null = null;
  private subscriptions: Map<string, any> = new Map();

  public campaignProgress$ = new Subject<any>();
  public connected$ = new BehaviorSubject<boolean>(false);

  connect(): void {
    if (this.client?.active) return;

    try {
      this.client = new Client({
        brokerURL: environment.wsUrl.replace('http://', 'ws://').replace('https://', 'wss://'),
        reconnectDelay: 5000,
        onConnect: () => {
          console.log('WebSocket connected');
          this.connected$.next(true);
          // Re-subscribe to all previously subscribed topics after reconnect
          this.resubscribeAll();
        },
        onDisconnect: () => {
          console.log('WebSocket disconnected');
          this.connected$.next(false);
        },
        onStompError: (frame) => {
          console.warn('STOMP error:', frame);
          this.connected$.next(false);
        }
      });

      this.client.activate();
    } catch (e) {
      console.warn('WebSocket not available:', e);
      this.connected$.next(false);
    }
  }

  subscribeToCampaignProgress(campaignId: number): void {
    const topic = `/topic/campaign-progress/${campaignId}`;
    if (this.subscriptions.has(topic)) return;

    // Store intent so we can re-subscribe after reconnect
    this.subscriptions.set(topic, null);

    if (!this.client?.connected) return;
    this.doSubscribe(topic);
  }

  private doSubscribe(topic: string): void {
    if (!this.client?.connected) return;
    const sub = this.client.subscribe(topic, (message) => {
      try {
        const data = JSON.parse(message.body);
        this.campaignProgress$.next(data);
      } catch (e) { /* ignore */ }
    });
    this.subscriptions.set(topic, sub);
  }

  private resubscribeAll(): void {
    this.subscriptions.forEach((sub, topic) => {
      if (!sub) this.doSubscribe(topic);
    });
  }

  unsubscribeFromCampaign(campaignId: number): void {
    const topic = `/topic/campaign-progress/${campaignId}`;
    const sub = this.subscriptions.get(topic);
    if (sub) sub.unsubscribe();
    this.subscriptions.delete(topic);
  }

  disconnect(): void {
    this.subscriptions.forEach(sub => { if (sub) sub.unsubscribe(); });
    this.subscriptions.clear();
    this.client?.deactivate();
    this.client = null;
    this.connected$.next(false);
  }
}
