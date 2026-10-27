"use client";

import { useEffect, useState } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { api, API_BASE, OrderBookResponse, TradeResponse } from "./api";

interface MarketSocketState {
  connected: boolean;
  orderBook: OrderBookResponse | null;
  trades: TradeResponse[];
  /** Incremente a chaque evenement de marche: sert a rafraichir wallet et ordres. */
  version: number;
}

/**
 * Carnet + flux de transactions d'un symbole:
 * 1. charge l'etat initial par l'API REST (sinon l'ecran reste vide
 *    jusqu'au premier evenement),
 * 2. puis suit les mises a jour en direct via WebSocket (STOMP sur SockJS).
 */
export function useMarketSocket(symbol: string, maxTrades = 30): MarketSocketState {
  const [connected, setConnected] = useState(false);
  // Chaque donnee est memorisee AVEC son symbole: apres un changement de
  // symbole, les donnees de l'ancien ne sont simplement plus affichees.
  const [orderBook, setOrderBook] = useState<{ symbol: string; book: OrderBookResponse } | null>(null);
  const [trades, setTrades] = useState<{ symbol: string; list: TradeResponse[] }>({ symbol, list: [] });
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let cancelled = false;

    api
      .orderBook(symbol)
      .then((book) => !cancelled && setOrderBook({ symbol, book }))
      .catch(() => {});
    api
      .recentTrades(symbol)
      .then((recent) => !cancelled && setTrades({ symbol, list: recent.slice(0, maxTrades) }))
      .catch(() => {});

    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws`) as unknown as WebSocket,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/market/${symbol}/orderbook`, (message) => {
          setOrderBook({ symbol, book: JSON.parse(message.body) });
          setVersion((v) => v + 1);
        });
        client.subscribe(`/topic/market/${symbol}/trades`, (message) => {
          const trade: TradeResponse = JSON.parse(message.body);
          setTrades((prev) => {
            const list = prev.symbol === symbol ? prev.list : [];
            return { symbol, list: [trade, ...list.filter((t) => t.id !== trade.id)].slice(0, maxTrades) };
          });
        });
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    client.activate();

    return () => {
      cancelled = true;
      client.deactivate();
    };
  }, [symbol, maxTrades]);

  return {
    connected,
    orderBook: orderBook?.symbol === symbol ? orderBook.book : null,
    trades: trades.symbol === symbol ? trades.list : [],
    version,
  };
}
