"use client";

import { useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { API_BASE, OrderBookResponse, TradeResponse } from "./api";

interface MarketSocketState {
  connected: boolean;
  orderBook: OrderBookResponse | null;
  trades: TradeResponse[];
}

export function useMarketSocket(symbol: string, maxTrades = 30): MarketSocketState {
  const [connected, setConnected] = useState(false);
  const [orderBook, setOrderBook] = useState<OrderBookResponse | null>(null);
  const [trades, setTrades] = useState<TradeResponse[]>([]);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws`) as unknown as WebSocket,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/market/${symbol}/orderbook`, (message) => {
          setOrderBook(JSON.parse(message.body));
        });
        client.subscribe(`/topic/market/${symbol}/trades`, (message) => {
          const trade: TradeResponse = JSON.parse(message.body);
          setTrades((prev) => [trade, ...prev].slice(0, maxTrades));
        });
      },
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, [symbol, maxTrades]);

  return { connected, orderBook, trades };
}
