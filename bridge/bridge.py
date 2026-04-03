"""
F1 Live Timing Bridge

Connects to the official F1 live timing server via FastF1's SignalRClient
and re-publishes every message to local WebSocket clients.

Usage:
    python3 bridge.py [PORT]          # default port 9001

Acceptance criteria:
    - Auto-reconnect when F1 timing server closes connection (~2h sessions)
    - Supports multiple simultaneous Kotlin clients
    - Shuts down cleanly on SIGTERM / SIGINT
    - CarData.z and Position.z arrive already decompressed (FastF1 handles this)
"""

import asyncio
import json
import logging
import signal
import sys

import websockets
from fastf1.livetiming.client import SignalRClient

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

TOPICS = [
    "SessionInfo",
    "SessionStatus",
    "DriverList",
    "TimingData",
    "TimingAppData",
    "TimingStats",
    "TrackStatus",
    "RaceControlMessages",
    "WeatherData",
    "ExtrapolatedClock",
    "CarData.z",
    "Position.z",
    "LapCount",
    "Heartbeat",
]

RECONNECT_DELAY_S = 5      # seconds to wait before reconnecting to F1 server
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 9001

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [bridge] %(levelname)s %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("bridge")

# ---------------------------------------------------------------------------
# State
# ---------------------------------------------------------------------------

connected: set = set()
shutdown_event: asyncio.Event | None = None   # set in main()


# ---------------------------------------------------------------------------
# WebSocket server — local clients (Kotlin)
# ---------------------------------------------------------------------------

async def broadcast(msg: str) -> None:
    """Send *msg* to every connected client; remove dead connections."""
    if not connected:
        return
    dead: set = set()
    for ws in connected.copy():
        try:
            await ws.send(msg)
        except websockets.ConnectionClosed:
            dead.add(ws)
    connected.difference_update(dead)


async def ws_handler(websocket) -> None:
    """Handle a single local WebSocket client connection."""
    connected.add(websocket)
    logger.info("Client connected (%d total)", len(connected))
    try:
        await websocket.wait_closed()
    finally:
        connected.discard(websocket)
        logger.info("Client disconnected (%d remaining)", len(connected))


# ---------------------------------------------------------------------------
# F1 SignalR bridge — upstream connection
# ---------------------------------------------------------------------------

def on_message(topic: str, data, timestamp) -> None:
    """
    Callback invoked by SignalRClient (in a separate thread).

    Serialises the message and schedules a broadcast on the asyncio event loop.
    CarData.z and Position.z are already decompressed by FastF1 before this
    callback is called — no extra handling needed.
    """
    try:
        msg = json.dumps(
            {"topic": topic, "data": data, "timestamp": timestamp.isoformat()}
        )
    except (TypeError, ValueError) as exc:
        logger.warning("Could not serialise message for topic %s: %s", topic, exc)
        return

    loop = asyncio.get_event_loop()
    loop.call_soon_threadsafe(asyncio.ensure_future, broadcast(msg))


async def run_f1_client() -> None:
    """
    Connect to the F1 live timing server and keep reconnecting after
    disconnections (the server drops connections after ~2 h).
    """
    while not shutdown_event.is_set():
        client = SignalRClient(topics=TOPICS)
        client.on_message = on_message
        try:
            logger.info("Connecting to F1 live timing server…")
            await client.connect()
            # connect() returns when the server closes the connection
            logger.info("F1 timing server closed connection — will reconnect in %ds", RECONNECT_DELAY_S)
        except Exception as exc:
            logger.warning("F1 connection error: %s — retrying in %ds", exc, RECONNECT_DELAY_S)

        if shutdown_event.is_set():
            break

        try:
            await asyncio.wait_for(shutdown_event.wait(), timeout=RECONNECT_DELAY_S)
        except asyncio.TimeoutError:
            pass   # normal: timeout expired, loop again


# ---------------------------------------------------------------------------
# Signal handling
# ---------------------------------------------------------------------------

def handle_shutdown(sig, frame=None) -> None:
    """Handle SIGTERM / SIGINT: signal the main loop to exit."""
    logger.info("Received signal %s — shutting down…", signal.Signals(sig).name)
    if shutdown_event is not None:
        loop = asyncio.get_event_loop()
        loop.call_soon_threadsafe(shutdown_event.set)


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

async def main() -> None:
    global shutdown_event
    shutdown_event = asyncio.Event()

    # Register OS signal handlers
    signal.signal(signal.SIGTERM, handle_shutdown)
    signal.signal(signal.SIGINT, handle_shutdown)

    # Start the local WebSocket server for Kotlin clients
    async with websockets.serve(ws_handler, "localhost", PORT):
        logger.info("F1 Bridge listening on ws://localhost:%d", PORT)

        # Run the F1 upstream client concurrently
        f1_task = asyncio.create_task(run_f1_client())

        # Block until a shutdown signal is received
        await shutdown_event.wait()

        logger.info("Shutdown initiated — closing…")
        f1_task.cancel()
        try:
            await f1_task
        except asyncio.CancelledError:
            pass

    logger.info("Bridge stopped.")


if __name__ == "__main__":
    asyncio.run(main())
