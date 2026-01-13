const VERSION = "5.0 - Reinicio y Sugerencia de Cambio";
console.log("Soccer Game Frontend Version:", VERSION);

let stompClient = null;
let currentRoom = null;
let currentPlayer = null;
let allPlayers = [];
let turnOrder = [];
let pendingVoteFor = null;
let votingType = null; // 'guess' or 'change'

// DOM Elements
const views = {
    landing: document.getElementById('landing'),
    lobby: document.getElementById('lobby'),
    game: document.getElementById('game')
};

function showView(viewName) {
    Object.values(views).forEach(v => v.classList.add('hidden'));
    views[viewName].classList.remove('hidden');
}

// REST API calls
async function createRoom(playerName) {
    try {
        const packType = document.getElementById('packType').value || "SOCCER";
        const res = await fetch(`/api/rooms/create?playerName=${encodeURIComponent(playerName)}&packType=${packType}`, { method: 'POST' });
        const room = await res.json();
        currentRoom = room;
        currentPlayer = room.players[0]; // The host
        setupLobby(room);
        connectWebSocket(room.roomCode);
        showView('lobby');
    } catch (err) {
        console.error("Error creating room:", err);
        alert("No se pudo crear la sala: " + err.message);
    }
}

async function joinRoom(roomCode, playerName) {
    try {
        const res = await fetch(`/api/rooms/join?roomCode=${roomCode}&playerName=${encodeURIComponent(playerName)}`, { method: 'POST' });
        if (!res.ok) throw new Error("No se pudo unir a la sala. Revisa el código.");
        const player = await res.json();
        currentPlayer = player;
        currentRoom = { roomCode: roomCode };
        setupLobby(currentRoom);
        connectWebSocket(roomCode);
        showView('lobby');
    } catch (err) {
        alert(err.message);
    }
}

async function startGame() {
    try {
        await fetch(`/api/rooms/${currentRoom.roomCode}/start`, { method: 'POST' });
    } catch (err) {
        console.error("Error starting game:", err);
    }
}

async function resetGame() {
    try {
        await fetch(`/api/rooms/${currentRoom.roomCode}/reset`, { method: 'POST' });
    } catch (err) {
        console.error("Error resetting game:", err);
    }
}

async function suggestChange(targetPlayerId) {
    try {
        await fetch(`/api/rooms/players/${targetPlayerId}/request-change?requesterId=${currentPlayer.id}`, { method: 'POST' });
    } catch (err) {
        console.error("Error suggesting change:", err);
    }
}

async function addNewCategory() {
    const nameInput = document.getElementById('newCategoryName');
    const name = nameInput.value.trim();
    if (!name) return;

    try {
        const res = await fetch(`/api/rooms/categories?name=${encodeURIComponent(name)}`, { method: 'POST' });
        if (res.ok) {
            showNotification("Categoría añadida ✅");
            nameInput.value = "";
        }
    } catch (err) {
        console.error("Error adding category:", err);
    }
}

// WebSocket Logic
let socket = null;

function connectWebSocket(roomCode) {
    // Si ya hay un cliente intentando conectar o conectado, verificar estado.
    if (stompClient && stompClient.connected) {
        console.log("Ya conectado, suscribiendo a nueva sala si es necesario...");
        // Re-suscribir si cambia la sala (lógica simplificada)
        stompClient.subscribe('/topic/room/' + roomCode, function (msg) {
            handleGameUpdate(msg.body);
        });
        return;
    }

    console.log("Iniciando conexión WebSocket...");
    socket = new SockJS('/ws-game');
    stompClient = Stomp.over(socket);
    // stompClient.debug = null; // Comentado para ver logs si falla

    stompClient.connect({}, function (frame) {
        console.log('WS Conectado: ' + frame);
        const statusEl = document.getElementById('connectionStatus');
        if (statusEl) {
            statusEl.innerText = "🟢 Conectado";
            statusEl.classList.remove('disconnected');
        }

        stompClient.subscribe('/topic/room/' + roomCode, function (msg) {
            handleGameUpdate(msg.body);
        });

        // Al reconectar, pedir el estado actual por si nos perdimos algo
        if (currentRoom) {
            if (currentRoom.status === 'WAITING') refreshLobby();
            else refreshGameState();
        }

    }, function (error) {
        console.error('WS Error/Desconexión:', error);
        const statusEl = document.getElementById('connectionStatus');
        if (statusEl) {
            statusEl.innerText = "🔴 Desconectado (Reintentando...)";
            statusEl.classList.add('disconnected');
        }

        // Reintentar en 3 segundos
        setTimeout(() => connectWebSocket(roomCode), 3000);
    });
}

function handleGameUpdate(action) {
    console.log("Game Update Received:", action);
    if (action === 'PLAYER_JOINED') {
        refreshLobby();
    } else if (action === 'GAME_STARTED') {
        initGame();
    } else if (action.startsWith('GUESS_SUBMITTED:')) {
        const parts = action.split(':');
        const name = parts[1];
        const guess = parts[2];
        const pId = parts[3];

        if (pId != currentPlayer.id) {
            showVotingUI(name, guess, pId, 'guess');
        } else {
            showNotification("Has enviado tu adivinanza. Esperando validación...");
        }
    } else if (action.startsWith('GUESS_VALIDATED_CORRECT:')) {
        const name = action.split(':')[1];
        showNotification(`¡${name} ha acertado! 🎉`);
        hideVotingUI();
        refreshGameState();
    } else if (action.startsWith('GUESS_VALIDATED_INCORRECT:')) {
        const name = action.split(':')[1];
        showNotification(`${name} falló. ❌`);
        hideVotingUI();
    } else if (action.startsWith('CHANGE_PROPOSED:')) {
        const parts = action.split(':');
        const targetName = parts[1];
        const targetId = parts[2];
        const requesterName = parts[3];

        // El afectado NO ve la votación (para evitar spoilers sobre la dificultad)
        if (targetId != currentPlayer.id) {
            showVotingUI(targetName, `Sugerido por ${requesterName}. ¿Cambiar categoría?`, targetId, 'change');
        } else {
            // El afectado no sabe nada
            console.log("Se ha propuesto un cambio para ti, pero no te lo diremos ;)");
        }
    } else if (action.startsWith('CHANGE_EXECUTED:')) {
        const name = action.split(':')[1];
        showNotification(`Se ha cambiado la categoría de ${name} 🔄`);
        hideVotingUI();
        refreshGameState();
    } else if (action.startsWith('VOTE_PROGRESS:')) {
        const parts = action.split(':');
        const count = parts[1];
        const total = parts[2];
        const label = document.getElementById('votingMsg').querySelector('em') || document.createElement('em');
        label.style.display = 'block';
        label.style.fontSize = '0.8rem';
        label.style.marginTop = '10px';
        label.innerText = `Progreso: ${count}/${total} votos recibidos`;
        if (!label.parentNode) document.getElementById('votingMsg').appendChild(label);
    } else if (action.startsWith('CHANGE_REJECTED:')) {
        const name = action.split(':')[1];
        showNotification(`El cambio para ${name} fue rechazado ❌`);
        hideVotingUI();
    }
}

async function refreshLobby() {
    if (!currentRoom) return;
    const res = await fetch(`/api/rooms/${currentRoom.roomCode}/players`);
    if (res.ok) {
        const players = await res.json();
        allPlayers = players;
        updatePlayerListUI(players);
    }
}

function updatePlayerListUI(players) {
    const list = document.getElementById('playerList');
    if (!list) return;
    list.innerHTML = players.map(p => `<li class="player-tag">${p.name} ${p.host ? '👑' : ''}</li>`).join('');

    // Shared Category Addition: Everyone can see it
    document.getElementById('addCategorySection').classList.remove('hidden');

    if (currentPlayer && currentPlayer.host) {
        if (players.length >= 2) {
            document.getElementById('btnStartGame').classList.remove('hidden');
            document.getElementById('waitingMsg').classList.add('hidden');
        }
    }
}

async function initGame() {
    console.log("Initializing Game UI...");
    // Explicitly hide podium and clear state before fetch
    document.getElementById('podiumArea').classList.add('hidden');
    document.getElementById('myStatus').innerText = "Adivinando...";
    document.getElementById('myStatus').classList.remove('guessed-badge');

    setTimeout(async () => {
        const res = await fetch(`/api/rooms/${currentRoom.roomCode}/players`);
        if (res.ok) {
            const players = await res.json();
            allPlayers = players;

            // Randomize turn order for visual circle
            turnOrder = [...allPlayers].sort(() => Math.random() - 0.5);

            const updatedMe = players.find(p => p.id === currentPlayer.id);
            if (updatedMe) {
                currentPlayer = updatedMe;
                if (currentPlayer.host) {
                    document.getElementById('btnResetGame').classList.remove('hidden');
                }
            }

            document.getElementById('gameRoomCode').innerText = currentRoom.roomCode;
            document.querySelector('.secret-card').innerHTML = `
                <span class="lock-icon">🔒</span>
                <p>Tú no sabes qué categoría eres.</p>
                <p class="hint">Tus amigos te darán pistas.</p>
            `;
            document.getElementById('btnGuess').disabled = false;

            renderOtherPlayers();
            renderTurnCircle();
            renderPodium();
            showView('game');
        }
    }, 500);
}

async function refreshGameState() {
    const res = await fetch(`/api/rooms/${currentRoom.roomCode}/players`);
    if (res.ok) {
        allPlayers = await res.json();
        renderOtherPlayers();
        renderTurnCircle();
        renderPodium();

        const me = allPlayers.find(p => p.id === currentPlayer.id);
        if (me && me.guessed) {
            const statusLabel = document.getElementById('myStatus');
            statusLabel.innerText = "¡ADIVINADO!";
            statusLabel.classList.add('guessed-badge');
            document.querySelector('.secret-card').innerHTML = `
                <span class="lock-icon">🏆</span>
                <p>¡Eras de la categoría: ${me.assignedCharacter ? me.assignedCharacter.name : '??'}!</p>
            `;
            document.getElementById('btnGuess').disabled = true;
        }
    }
}

function renderOtherPlayers() {
    const container = document.getElementById('otherPlayers');
    if (!container) return;
    container.innerHTML = allPlayers
        .filter(p => p.id !== currentPlayer.id)
        .map(p => `
            <div id="player-${p.id}" class="other-player-card ${p.guessed ? 'guessed' : ''}">
                <div class="player-header">
                    <strong>${p.name}</strong>
                    <div style="display: flex; gap: 5px; align-items: center;">
                        <button class="btn-suggest-change" onclick="suggestChange(${p.id})" title="Sugerir cambio de categoría">🔄</button>
                        ${p.guessed ? '✅' : ''}
                    </div>
                </div>
                <span class="char-name">${p.assignedCharacter ? p.assignedCharacter.name : 'Asignando...'}</span>
            </div>
        `).join('');
}

function renderTurnCircle() {
    const container = document.getElementById('turnCircleContainer');
    if (!container) return;

    // Clear old labels and dots
    container.querySelectorAll('.player-name-label, .turn-dot').forEach(el => el.remove());

    if (turnOrder.length === 0) turnOrder = [...allPlayers];

    const count = turnOrder.length;
    const radius = 45;
    const centerX = 60;
    const centerY = 60;

    turnOrder.forEach((p, i) => {
        // Sync player status from allPlayers list
        const latestP = allPlayers.find(ap => ap.id === p.id) || p;

        const dot = document.createElement('div');
        dot.className = 'turn-dot' + (latestP.guessed ? ' guessed' : '');

        const label = document.createElement('div');
        label.className = 'player-name-label' + (latestP.guessed ? ' guessed' : '');
        label.innerText = latestP.name;

        const angle = (2 * Math.PI / count) * i - (Math.PI / 2);

        // Dot position
        const dotX = centerX + radius * Math.cos(angle) - 3;
        const dotY = centerY + radius * Math.sin(angle) - 3;
        dot.style.left = `${dotX}px`;
        dot.style.top = `${dotY}px`;

        // Label position (slightly further out)
        const labelRadius = radius + 22;
        const labelX = centerX + labelRadius * Math.cos(angle) - 25;
        const labelY = centerY + labelRadius * Math.sin(angle) - 10;

        label.style.left = `${labelX}px`;
        label.style.top = `${labelY}px`;

        container.appendChild(dot);
        container.appendChild(label);
    });
}

function renderPodium() {
    const winners = allPlayers
        .filter(p => p.guessOrder)
        .sort((a, b) => a.guessOrder - b.guessOrder);

    const area = document.getElementById('podiumArea');
    const list = document.getElementById('podiumList');

    if (winners.length > 0) {
        area.classList.remove('hidden');
        list.innerHTML = winners.slice(0, 3).map((p, i) => {
            const rankClass = i === 0 ? 'gold' : i === 1 ? 'silver' : 'bronze';
            const rankLabel = i === 0 ? '1er Puesto' : i === 1 ? '2do Puesto' : '3er Puesto';
            return `
                <div class="podium-item ${rankClass}">
                    <span class="rank-badge">${rankLabel}</span>
                    <strong>${p.name}</strong>
                </div>
            `;
        }).join('');
    } else {
        area.classList.add('hidden');
    }
}

function showNotification(msg) {
    const div = document.createElement('div');
    div.className = 'notification-toast';
    div.innerText = msg;
    document.body.appendChild(div);
    setTimeout(() => div.classList.add('show'), 10);
    setTimeout(() => {
        div.classList.remove('show');
        setTimeout(() => div.remove(), 500);
    }, 4000);
}

function showVotingUI(name, subtitle, playerId, type) {
    pendingVoteFor = playerId;
    votingType = type;
    const area = document.getElementById('votingArea');
    document.getElementById('votingMsg').innerHTML = `<strong>${name}</strong><br>${subtitle}`;
    area.querySelectorAll('button').forEach(b => b.disabled = false);
    area.classList.remove('hidden');
}

function hideVotingUI() {
    pendingVoteFor = null;
    votingType = null;
    const area = document.getElementById('votingArea');
    area.classList.add('hidden');
    area.querySelectorAll('button').forEach(b => b.disabled = false);
    const progressLabel = area.querySelector('em');
    if (progressLabel) progressLabel.remove();
}

// Event Listeners
document.getElementById('btnAddCategory').onclick = addNewCategory;
document.getElementById('btnVoteCorrect').onclick = () => submitVote(true);
document.getElementById('btnVoteIncorrect').onclick = () => submitVote(false);
document.getElementById('btnResetGame').onclick = resetGame;

async function submitVote(correct) {
    if (!pendingVoteFor) return;
    try {
        if (votingType === 'guess') {
            await fetch(`/api/rooms/players/${pendingVoteFor}/validate?voterId=${currentPlayer.id}&correct=${correct}`, { method: 'POST' });
        } else if (votingType === 'change') {
            await fetch(`/api/rooms/players/${pendingVoteFor}/execute-change?voterId=${currentPlayer.id}&yes=${correct}`, { method: 'POST' });
        }
        // No ocultamos el UI inmediatamente aquí, esperamos el progreso o la resolución final
        document.getElementById('votingArea').querySelectorAll('button').forEach(b => b.disabled = true);
        showNotification("Voto enviado. Esperando a los demás...");
    } catch (err) {
        console.error("Error voting:", err);
    }
}

// Note updates
document.getElementById('myNotes').addEventListener('input', debounce(async (e) => {
    if (!currentPlayer) return;
    await fetch(`/api/rooms/players/${currentPlayer.id}/notes`, {
        method: 'PUT',
        headers: { 'Content-Type': 'text/plain' },
        body: e.target.value
    });
}, 1000));

document.getElementById('btnCreateRoom').onclick = () => {
    const name = document.getElementById('playerName').value;
    if (name) createRoom(name);
    else alert("Escribe tu nombre");
};

document.getElementById('btnJoinRoom').onclick = () => {
    const name = document.getElementById('playerName').value;
    const code = document.getElementById('joinCode').value;
    if (name && code) joinRoom(code.toUpperCase(), name);
    else alert("Escribe nombre y código");
};

document.getElementById('btnStartGame').onclick = startGame;

document.getElementById('btnGuess').onclick = () => {
    document.getElementById('guessModal').classList.remove('hidden');
};

document.getElementById('btnCancelGuess').onclick = () => {
    document.getElementById('guessModal').classList.add('hidden');
};

document.getElementById('btnSubmitGuess').onclick = async () => {
    const guessName = document.getElementById('guessName').value;
    if (!guessName) return;

    if (!currentPlayer || !currentPlayer.id) return;

    try {
        const url = `/api/rooms/players/${currentPlayer.id}/guess?guessName=${encodeURIComponent(guessName)}`;
        await fetch(url, { method: 'POST' });
    } catch (err) {
        console.error("Error al adivinar:", err);
    }

    document.getElementById('guessName').value = "";
    document.getElementById('guessModal').classList.add('hidden');
};

function setupLobby(room) {
    document.getElementById('displayRoomCode').innerText = room.roomCode;
    updatePlayerListUI(room.players || [currentPlayer]);
}

function debounce(func, wait) {
    let timeout;
    return function () {
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(this, arguments), wait);
    };
}

function copyRoomCode() {
    const code = document.getElementById('displayRoomCode').innerText;
    if (code && code !== '----') {
        navigator.clipboard.writeText(code).then(() => {
            showNotification("Código copiado! 📋");
        });
    }
}

// Dynamic Packs
async function loadPacks() {
    try {
        const res = await fetch('/api/admin/packs');
        if (res.ok) {
            const packs = await res.json();
            const select = document.getElementById('packType');
            if (select && packs.length > 0) {
                select.innerHTML = packs.map(p =>
                    `<option value="${p}">${getPackEmoji(p)} ${p}</option>`
                ).join('');
            }
        }
    } catch (e) { console.error("Could not load packs", e); }
}

function getPackEmoji(pack) {
    if (pack === 'SOCCER') return '⚽';
    if (pack === 'MOVIES') return '🎬';
    return '📦';
}

// Init
document.addEventListener('DOMContentLoaded', loadPacks);
