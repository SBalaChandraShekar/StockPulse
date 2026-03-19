import { useEffect, useRef, useState } from "react";
import axios from "axios";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL ?? API_BASE_URL;
const PORTFOLIO_HISTORY_LIMIT = 24;
const INITIAL_PORTFOLIO_CASH = 100000;

const api = axios.create({
  baseURL: API_BASE_URL,
});

function App() {
  const [authMode, setAuthMode] = useState("login");
  const [authForm, setAuthForm] = useState({
    fullName: "",
    email: "bala@example.com",
    password: "Password123",
  });
  const [token, setToken] = useState(() => localStorage.getItem("stockpulse-token") ?? "");
  const [userEmail, setUserEmail] = useState(() => localStorage.getItem("stockpulse-email") ?? "");
  const [portfolio, setPortfolio] = useState(null);
  const [trades, setTrades] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [leaderboard, setLeaderboard] = useState([]);
  const [portfolioHistory, setPortfolioHistory] = useState([]);
  const [tradeForm, setTradeForm] = useState({ symbol: "AAPL", quantity: 1, side: "buy" });
  const [alertForm, setAlertForm] = useState({ symbol: "AAPL", direction: "ABOVE", targetPrice: 200 });
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("Connect your account to start the live desk.");
  const [error, setError] = useState("");
  const [socketState, setSocketState] = useState("offline");
  const clientRef = useRef(null);
  const subscriptionsRef = useRef(new Map());
  const activeSymbolsRef = useRef([]);

  const holdingSymbols = portfolio?.holdings?.map((holding) => holding.symbol.toUpperCase()) ?? [];
  const pnlChartData =
    portfolio?.holdings?.map((holding) => ({
      symbol: holding.symbol,
      pnl: Number(holding.unrealizedProfitLoss),
      fill: Number(holding.unrealizedProfitLoss) >= 0 ? "#2fcf8f" : "#ff6b6b",
    })) ?? [];

  useEffect(() => {
    if (!token) {
      disconnectSocket();
      setPortfolio(null);
      setTrades([]);
      setAlerts([]);
      setLeaderboard([]);
      setPortfolioHistory([]);
      setSocketState("offline");
      return;
    }

    loadDashboard(token);
    connectSocket();

    return () => {
      disconnectSocket();
    };
  }, [token]);

  useEffect(() => {
    activeSymbolsRef.current = holdingSymbols;
    if (clientRef.current?.connected) {
      syncSubscriptions(holdingSymbols);
    }
  }, [holdingSymbols.join("|"), socketState]);

  function authHeaders(currentToken = token) {
    return currentToken ? { Authorization: `Bearer ${currentToken}` } : {};
  }

  async function loadDashboard(currentToken = token) {
    if (!currentToken) {
      return;
    }

    try {
      setBusy(true);
      setError("");
      const [portfolioResponse, tradesResponse, alertsResponse, leaderboardResponse] = await Promise.all([
        api.get("/api/portfolio", { headers: authHeaders(currentToken) }),
        api.get("/api/portfolio/trades", { headers: authHeaders(currentToken) }),
        api.get("/api/alerts", { headers: authHeaders(currentToken) }),
        api.get("/api/leaderboard", { headers: authHeaders(currentToken) }),
      ]);

      setPortfolio(portfolioResponse.data);
      setTrades(tradesResponse.data);
      setAlerts(alertsResponse.data);
      setLeaderboard(leaderboardResponse.data);
      appendPortfolioSnapshot(portfolioResponse.data, "REST sync");
      setNotice("REST portfolio synced. STOMP subscriptions are ready.");
    } catch (requestError) {
      setError(extractErrorMessage(requestError));
    } finally {
      setBusy(false);
    }
  }

  async function handleAuthSubmit(event) {
    event.preventDefault();
    setBusy(true);
    setError("");

    try {
      const path = authMode === "login" ? "/api/auth/login" : "/api/auth/register";
      const payload =
        authMode === "login"
          ? { email: authForm.email, password: authForm.password }
          : {
              fullName: authForm.fullName,
              email: authForm.email,
              password: authForm.password,
            };

      const response = await api.post(path, payload);
      setToken(response.data.token);
      setUserEmail(response.data.email);
      localStorage.setItem("stockpulse-token", response.data.token);
      localStorage.setItem("stockpulse-email", response.data.email);
      setNotice(`${authMode === "login" ? "Logged in" : "Registered"} as ${response.data.email}.`);
    } catch (requestError) {
      setError(extractErrorMessage(requestError));
    } finally {
      setBusy(false);
    }
  }

  async function handleTradeSubmit(event) {
    event.preventDefault();
    if (!token) {
      setError("Login first to execute trades.");
      return;
    }

    setBusy(true);
    setError("");

    try {
      const response = await api.post(
        `/api/portfolio/${tradeForm.side}`,
        {
          symbol: tradeForm.symbol,
          quantity: Number(tradeForm.quantity),
        },
        { headers: authHeaders() },
      );

      setNotice(
        `${response.data.tradeType} ${response.data.quantity} ${response.data.symbol} @ ${formatCurrency(
          response.data.executedPrice,
        )}`,
      );
      await loadDashboard();
    } catch (requestError) {
      setError(extractErrorMessage(requestError));
    } finally {
      setBusy(false);
    }
  }

  async function handleAlertSubmit(event) {
    event.preventDefault();
    if (!token) {
      setError("Login first to create alerts.");
      return;
    }

    setBusy(true);
    setError("");

    try {
      const response = await api.post(
        "/api/alerts",
        {
          symbol: alertForm.symbol,
          direction: alertForm.direction,
          targetPrice: Number(alertForm.targetPrice),
        },
        { headers: authHeaders() },
      );
      setAlerts((current) => [response.data, ...current]);
      setNotice(`Alert created for ${response.data.symbol} ${response.data.direction} ${formatCurrency(response.data.targetPrice)}.`);
    } catch (requestError) {
      setError(extractErrorMessage(requestError));
    } finally {
      setBusy(false);
    }
  }

  function connectSocket() {
    if (clientRef.current) {
      return;
    }

    const client = new Client({
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      webSocketFactory: () => new SockJS(`${WS_BASE_URL}/ws`),
      onConnect: () => {
        setSocketState("live");
        syncSubscriptions(activeSymbolsRef.current);
        setNotice("WebSocket connected. Waiting for scheduled ticker pushes.");
      },
      onDisconnect: () => {
        setSocketState("offline");
      },
      onStompError: (frame) => {
        setSocketState("error");
        setError(frame.headers.message || "STOMP broker reported an error.");
      },
      onWebSocketClose: () => {
        setSocketState("offline");
      },
      onWebSocketError: () => {
        setSocketState("error");
      },
    });

    client.activate();
    clientRef.current = client;
    setSocketState("connecting");
  }

  function disconnectSocket() {
    subscriptionsRef.current.forEach((subscription) => subscription.unsubscribe());
    subscriptionsRef.current.clear();

    if (clientRef.current) {
      clientRef.current.deactivate();
      clientRef.current = null;
    }
  }

  function syncSubscriptions(symbols) {
    const nextSymbols = new Set(symbols.map((symbol) => symbol.toUpperCase()));

    subscriptionsRef.current.forEach((subscription, symbol) => {
      if (!nextSymbols.has(symbol)) {
        subscription.unsubscribe();
        subscriptionsRef.current.delete(symbol);
      }
    });

    nextSymbols.forEach((symbol) => {
      if (subscriptionsRef.current.has(symbol) || !clientRef.current?.connected) {
        return;
      }

      const subscription = clientRef.current.subscribe(`/topic/prices/${symbol}`, (message) => {
        handlePriceUpdate(JSON.parse(message.body));
      });

      subscriptionsRef.current.set(symbol, subscription);
    });
  }

  function handlePriceUpdate(update) {
    setPortfolio((currentPortfolio) => {
      if (!currentPortfolio) {
        return currentPortfolio;
      }

      const nextHoldings = currentPortfolio.holdings.map((holding) => {
        if (holding.symbol.toUpperCase() !== update.symbol.toUpperCase()) {
          return holding;
        }

        const nextCurrentPrice = Number(update.price);
        const marketValue = roundMoney(nextCurrentPrice * Number(holding.quantity));
        const unrealizedProfitLoss = roundMoney(
          (nextCurrentPrice - Number(holding.averageBuyPrice)) * Number(holding.quantity),
        );

        return {
          ...holding,
          currentPrice: roundPrice(nextCurrentPrice),
          marketValue,
          unrealizedProfitLoss,
        };
      });

      const holdingsMarketValue = roundMoney(
        nextHoldings.reduce((sum, holding) => sum + Number(holding.marketValue), 0),
      );
      const totalPortfolioValue = roundMoney(Number(currentPortfolio.cashBalance) + holdingsMarketValue);

      const nextPortfolio = {
        ...currentPortfolio,
        holdings: nextHoldings,
        holdingsMarketValue,
        totalPortfolioValue,
      };

      appendPortfolioSnapshot(nextPortfolio, `STOMP ${update.symbol}`);
      setNotice(`Live update received for ${update.symbol} at ${formatTime(update.fetchedAt)}.`);
      return nextPortfolio;
    });
  }

  function appendPortfolioSnapshot(nextPortfolio, source) {
    if (!nextPortfolio) {
      return;
    }

    setPortfolioHistory((currentHistory) => {
      const snapshot = {
        label: new Date().toLocaleTimeString([], {
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
        }),
        totalPortfolioValue: Number(nextPortfolio.totalPortfolioValue),
        holdingsMarketValue: Number(nextPortfolio.holdingsMarketValue),
        source,
      };

      return [...currentHistory.slice(-(PORTFOLIO_HISTORY_LIMIT - 1)), snapshot];
    });
  }

  function handleLogout() {
    setToken("");
    setUserEmail("");
    localStorage.removeItem("stockpulse-token");
    localStorage.removeItem("stockpulse-email");
    setNotice("Session cleared.");
    setError("");
  }

  const overallPnl = portfolio
    ? roundMoney(Number(portfolio.totalPortfolioValue) - INITIAL_PORTFOLIO_CASH)
    : 0;

  if (!token) {
    return (
      <div className="dashboard-shell min-h-screen px-4 py-6 text-slate-50 sm:px-6 lg:px-10">
        <div className="mx-auto grid min-h-[calc(100vh-3rem)] max-w-7xl items-center gap-6 lg:grid-cols-[1.1fr,420px]">
          <section className="glass-panel rounded-[36px] px-6 py-8 sm:px-10">
            <p className="mono text-xs uppercase tracking-[0.34em] text-cyan-300/80">StockPulse Live Desk</p>
            <h1 className="mt-4 max-w-3xl text-4xl font-bold tracking-tight text-white sm:text-6xl">
              Trade, stream, and present your portfolio story in real time.
            </h1>
            <p className="mt-5 max-w-2xl text-base leading-7 text-slate-300">
              This demo frontend connects to your Spring Boot backend over REST and STOMP over SockJS. Once you log in,
              the dashboard switches into a live market desk with streaming tickers, trade execution, and animated P&amp;L
              charts.
            </p>

            <div className="mt-8 grid gap-4 sm:grid-cols-3">
              <MetricCard label="Backend" value="Spring Boot + JWT" />
              <MetricCard label="Streaming" value="WebSocket / STOMP" accent="text-cyan-300" />
              <MetricCard label="Showpiece" value="Recharts P&L" accent="text-emerald-300" />
            </div>
          </section>

          <aside className="flex flex-col gap-6">
            <section className="glass-panel rounded-[28px] p-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="mono text-xs uppercase tracking-[0.28em] text-slate-400">Access</p>
                  <h2 className="mt-2 text-2xl font-semibold">Auth Console</h2>
                </div>
              </div>

              <div className="mt-5 flex gap-2 rounded-full border border-white/10 bg-white/5 p-1">
                <button
                  className={`flex-1 rounded-full px-4 py-2 text-sm transition ${
                    authMode === "login" ? "bg-amber-400 text-slate-950" : "text-slate-300"
                  }`}
                  onClick={() => setAuthMode("login")}
                  type="button"
                >
                  Login
                </button>
                <button
                  className={`flex-1 rounded-full px-4 py-2 text-sm transition ${
                    authMode === "register" ? "bg-cyan-300 text-slate-950" : "text-slate-300"
                  }`}
                  onClick={() => setAuthMode("register")}
                  type="button"
                >
                  Register
                </button>
              </div>

              <form className="mt-5 space-y-4" onSubmit={handleAuthSubmit}>
                {authMode === "register" ? (
                  <Field
                    label="Full Name"
                    value={authForm.fullName}
                    onChange={(value) => setAuthForm((current) => ({ ...current, fullName: value }))}
                    placeholder="Bala"
                  />
                ) : null}
                <Field
                  label="Email"
                  type="email"
                  value={authForm.email}
                  onChange={(value) => setAuthForm((current) => ({ ...current, email: value }))}
                  placeholder="bala@example.com"
                />
                <Field
                  label="Password"
                  type="password"
                  value={authForm.password}
                  onChange={(value) => setAuthForm((current) => ({ ...current, password: value }))}
                  placeholder="Password123"
                />
                <button
                  className="w-full rounded-2xl bg-gradient-to-r from-amber-400 to-orange-300 px-4 py-3 font-medium text-slate-950 transition hover:brightness-105 disabled:cursor-not-allowed disabled:opacity-70"
                  disabled={busy}
                  type="submit"
                >
                  {busy ? "Working..." : authMode === "login" ? "Enter Live Desk" : "Create Demo Account"}
                </button>
              </form>
            </section>

            <StatusPanel notice={notice} error={error} />
          </aside>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard-shell min-h-screen px-4 py-6 text-slate-50 sm:px-6 lg:px-10">
      <div className="mx-auto flex max-w-7xl flex-col gap-6">
        <header className="glass-panel overflow-hidden rounded-[32px] border px-6 py-6 sm:px-8">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-2xl">
              <p className="mono text-xs uppercase tracking-[0.34em] text-cyan-300/80">StockPulse Live Desk</p>
              <h1 className="mt-3 text-4xl font-bold tracking-tight text-white sm:text-5xl">
                Real-time portfolio streaming for your Spring Boot demo.
              </h1>
              <p className="mt-4 max-w-xl text-sm leading-6 text-slate-300 sm:text-base">
                REST handles auth and trades, STOMP over SockJS streams ticker moves, and the chart deck redraws your
                portfolio story live while the backend scheduler refreshes prices.
              </p>
            </div>

            <div className="grid gap-3 sm:grid-cols-3">
              <MetricCard
                label="Socket"
                value={socketState.toUpperCase()}
                accent={socketState === "live" ? "text-emerald-300" : "text-amber-300"}
              />
              <MetricCard label="Portfolio Value" value={portfolio ? formatCurrency(portfolio.totalPortfolioValue) : "--"} />
              <MetricCard
                label="Net P&L"
                value={portfolio ? formatSignedCurrency(overallPnl) : "--"}
                accent={overallPnl >= 0 ? "text-emerald-300" : "text-rose-300"}
              />
            </div>
          </div>
        </header>

        <div className="grid gap-6 xl:grid-cols-[360px,1fr]">
          <aside className="flex flex-col gap-6">
            <section className="glass-panel rounded-[28px] p-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="mono text-xs uppercase tracking-[0.28em] text-slate-400">Session</p>
                  <h2 className="mt-2 text-2xl font-semibold">Operator Console</h2>
                </div>
                <button
                  className="rounded-full border border-white/10 px-4 py-2 text-sm text-slate-200 transition hover:border-white/30 hover:bg-white/5"
                  onClick={handleLogout}
                  type="button"
                >
                  Log Out
                </button>
              </div>

              <div className="mt-5 rounded-2xl bg-slate-950/40 p-4 text-sm text-slate-300">
                <p className="font-medium text-white">Session</p>
                <p className="mt-2 break-all">{userEmail || "No active session"}</p>
              </div>
            </section>

            <section className="glass-panel rounded-[28px] p-6">
              <p className="mono text-xs uppercase tracking-[0.28em] text-slate-400">Trade Launcher</p>
              <h2 className="mt-2 text-2xl font-semibold">Execute Buy / Sell</h2>
              <form className="mt-5 space-y-4" onSubmit={handleTradeSubmit}>
                <Field
                  label="Ticker"
                  value={tradeForm.symbol}
                  onChange={(value) => setTradeForm((current) => ({ ...current, symbol: value.toUpperCase() }))}
                  placeholder="AAPL"
                />
                <Field
                  label="Quantity"
                  type="number"
                  min="1"
                  value={tradeForm.quantity}
                  onChange={(value) => setTradeForm((current) => ({ ...current, quantity: Number(value) || 1 }))}
                />
                <div className="grid grid-cols-2 gap-3">
                  <button
                    type="button"
                    className={`rounded-2xl px-4 py-3 text-sm font-medium transition ${
                      tradeForm.side === "buy"
                        ? "bg-emerald-400 text-slate-950"
                        : "border border-white/10 text-slate-300"
                    }`}
                    onClick={() => setTradeForm((current) => ({ ...current, side: "buy" }))}
                  >
                    Buy
                  </button>
                  <button
                    type="button"
                    className={`rounded-2xl px-4 py-3 text-sm font-medium transition ${
                      tradeForm.side === "sell"
                        ? "bg-rose-400 text-slate-950"
                        : "border border-white/10 text-slate-300"
                    }`}
                    onClick={() => setTradeForm((current) => ({ ...current, side: "sell" }))}
                  >
                    Sell
                  </button>
                </div>
                <button
                  className="w-full rounded-2xl border border-amber-300/40 bg-amber-300/10 px-4 py-3 font-medium text-amber-100 transition hover:border-amber-200/70 hover:bg-amber-300/20 disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={!token || busy}
                  type="submit"
                >
                  {busy ? "Submitting..." : `Submit ${tradeForm.side.toUpperCase()} Order`}
                </button>
              </form>
            </section>

            <section className="glass-panel rounded-[28px] p-6">
              <p className="mono text-xs uppercase tracking-[0.28em] text-slate-400">Alerts</p>
              <h2 className="mt-2 text-2xl font-semibold">Price Triggers</h2>
              <form className="mt-5 space-y-4" onSubmit={handleAlertSubmit}>
                <Field
                  label="Ticker"
                  value={alertForm.symbol}
                  onChange={(value) => setAlertForm((current) => ({ ...current, symbol: value.toUpperCase() }))}
                  placeholder="AAPL"
                />
                <Field
                  label="Target Price"
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={alertForm.targetPrice}
                  onChange={(value) => setAlertForm((current) => ({ ...current, targetPrice: Number(value) || 0 }))}
                />
                <div className="grid grid-cols-2 gap-3">
                  <button
                    type="button"
                    className={`rounded-2xl px-4 py-3 text-sm font-medium transition ${
                      alertForm.direction === "ABOVE"
                        ? "bg-cyan-300 text-slate-950"
                        : "border border-white/10 text-slate-300"
                    }`}
                    onClick={() => setAlertForm((current) => ({ ...current, direction: "ABOVE" }))}
                  >
                    Above
                  </button>
                  <button
                    type="button"
                    className={`rounded-2xl px-4 py-3 text-sm font-medium transition ${
                      alertForm.direction === "BELOW"
                        ? "bg-cyan-300 text-slate-950"
                        : "border border-white/10 text-slate-300"
                    }`}
                    onClick={() => setAlertForm((current) => ({ ...current, direction: "BELOW" }))}
                  >
                    Below
                  </button>
                </div>
                <button
                  className="w-full rounded-2xl border border-cyan-300/40 bg-cyan-300/10 px-4 py-3 font-medium text-cyan-100 transition hover:border-cyan-200/70 hover:bg-cyan-300/20 disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={!token || busy}
                  type="submit"
                >
                  {busy ? "Saving..." : "Create Alert"}
                </button>
              </form>
            </section>

            <StatusPanel notice={notice} error={error} />
          </aside>

          <main className="grid gap-6">
            <section className="grid gap-6 lg:grid-cols-[1.6fr,1fr]">
              <div className="glass-panel rounded-[32px] p-6">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                  <div>
                    <p className="mono text-xs uppercase tracking-[0.28em] text-slate-400">Streaming Value</p>
                    <h2 className="mt-2 text-2xl font-semibold">Portfolio Pulse</h2>
                  </div>
                  <div className="flex items-center gap-2 text-sm text-slate-300">
                    <span
                      className={`pulse-dot inline-flex h-2.5 w-2.5 rounded-full ${
                        socketState === "live" ? "text-emerald-400" : "text-amber-300"
                      }`}
                    ></span>
                    {socketState === "live" ? "Live updates active" : "Awaiting stream"}
                  </div>
                </div>

                <div className="mt-6 h-72 min-w-0">
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={portfolioHistory}>
                      <defs>
                        <linearGradient id="portfolioFill" x1="0" x2="0" y1="0" y2="1">
                          <stop offset="5%" stopColor="#53d3ff" stopOpacity={0.65} />
                          <stop offset="95%" stopColor="#53d3ff" stopOpacity={0.02} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid stroke="rgba(255,255,255,0.08)" vertical={false} />
                      <XAxis dataKey="label" tick={{ fill: "#9fb4c6", fontSize: 12 }} axisLine={false} tickLine={false} />
                      <YAxis
                        tick={{ fill: "#9fb4c6", fontSize: 12 }}
                        axisLine={false}
                        tickLine={false}
                        tickFormatter={(value) => compactCurrency(value)}
                      />
                      <Tooltip
                        contentStyle={{
                          borderRadius: "16px",
                          border: "1px solid rgba(255,255,255,0.08)",
                          background: "rgba(7, 16, 25, 0.92)",
                          color: "#f8fafc",
                        }}
                      />
                      <Area
                        type="monotone"
                        dataKey="totalPortfolioValue"
                        stroke="#53d3ff"
                        strokeWidth={3}
                        fill="url(#portfolioFill)"
                        isAnimationActive
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>
              </div>

              <div className="glass-panel rounded-[32px] p-6">
                <p className="mono text-xs uppercase tracking-[0.28em] text-slate-400">Live P&amp;L</p>
                <h2 className="mt-2 text-2xl font-semibold">Holdings Heat</h2>
                <div className="mt-6 h-72 min-w-0">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={pnlChartData}>
                      <CartesianGrid stroke="rgba(255,255,255,0.08)" vertical={false} />
                      <XAxis dataKey="symbol" tick={{ fill: "#9fb4c6", fontSize: 12 }} axisLine={false} tickLine={false} />
                      <YAxis tick={{ fill: "#9fb4c6", fontSize: 12 }} axisLine={false} tickLine={false} />
                      <Tooltip
                        formatter={(value) => formatSignedCurrency(value)}
                        contentStyle={{
                          borderRadius: "16px",
                          border: "1px solid rgba(255,255,255,0.08)",
                          background: "rgba(7, 16, 25, 0.92)",
                          color: "#f8fafc",
                        }}
                      />
                      <Bar dataKey="pnl" radius={[12, 12, 0, 0]}>
                        {pnlChartData.map((entry) => (
                          <Cell key={entry.symbol} fill={entry.fill} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </div>
            </section>

            <section className="grid gap-6 xl:grid-cols-[1.3fr,0.9fr]">
              <div className="glass-panel rounded-[32px] p-6">
                <div className="flex items-end justify-between gap-4">
                  <div>
                    <p className="mono text-xs uppercase tracking-[0.28em] text-slate-400">Portfolio</p>
                    <h2 className="mt-2 text-2xl font-semibold">Position Sheet</h2>
                  </div>
                  <div className="grid grid-cols-2 gap-3 text-right sm:grid-cols-3">
                    <MiniMetric label="Cash" value={portfolio ? formatCurrency(portfolio.cashBalance) : "--"} />
                    <MiniMetric label="Holdings" value={portfolio ? formatCurrency(portfolio.holdingsMarketValue) : "--"} />
                    <MiniMetric label="Total" value={portfolio ? formatCurrency(portfolio.totalPortfolioValue) : "--"} />
                  </div>
                </div>

                <div className="mt-6 overflow-hidden rounded-[24px] border border-white/8">
                  <table className="min-w-full divide-y divide-white/8 text-sm">
                    <thead className="bg-white/5 text-left text-slate-300">
                      <tr>
                        <th className="px-4 py-3 font-medium">Ticker</th>
                        <th className="px-4 py-3 font-medium">Qty</th>
                        <th className="px-4 py-3 font-medium">Avg Buy</th>
                        <th className="px-4 py-3 font-medium">Current</th>
                        <th className="px-4 py-3 font-medium">Market Value</th>
                        <th className="px-4 py-3 font-medium">P&amp;L</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-white/6 bg-slate-950/25 text-slate-100">
                      {portfolio?.holdings?.length ? (
                        portfolio.holdings.map((holding) => (
                          <tr key={holding.symbol} className="transition hover:bg-white/[0.03]">
                            <td className="px-4 py-3">
                              <div className="flex items-center gap-2">
                                <span className="inline-flex h-2.5 w-2.5 rounded-full bg-cyan-300"></span>
                                <span className="font-medium">{holding.symbol}</span>
                              </div>
                            </td>
                            <td className="mono px-4 py-3">{holding.quantity}</td>
                            <td className="mono px-4 py-3">{formatCurrency(holding.averageBuyPrice)}</td>
                            <td className="mono px-4 py-3">{formatCurrency(holding.currentPrice)}</td>
                            <td className="mono px-4 py-3">{formatCurrency(holding.marketValue)}</td>
                            <td
                              className={`mono px-4 py-3 ${
                                Number(holding.unrealizedProfitLoss) >= 0 ? "text-emerald-300" : "text-rose-300"
                              }`}
                            >
                              {formatSignedCurrency(holding.unrealizedProfitLoss)}
                            </td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td className="px-4 py-8 text-center text-slate-400" colSpan="6">
                            Buy a ticker to start the streaming showpiece.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="glass-panel rounded-[32px] p-6">
                <p className="mono text-xs uppercase tracking-[0.28em] text-slate-400">Trade Tape</p>
                <h2 className="mt-2 text-2xl font-semibold">Latest Activity</h2>
                <div className="mt-6 space-y-3">
                  {trades.length ? (
                    trades.slice(0, 8).map((trade) => (
                      <div key={trade.id} className="rounded-2xl border border-white/8 bg-slate-950/30 px-4 py-4">
                        <div className="flex items-center justify-between gap-3">
                          <div>
                            <p className="font-medium text-white">
                              {trade.tradeType} {trade.symbol}
                            </p>
                            <p className="mt-1 text-xs text-slate-400">{formatTime(trade.executedAt)}</p>
                          </div>
                          <span
                            className={`rounded-full px-3 py-1 text-xs font-medium ${
                              trade.tradeType === "BUY"
                                ? "bg-emerald-400/15 text-emerald-200"
                                : "bg-rose-400/15 text-rose-200"
                            }`}
                          >
                            {trade.quantity} shares
                          </span>
                        </div>
                        <div className="mt-3 flex justify-between text-sm text-slate-300">
                          <span>@ {formatCurrency(trade.executedPrice)}</span>
                          <span className="mono">{formatCurrency(trade.totalAmount)}</span>
                        </div>
                      </div>
                    ))
                  ) : (
                    <div className="rounded-2xl border border-dashed border-white/12 px-4 py-8 text-center text-sm text-slate-400">
                      Trades will appear here after your first buy or sell.
                    </div>
                  )}
                </div>
              </div>
            </section>

            <section className="grid gap-6 xl:grid-cols-[1fr,1fr]">
              <div className="glass-panel rounded-[32px] p-6">
                <p className="mono text-xs uppercase tracking-[0.28em] text-slate-400">Leaderboard</p>
                <h2 className="mt-2 text-2xl font-semibold">Top Performers</h2>
                <div className="mt-6 space-y-3">
                  {leaderboard.length ? (
                    leaderboard.slice(0, 6).map((entry) => (
                      <div key={entry.rank + entry.email} className="flex items-center justify-between rounded-2xl border border-white/8 bg-slate-950/30 px-4 py-4">
                        <div>
                          <p className="font-medium text-white">
                            #{entry.rank} {entry.email}
                          </p>
                          <p className="mt-1 text-xs text-slate-400">{formatCurrency(entry.totalPortfolioValue)} total value</p>
                        </div>
                        <span className={`mono text-sm ${Number(entry.percentGain) >= 0 ? "text-emerald-300" : "text-rose-300"}`}>
                          {formatSignedPercent(entry.percentGain)}
                        </span>
                      </div>
                    ))
                  ) : (
                    <div className="rounded-2xl border border-dashed border-white/12 px-4 py-8 text-center text-sm text-slate-400">
                      Leaderboard will populate as portfolios become active.
                    </div>
                  )}
                </div>
              </div>

              <div className="glass-panel rounded-[32px] p-6">
                <p className="mono text-xs uppercase tracking-[0.28em] text-slate-400">Alert Queue</p>
                <h2 className="mt-2 text-2xl font-semibold">My Alerts</h2>
                <div className="mt-6 space-y-3">
                  {alerts.length ? (
                    alerts.slice(0, 8).map((alert) => (
                      <div key={alert.id} className="rounded-2xl border border-white/8 bg-slate-950/30 px-4 py-4">
                        <div className="flex items-center justify-between gap-3">
                          <div>
                            <p className="font-medium text-white">
                              {alert.symbol} {alert.direction} {formatCurrency(alert.targetPrice)}
                            </p>
                            <p className="mt-1 text-xs text-slate-400">
                              Created {formatTime(alert.createdAt)}
                            </p>
                          </div>
                          <span
                            className={`rounded-full px-3 py-1 text-xs font-medium ${
                              alert.triggered
                                ? "bg-emerald-400/15 text-emerald-200"
                                : "bg-amber-300/15 text-amber-100"
                            }`}
                          >
                            {alert.triggered ? "Triggered" : "Watching"}
                          </span>
                        </div>
                      </div>
                    ))
                  ) : (
                    <div className="rounded-2xl border border-dashed border-white/12 px-4 py-8 text-center text-sm text-slate-400">
                      Create a price alert to see it tracked here.
                    </div>
                  )}
                </div>
              </div>
            </section>
          </main>
        </div>
      </div>
    </div>
  );
}

function Field({ label, onChange, ...props }) {
  return (
    <label className="block">
      <span className="mb-2 block text-sm text-slate-300">{label}</span>
      <input
        className="w-full rounded-2xl border border-white/10 bg-slate-950/40 px-4 py-3 text-white outline-none transition placeholder:text-slate-500 focus:border-cyan-300/60"
        onChange={(event) => onChange(event.target.value)}
        {...props}
      />
    </label>
  );
}

function MetricCard({ label, value, accent = "text-white" }) {
  return (
    <div className="metric-chip rounded-[24px] px-4 py-4">
      <p className="mono text-[11px] uppercase tracking-[0.24em] text-slate-400">{label}</p>
      <p className={`mt-2 text-lg font-semibold ${accent}`}>{value}</p>
    </div>
  );
}

function MiniMetric({ label, value }) {
  return (
    <div className="rounded-2xl bg-white/5 px-3 py-2">
      <p className="mono text-[10px] uppercase tracking-[0.24em] text-slate-400">{label}</p>
      <p className="mt-1 text-sm font-medium text-white">{value}</p>
    </div>
  );
}

function StatusPanel({ notice, error }) {
  return (
    <section className="glass-panel rounded-[28px] p-6">
      <p className="mono text-xs uppercase tracking-[0.28em] text-slate-400">Signal Board</p>
      <div className="mt-4 space-y-3">
        <div className="rounded-2xl border border-cyan-300/15 bg-cyan-300/8 p-4 text-sm text-cyan-100">
          <p className="font-medium">Latest notice</p>
          <p className="mt-2 leading-6">{notice}</p>
        </div>
        <div className="rounded-2xl border border-rose-300/15 bg-rose-300/8 p-4 text-sm text-rose-100">
          <p className="font-medium">Last error</p>
          <p className="mt-2 min-h-12 leading-6">{error || "No active errors."}</p>
        </div>
      </div>
    </section>
  );
}

function extractErrorMessage(error) {
  return (
    error?.response?.data?.message ||
    error?.response?.data?.error ||
    error?.message ||
    "Something went wrong while talking to the backend."
  );
}

function formatCurrency(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "--";
  }

  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  }).format(Number(value));
}

function compactCurrency(value) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(Number(value));
}

function formatSignedCurrency(value) {
  const amount = Number(value);
  const prefix = amount >= 0 ? "+" : "-";
  return `${prefix}${formatCurrency(Math.abs(amount))}`;
}

function formatSignedPercent(value) {
  const amount = Number(value);
  const prefix = amount >= 0 ? "+" : "-";
  return `${prefix}${Math.abs(amount).toFixed(2)}%`;
}

function formatTime(value) {
  if (!value) {
    return "--";
  }

  return new Date(value).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function roundPrice(value) {
  return Number(Number(value).toFixed(4));
}

function roundMoney(value) {
  return Number(Number(value).toFixed(2));
}

export default App;
