#!/bin/bash

# ─────────────────────────────────────────────────────────────
#  Matir Bank — Local Dev Starter
#  Starts: Space 1 (keyservice :8081) + Space 2 (backend :8080)
#           + Frontend static server (:3000)
#  Press Ctrl+C to stop all services cleanly.
# ─────────────────────────────────────────────────────────────

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

JAVA_HOME="$ROOT_DIR/.tools/jdk-17.0.19+10"
MVN="$ROOT_DIR/.tools/apache-maven-3.9.6/bin/mvn"
M2_REPO="$ROOT_DIR/.m2/repository"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

# ── Colors ──────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

# ── PID tracking ────────────────────────────────────────────
PIDS=()

cleanup() {
    echo ""
    echo -e "${YELLOW}${BOLD}⏹  Stopping all Matir Bank services...${RESET}"
    for pid in "${PIDS[@]}"; do
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null
        fi
    done
    # Give processes a moment, then force-kill if still alive
    sleep 1
    for pid in "${PIDS[@]}"; do
        if kill -0 "$pid" 2>/dev/null; then
            kill -9 "$pid" 2>/dev/null
        fi
    done
    echo -e "${RED}${BOLD}✗  All services stopped.${RESET}"
    exit 0
}

trap cleanup SIGINT SIGTERM

# ── Log files ───────────────────────────────────────────────
LOG_DIR="$ROOT_DIR/logs"
mkdir -p "$LOG_DIR"
LOG_KEY="$LOG_DIR/keyservice.log"
LOG_BACK="$LOG_DIR/backend.log"
LOG_FRONT="$LOG_DIR/frontend.log"

# ── Banner ──────────────────────────────────────────────────
echo ""
echo -e "${CYAN}${BOLD}┌─────────────────────────────────────────────┐${RESET}"
echo -e "${CYAN}${BOLD}│          🏦  Matir Bank — Dev Start          │${RESET}"
echo -e "${CYAN}${BOLD}└─────────────────────────────────────────────┘${RESET}"
echo ""
echo -e "  Java:     ${GREEN}$JAVA_HOME${RESET}"
echo -e "  Logs:     ${GREEN}$LOG_DIR/${RESET}"
echo ""

# ── Kill any leftover processes from previous runs ───────────
echo -e "${BOLD}[0/3]${RESET} ${YELLOW}Cleaning up old processes...${RESET}"

# Kill all matir-bank java processes and their parent bash start.sh processes
OLD_STARTSH=$(ps aux | grep "[s]tart.sh" | awk '{print $2}' | grep -v "$$")
OLD_JAVA=$(ps aux | grep -E "\.tools/jdk.*java" | grep -v grep | awk '{print $2}')
OLD_PYTHON=$(ps aux | grep "http.server 3000" | grep -v grep | awk '{print $2}')

for pid in $OLD_STARTSH $OLD_JAVA $OLD_PYTHON; do
    [ "$pid" = "$$" ] && continue   # never kill ourselves
    kill -9 "$pid" 2>/dev/null
done

# Wait for OS to release ports
sleep 3
echo -e "  ${GREEN}✓ Ready${RESET}\n"

# ── Start Space 1: Key Service (port 8081) ──────────────────
echo -e "${BOLD}[1/3]${RESET} Starting ${CYAN}Space 1 — Key Service${RESET} on port ${YELLOW}8081${RESET}..."
"$MVN" -f "$ROOT_DIR/space1-keyservice/pom.xml" \
    -Dmaven.repo.local="$M2_REPO" \
    spring-boot:run \
    > "$LOG_KEY" 2>&1 &
PIDS+=($!)
KEY_PID=$!

# Wait until port 8081 is up (max 40s)
echo -n "  Waiting for keyservice"
for i in $(seq 1 40); do
    if curl -sf http://localhost:8081/api/keys/ping >/dev/null 2>&1 || \
       ss -tlnp 2>/dev/null | grep -q ':8081 '; then
        break
    fi
    sleep 1
    echo -n "."
done
echo ""
if kill -0 "$KEY_PID" 2>/dev/null; then
    echo -e "  ${GREEN}✓ Key Service running${RESET}  (log: logs/keyservice.log)"
else
    echo -e "  ${RED}✗ Key Service failed to start. Check logs/keyservice.log${RESET}"
    cat "$LOG_KEY" | tail -20
    cleanup
fi

echo ""

# ── Start Space 2: Backend (port 8080) ──────────────────────
echo -e "${BOLD}[2/3]${RESET} Starting ${CYAN}Space 2 — Backend${RESET} on port ${YELLOW}8080${RESET}..."
"$MVN" -f "$ROOT_DIR/space2-backend/pom.xml" \
    -Dmaven.repo.local="$M2_REPO" \
    spring-boot:run \
    > "$LOG_BACK" 2>&1 &
PIDS+=($!)
BACK_PID=$!

# Wait until port 8080 is up (max 50s)
echo -n "  Waiting for backend"
for i in $(seq 1 50); do
    if ss -tlnp 2>/dev/null | grep -q ':8080 '; then
        break
    fi
    sleep 1
    echo -n "."
done
echo ""
if kill -0 "$BACK_PID" 2>/dev/null; then
    echo -e "  ${GREEN}✓ Backend running${RESET}      (log: logs/backend.log)"
else
    echo -e "  ${RED}✗ Backend failed to start. Check logs/backend.log${RESET}"
    cat "$LOG_BACK" | tail -20
    cleanup
fi

echo ""

# ── Start Frontend (port 3000) ───────────────────────────────
echo -e "${BOLD}[3/3]${RESET} Starting ${CYAN}Frontend${RESET} on port ${YELLOW}3000${RESET}..."
python3 -m http.server 3000 \
    --directory "$ROOT_DIR/frontend" \
    > "$LOG_FRONT" 2>&1 &
PIDS+=($!)
FRONT_PID=$!
sleep 1
if kill -0 "$FRONT_PID" 2>/dev/null; then
    echo -e "  ${GREEN}✓ Frontend running${RESET}     (log: logs/frontend.log)"
else
    echo -e "  ${RED}✗ Frontend failed to start. Check logs/frontend.log${RESET}"
    cleanup
fi

# ── Ready ────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}${BOLD}┌─────────────────────────────────────────────┐${RESET}"
echo -e "${GREEN}${BOLD}│           ✅  All services running!          │${RESET}"
echo -e "${GREEN}${BOLD}├─────────────────────────────────────────────┤${RESET}"
echo -e "${GREEN}${BOLD}│  🌐 Frontend  →  http://localhost:3000       │${RESET}"
echo -e "${GREEN}${BOLD}│  ⚙️  Backend   →  http://localhost:8080       │${RESET}"
echo -e "${GREEN}${BOLD}│  🔑 KeySvc    →  http://localhost:8081       │${RESET}"
echo -e "${GREEN}${BOLD}├─────────────────────────────────────────────┤${RESET}"
echo -e "${GREEN}${BOLD}│  Press  Ctrl+C  to stop everything           │${RESET}"
echo -e "${GREEN}${BOLD}└─────────────────────────────────────────────┘${RESET}"
echo ""

# ── Keep script alive & tail logs ───────────────────────────
wait
