let stompClient = null;

document.addEventListener('DOMContentLoaded', () => {
    AUTH.requireAuth();

    const roomId = document.getElementById('chat-page').dataset.roomId;
    const chatLog = document.getElementById('chat-log');
    const messageForm = document.getElementById('message-form');
    const messageInput = document.getElementById('message-input');

    loadRoomTitle(roomId);
    loadHistory(roomId, chatLog);
    connect(roomId, chatLog);

    messageForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const content = messageInput.value.trim();
        if (!content || !stompClient || !stompClient.connected) {
            return;
        }
        stompClient.send('/app/rooms/' + roomId + '/send', {}, JSON.stringify({ content }));
        messageInput.value = '';
    });
});

async function loadRoomTitle(roomId) {
    const titleEl = document.getElementById('room-title');
    try {
        const res = await AUTH.apiFetch('/api/rooms');
        const rooms = await res.json();
        const room = rooms.find(r => String(r.id) === String(roomId));
        if (room) {
            titleEl.textContent = room.name || room.memberNicknames.join(', ');
        }
    } catch (err) {
        // keep default title
    }
}

async function loadHistory(roomId, chatLog) {
    try {
        const res = await AUTH.apiFetch('/api/rooms/' + roomId + '/messages?size=30');
        const page = await res.json();
        const messages = (page.content || []).slice().reverse();
        messages.forEach(msg => appendMessage(chatLog, msg));
        scrollToBottom(chatLog);
    } catch (err) {
        // history load failure is non-fatal; live messages will still arrive
    }
}

function connect(roomId, chatLog) {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect(
        { Authorization: 'Bearer ' + AUTH.getToken() },
        () => {
            stompClient.subscribe('/topic/rooms/' + roomId, (frame) => {
                const msg = JSON.parse(frame.body);
                appendMessage(chatLog, msg);
                scrollToBottom(chatLog);
            });
            stompClient.send('/app/rooms/' + roomId + '/enter', {}, JSON.stringify({}));
        },
        () => {
            setTimeout(() => connect(roomId, chatLog), 3000);
        }
    );
}

function appendMessage(chatLog, msg) {
    const row = document.createElement('div');

    if (msg.type === 'TEXT') {
        const mine = msg.senderUsername === AUTH.getUsername();
        row.className = 'msg-row ' + (mine ? 'mine' : 'theirs');
        row.innerHTML = `
            ${mine ? '' : `<div class="msg-sender">${escapeHtml(msg.senderNickname)}</div>`}
            <div class="msg-bubble">${escapeHtml(msg.content)}</div>
            <div class="msg-time">${formatTime(msg.sentAt)}</div>
        `;
    } else {
        row.className = 'msg-row system';
        row.innerHTML = `<div class="msg-bubble">${escapeHtml(msg.content)}</div>`;
    }

    chatLog.appendChild(row);
}

function formatTime(sentAt) {
    if (!sentAt) return '';
    const d = new Date(sentAt);
    if (isNaN(d.getTime())) return '';
    return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0');
}

function scrollToBottom(chatLog) {
    chatLog.scrollTop = chatLog.scrollHeight;
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str == null ? '' : str;
    return div.innerHTML;
}
