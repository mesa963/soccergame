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
        const gameType = document.getElementById('gameModeSelection').value;
        let url = `/api/rooms/create?playerName=${encodeURIComponent(playerName)}&gameType=${gameType}`;

        if (gameType === 'IMPOSTOR') {
            const count = document.getElementById('impostorCount').value;
            const hints = document.getElementById('impostorHints').checked;
            const category = document.getElementById('impostorCategory').value;
            url += `&impostorCount=${count}&hints=${hints}&impostorCategory=${encodeURIComponent(category)}`;
        } else {
            const packType = document.getElementById('packType').value || "FUTBOL";
            url += `&packType=${packType}`;
        }

        const res = await fetch(url, { method: 'POST' });
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
        // Obtener datos completos de la sala para conocer el modo (IMPOSTOR) y categoría
        try {
            const roomRes = await fetch(`/api/rooms/${roomCode}`);
            if (roomRes.ok) {
                currentRoom = await roomRes.json();
            } else {
                currentRoom = { roomCode: roomCode };
            }
        } catch (e) {
            currentRoom = { roomCode: roomCode };
        }

        setupLobby(currentRoom);
        connectWebSocket(roomCode);
        // Asegurar lista de jugadores actualizada
        await refreshLobby();
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

async function accusePlayer(targetPlayerId) {
    if (!confirm("¿Estás seguro de ACUSAR a este jugador? Tu voto será definitivo.")) return;
    try {
        const res = await fetch(`/api/rooms/players/${currentPlayer.id}/accuse?targetId=${targetPlayerId}`, { method: 'POST' });
        if (res.ok) {
            showNotification("Has votado. Esperando a los demás...");
            // Disable all accuse buttons locally
            document.querySelectorAll('.btn-suggest-change').forEach(b => b.disabled = true);
        } else {
            showNotification("Error al votar");
        }
    } catch (err) {
        console.error("Error accusing:", err);
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
async function addNewImpostorWord() {
    const wordInput = document.getElementById('newImpostorWord');
    const hintInput = document.getElementById('newImpostorHint');
    const catInput = document.getElementById('currentImpostorCategoryValue');

    const word = wordInput.value.trim();
    const hint = hintInput.value.trim();
    // Si la categoría estaba vacía o era random, usamos lo que haya o "General"
    const category = catInput.value.trim() || "General";

    if (!word || !hint) {
        alert("Falta palabra o pista");
        return;
    }

    try {
        const res = await fetch(`/api/rooms/impostor-words?category=${encodeURIComponent(category)}&word=${encodeURIComponent(word)}&hint=${encodeURIComponent(hint)}`, { method: 'POST' });
        if (res.ok) {
            showNotification("Palabra añadida a " + category + " ✅");
            wordInput.value = "";
            hintInput.value = "";
        } else {
            alert("Error añadiendo palabra");
        }
    } catch (err) {
        console.error("Error adding impostor word:", err);
    }
}
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
    } else if (action.startsWith('ACCUSE_PROGRESS:')) {
        const parts = action.split(':');
        const count = parts[1];
        const total = parts[2];
        showNotification(`Votos de acusación: ${count}/${total} 🗳️`);
    } else if (action.startsWith('ACCUSE_RESULT:')) {
        const parts = action.split(':');
        const type = parts[1];
        const msg = parts[2];

        if (type === 'IMPOSTOR_CAUGHT') {
            showGameOver("VICTORIA INOCENTE", msg, "innocent");
        } else if (type === 'INNOCENT_EJECTED') {
            // Check if it's me
            const victimName = msg.split(' ')[0]; // Simple heuristic or use ID if available in future
            if (currentPlayer.name === victimName) { // Assuming unique names for now
                showEjection();
            } else {
                showNotification(msg);
            }

            // Re-enable buttons if game continues and I'm active
            if (!currentPlayer.eliminated) {
                document.querySelectorAll('.btn-suggest-change').forEach(b => b.disabled = false);
            }
        } else {
            alert(msg); // Tie
        }
    } else if (action.startsWith('GAME_OVER:')) {
        const parts = action.split(':');
        const type = parts[1];
        const msg = parts[2];
        if (type === 'IMPOSTOR_WINS') {
            showGameOver("VICTORIA IMPOSTOR", msg, "impostor");
        }
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

    // Shared Category Addition Visibility
    const addCatSection = document.getElementById('addCategorySection');
    const addImpSection = document.getElementById('addImpostorWordSection');

    if (currentRoom.gameType === 'IMPOSTOR') {
        addCatSection.classList.add('hidden');
        addImpSection.classList.remove('hidden');

        // Update Label
        const catPreference = currentRoom.impostorCategoryPreference;
        const displaySpan = document.getElementById('currentImpostorCategoryDisplay');
        const valueInput = document.getElementById('currentImpostorCategoryValue');

        if (catPreference && catPreference !== 'RANDOM') {
            displaySpan.innerText = catPreference;
            valueInput.value = catPreference;
            // Bloqueamos edición si quisiéramos, pero por ahora asumimos que solo se añade a esta.
        } else {
            displaySpan.innerText = "Cualquiera (Aleatorio)";
            // Si es RANDOM, quizás queramos dejarles elegir o poner una default.
            // Para simplificar, usaremos "General" o dejaremos que el usuario lo vea.
            // O mejor aún, convertimos el span en input si es random? 
            // El usuario pidió "ver qué categoría se escogió". Si fue random, se escogió random.
            // Pero para añadir palabra necesitamos una categoría concreta.
            // Asignemos "Varios" por defecto si es random.
            valueInput.value = "Varios";
        }

    } else {
        addCatSection.classList.remove('hidden');
        addImpSection.classList.add('hidden');
    }

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
    document.getElementById('gameOverOverlay').classList.add('hidden');
    document.getElementById('ejectionOverlay').classList.add('hidden');
    document.getElementById('myStatus').innerText = "Adivinando...";
    document.getElementById('myStatus').classList.remove('guessed-badge');

    setTimeout(async () => {
        const res = await fetch(`/api/rooms/${currentRoom.roomCode}/players`);
        if (res.ok) {
            const players = await res.json();
            allPlayers = players;

            // Persistent Visual Order
            turnOrder = [...allPlayers].sort((a, b) => {
                // Should exist if game started, but fallback to ID just in case
                const va = a.visualOrder !== undefined && a.visualOrder !== null ? a.visualOrder : a.id;
                const vb = b.visualOrder !== undefined && b.visualOrder !== null ? b.visualOrder : b.id;
                return va - vb;
            });

            const updatedMe = players.find(p => p.id === currentPlayer.id);
            if (updatedMe) {
                currentPlayer = updatedMe;
                if (currentPlayer.host) {
                    document.getElementById('btnResetGame').classList.remove('hidden');
                }
            }

            document.getElementById('gameRoomCode').innerText = currentRoom.roomCode;

            // Render Secret Card based on Game Type
            const secretCard = document.querySelector('.secret-card');

            // Refresh detailed room info to get Type and Words
            const roomRes = await fetch(`/api/rooms/${currentRoom.roomCode}`);
            if (roomRes.ok) {
                currentRoom = await roomRes.json(); // refresh full room object
            }

            if (currentRoom.gameType === 'IMPOSTOR') {
                // Impostor UI
                const amIImpostor = currentPlayer.impostor;
                document.querySelector('.notes-area').style.display = 'none'; // Hide notes
                document.getElementById('btnGuess').classList.add('hidden'); // Hide Guess button

                // Hide "What is your category?" title
                const titleEl = document.querySelector('.my-section h3');
                if (titleEl) titleEl.style.display = 'none';

                if (currentPlayer.eliminated) {
                    secretCard.innerHTML = `
                        <div style="background: rgba(107, 114, 128, 0.2); border: 2px solid #6b7280; border-radius: 12px; padding: 20px;">
                            <span style="font-size:3rem;">💀</span>
                            <h2 style="color: #9ca3af; margin: 10px 0;">ELIMINADO</h2>
                            <p style="color: #d1d5db;">Has sido expulsado de la nave.</p>
                        </div>
                    `;
                    document.getElementById('myStatus').innerText = "ELIMINADO";
                    document.getElementById('myStatus').className = "player-badge eliminated-badge";
                } else if (amIImpostor) {
                    secretCard.innerHTML = `
                        <div style="background: rgba(239, 68, 68, 0.2); border: 2px solid #ef4444; border-radius: 12px; padding: 20px;">
                            <span style="font-size:3rem;">🤫</span>
                            <h2 style="color: #ef4444; margin: 10px 0;">¡ERES EL IMPOSTOR!</h2>
                            <p style="color: #fca5a5;">Tu objetivo es engañar a los demás y descubrir la palabra secreta.</p>
                            ${currentPlayer.pendingGuess || currentPlayer.pendingCategory ? `<div style="margin-top:15px; background:rgba(251, 191, 36, 0.1); padding:10px; border-radius:8px; border:1px solid #fbbf24;">
                                ${currentPlayer.pendingGuess ? `<p style="color:#fbbf24; font-weight:bold; font-size:0.9rem;">PISTA SECRETA</p><p style="color:white; font-style:italic;">"${currentPlayer.pendingGuess}"</p>` : ''}
                                ${currentPlayer.pendingCategory ? `<p style="color:#fbbf24; font-weight:bold; font-size:0.9rem; margin-top:8px;">CATEGORÍA</p><p style="color:white; font-style:italic;">${currentPlayer.pendingCategory}</p>` : ''}
                            </div>` : ''}
                        </div>
                     `;
                } else {
                    // Regular Player
                    secretCard.innerHTML = `
                         <div style="background: rgba(16, 185, 129, 0.2); border: 2px solid #10b981; border-radius: 12px; padding: 20px;">
                            <span style="font-size:3rem;">🛡️</span>
                            <h2 style="color: #10b981; margin: 10px 0;">NO ERES EL IMPOSTOR</h2>
                            <p style="color: #a7f3d0;">Palabra Secreta:</p>
                            <h1 style="color: white; font-size: 2.5rem; text-transform: uppercase; margin: 10px 0; text-shadow: 0 0 10px rgba(16, 185, 129, 0.5);">${currentRoom.currentWord}</h1>
                            <p style="font-size:0.9rem; color:rgba(255,255,255,0.7);">Categoría: ${currentRoom.currentCategory}</p>
                         </div>
                     `;
                }
                document.getElementById('myStatus').innerText = amIImpostor ? "IMPOSTOR" : "JUGADOR";
                document.getElementById('myStatus').className = "player-badge " + (amIImpostor ? "impostor-badge" : "civilian-badge");
            } else {
                // Classic Guess Who
                document.querySelector('.notes-area').style.display = 'block'; // Show notes
                document.getElementById('btnGuess').classList.remove('hidden'); // Show Guess button
                secretCard.innerHTML = `
                    <span class="lock-icon">🔒</span>
                    <p>Tú no sabes qué categoría eres.</p>
                    <p class="hint">Tus amigos te darán pistas.</p>
                `;
                document.getElementById('myStatus').innerText = "Adivinando...";
            }
            // document.getElementById('btnGuess').disabled = false; // Managed by class now

            // Load notes
            if (currentPlayer) {
                document.getElementById('myNotes').value = currentPlayer.notes || "";
                document.getElementById('myInvalidNotes').value = currentPlayer.invalidNotes || "";
            }

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
        .map(p => {
            const isEliminated = p.eliminated;
            const myStatus = currentPlayer.eliminated;

            let actionBtn = '';
            if (currentRoom.gameType === 'IMPOSTOR') {
                if (!isEliminated && !myStatus) {
                    actionBtn = `<button class="btn-suggest-change" onclick="accusePlayer(${p.id})" title="Acusar de Impostor" style="background:rgba(239,68,68,0.2); border:1px solid #ef4444;">🫵</button>`;
                }
            } else {
                actionBtn = `<button class="btn-suggest-change" onclick="suggestChange(${p.id})" title="Sugerir cambio de categoría">🔄</button>`;
            }

            return `
            <div id="player-${p.id}" class="other-player-card ${p.guessed ? 'guessed' : ''} ${isEliminated ? 'eliminated' : ''}" style="${isEliminated ? 'opacity:0.5; filter:grayscale(1);' : ''}">
                <div class="player-header">
                    <strong>${p.name} ${isEliminated ? '💀' : ''}</strong>
                    <div style="display: flex; gap: 5px; align-items: center;">
                        ${actionBtn}
                        ${p.guessed ? '✅' : ''}
                    </div>
                </div>
                <span class="char-name">${currentRoom.gameType === 'IMPOSTOR' ? '' : (p.assignedCharacter ? p.assignedCharacter.name : 'Asignando...')}</span>
            </div>
        `}).join('');
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

        // Show sequence numbers for Impostor Game
        if (currentRoom && currentRoom.gameType === 'IMPOSTOR') {
            const seqBadge = document.createElement('div');
            seqBadge.className = 'sequence-badge';
            seqBadge.innerText = i + 1;

            // First player emphasis
            if (i === 0) {
                seqBadge.style.background = '#fbbf24';
                seqBadge.style.color = '#000';
                seqBadge.style.border = '2px solid #fff';
                label.innerHTML += ' <span style="color:#fbbf24; font-size:0.8em; margin-left:5px;">(Empieza)</span>';
            }

            // Position badge near dot
            const badgeX = centerX + (radius - 15) * Math.cos(angle) - 8; // Inner circle
            const badgeY = centerY + (radius - 15) * Math.sin(angle) - 8;
            seqBadge.style.left = `${badgeX}px`;
            seqBadge.style.top = `${badgeY}px`;
            seqBadge.style.position = 'absolute';
            seqBadge.style.width = '16px';
            seqBadge.style.height = '16px';
            seqBadge.style.borderRadius = '50%';
            seqBadge.style.fontSize = '10px';
            seqBadge.style.fontWeight = 'bold';
            seqBadge.style.display = 'flex';
            seqBadge.style.justifyContent = 'center';
            seqBadge.style.alignItems = 'center';

            if (i !== 0) {
                seqBadge.style.background = 'rgba(255,255,255,0.2)';
                seqBadge.style.color = 'white';
            }

            container.appendChild(seqBadge);
        }

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
document.getElementById('btnAddImpostorWord').onclick = addNewImpostorWord;
document.getElementById('btnAddCategory').onclick = addNewCategory;
document.getElementById('btnVoteCorrect').onclick = () => submitVote(true);
document.getElementById('btnVoteIncorrect').onclick = () => submitVote(false);
document.getElementById('btnResetGame').onclick = resetGame;

async function submitVote(correct) {
    if (!pendingVoteFor) return;
    try {
        let res;
        if (votingType === 'guess') {
            res = await fetch(`/api/rooms/players/${pendingVoteFor}/validate?voterId=${currentPlayer.id}&correct=${correct}`, { method: 'POST' });
        } else if (votingType === 'change') {
            res = await fetch(`/api/rooms/players/${pendingVoteFor}/execute-change?voterId=${currentPlayer.id}&yes=${correct}`, { method: 'POST' });
        }

        if (res && !res.ok) {
            // If the server rejected the vote (e.g. error), force close UI
            console.error("Vote failed, closing UI");
            hideVotingUI();
            const msg = await res.text();
            showNotification("Error: " + msg);
            return;
        }

        // No ocultamos el UI inmediatamente aquí, esperamos el progreso o la resolución final
        document.getElementById('votingArea').querySelectorAll('button').forEach(b => b.disabled = true);
        showNotification("Voto enviado. Esperando a los demás...");
    } catch (err) {
        console.error("Error voting:", err);
        hideVotingUI();
        showNotification("Error de conexión");
    }
}

// Note updates
function sendNotes() {
    if (!currentPlayer) return;
    const valid = document.getElementById('myNotes').value;
    const invalid = document.getElementById('myInvalidNotes').value;

    fetch(`/api/rooms/players/${currentPlayer.id}/notes`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ valid: valid, invalid: invalid })
    }).catch(e => console.error(e));
}

const debouncedSendNotes = debounce(sendNotes, 1000);

document.getElementById('myNotes').addEventListener('input', debouncedSendNotes);
document.getElementById('myInvalidNotes').addEventListener('input', debouncedSendNotes);

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

function showEjection() {
    const overlay = document.getElementById('ejectionOverlay');
    overlay.classList.remove('hidden');
    // Auto hide after animation (optional, or keep until game ends?)
    // Among Us keeps you as ghost. Here we just show the dramatic screen then let you see the game.
    setTimeout(() => {
        overlay.classList.add('hidden');
        showNotification("Ahora eres un espectador fantasma... 👻");
        refreshGameState();
    }, 4000);
}

function showGameOver(title, message, type) {
    const overlay = document.getElementById('gameOverOverlay');
    const titleEl = document.getElementById('winnerTitle');

    titleEl.innerText = title;
    document.getElementById('winnerMessage').innerText = message;

    if (type === 'impostor') {
        titleEl.style.backgroundImage = 'linear-gradient(to right, #ef4444, #b91c1c)';
        titleEl.style.textShadow = '0 0 20px rgba(239, 68, 68, 0.5)';
    } else {
        titleEl.style.backgroundImage = 'linear-gradient(to right, #00f2ff, #7000ff)';
        titleEl.style.textShadow = '0 0 20px rgba(0, 242, 255, 0.5)';
    }

    overlay.classList.remove('hidden');
}

function closeGameOver() {
    document.getElementById('gameOverOverlay').classList.add('hidden');
    // Maybe reset game UI locally?
}

// Dynamic Packs
async function loadImpostorCategories() {
    try {
        const res = await fetch('/api/rooms/impostor-categories');
        if (res.ok) {
            const categories = await res.json();
            const select = document.getElementById('impostorCategory');
            if (select && categories.length > 0) {
                // Keep Random option
                select.innerHTML = '<option value="RANDOM">🎲 Aleatorio</option>' +
                    categories.map(c => `<option value="${c}">${c}</option>`).join('');
            }
        }
    } catch (e) { console.error("Could not load impostor categories", e); }
}

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

function selectGameMode(mode, element) {
    // Update Hidden Input
    document.getElementById('gameModeSelection').value = mode;

    // Visual Updates
    document.querySelectorAll('.game-mode-card').forEach(c => c.classList.remove('selected'));
    element.classList.add('selected');

    // Toggle Config Sections
    const guessWho = document.getElementById('guessWhoConfig');
    const impostor = document.getElementById('impostorConfig');

    if (mode === 'IMPOSTOR') {
        guessWho.classList.add('hidden');
        impostor.classList.remove('hidden');
    } else {
        guessWho.classList.remove('hidden');
        impostor.classList.add('hidden');
    }
}

function getPackEmoji(pack) {
    if (pack === 'FUTBOL' || pack === 'SOCCER') return '⚽';
    if (pack === 'MOVIES') return '🎬';
    return '📦';
}

// Init
document.addEventListener('DOMContentLoaded', () => {
    loadPacks();
    loadImpostorCategories();
});
