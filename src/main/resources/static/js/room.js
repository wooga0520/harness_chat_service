let stompClient = null;
let roomId = null;
let currentUsername = null;

const members = new Map(); // userId -> { userId, username, nickname, role, online, lastReadAt }
const renderedMessages = new Map(); // messageId -> { senderId, sentAt }
let inviteSearchTimer = null;

document.addEventListener('DOMContentLoaded', () => {
    AUTH.requireAuth();
    currentUsername = AUTH.getUsername();

    roomId = document.getElementById('chat-page').dataset.roomId;
    const chatLog = document.getElementById('chat-log');
    const messageForm = document.getElementById('message-form');
    const messageInput = document.getElementById('message-input');

    loadHistory(roomId, chatLog);
    loadMembers(roomId);
    setupMemberPanel(roomId);
    initRoomStatus(roomId, chatLog);

    messageForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const content = messageInput.value.trim();
        if (!content || !stompClient || !stompClient.connected) {
            return;
        }
        stompClient.send('/app/rooms/' + roomId + '/send', {}, JSON.stringify({ content }));
        messageInput.value = '';
    });

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') {
            sendRead();
        }
    });
    window.addEventListener('focus', sendRead);
});

/**
 * Fetches the room list to find this room's title and the current user's own
 * pendingForMe/active flags (see RoomService.toRoomResponses), then decides whether to show
 * the chat as usual, show an accept/decline banner (I was invited and haven't responded), or
 * show a waiting banner (I'm accepted but the other DM participant hasn't accepted yet). Only
 * connects the STOMP session in the first case -- a pending/waiting room has nothing to send
 * or receive yet, and the server would reject enter/send attempts anyway.
 */
async function initRoomStatus(roomId, chatLog) {
    const titleEl = document.getElementById('room-title');
    let room = null;
    try {
        const res = await AUTH.apiFetch('/api/rooms');
        const rooms = await res.json();
        room = rooms.find(r => String(r.id) === String(roomId));
        if (room) {
            titleEl.textContent = room.name || room.memberNicknames.join(', ');
        }
    } catch (err) {
        // keep default title; fall through and connect as usual
    }

    if (room && room.pendingForMe) {
        showChatBanner('이 대화방에 초대되었습니다. 수락해야 메시지를 주고받을 수 있습니다.', [
            { label: '수락', className: 'accept-btn', onClick: () => respondToRoomInvite(roomId, 'accept') },
            { label: '거절', className: 'decline-btn', onClick: () => respondToRoomInvite(roomId, 'decline') }
        ]);
        setChatInputVisible(false);
        return;
    }

    if (room && !room.active) {
        showChatBanner('상대방이 아직 초대를 수락하지 않았습니다.', []);
        setChatInputVisible(false);
        return;
    }

    connect(roomId, chatLog);
}

function showChatBanner(text, actions) {
    const banner = document.getElementById('chat-banner');
    const textEl = document.getElementById('chat-banner-text');
    const actionsEl = document.getElementById('chat-banner-actions');
    if (!banner || !textEl || !actionsEl) return;

    textEl.textContent = text;
    actionsEl.innerHTML = '';
    actions.forEach(action => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = action.className;
        btn.textContent = action.label;
        btn.addEventListener('click', action.onClick);
        actionsEl.appendChild(btn);
    });
    banner.classList.remove('hidden');
}

function setChatInputVisible(visible) {
    const bar = document.getElementById('message-form');
    if (bar) {
        bar.classList.toggle('hidden', !visible);
    }
}

async function respondToRoomInvite(roomId, action) {
    try {
        await AUTH.apiFetch('/api/rooms/' + roomId + '/' + action, { method: 'POST' });
    } catch (err) {
        return;
    }
    window.location.href = '/rooms';
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

async function loadMembers(roomId) {
    try {
        const res = await AUTH.apiFetch('/api/rooms/' + roomId + '/members');
        const list = await res.json();
        members.clear();
        list.forEach(m => members.set(m.userId, m));
        renderMemberList();
        refreshAllReadBadges();
    } catch (err) {
        // member panel just stays empty; core chat still works
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
                upsertMessage(chatLog, msg);
            });
            stompClient.subscribe('/topic/rooms/' + roomId + '/presence', (frame) => {
                const event = JSON.parse(frame.body);
                handlePresenceEvent(event);
            });
            stompClient.subscribe('/topic/rooms/' + roomId + '/read', (frame) => {
                const event = JSON.parse(frame.body);
                handleReadEvent(event);
            });
            stompClient.send('/app/rooms/' + roomId + '/enter', {}, JSON.stringify({}));

            // The initial loadMembers() call (fired at page load, before this WebSocket
            // handshake finishes) can't reflect our own just-connected online status yet,
            // and may also miss other users' presence transitions that raced with these
            // subscribe() calls above -- refresh once now that the topics are live.
            loadMembers(roomId);
        },
        () => {
            setTimeout(() => connect(roomId, chatLog), 3000);
        }
    );
}

function sendRead() {
    if (stompClient && stompClient.connected && roomId) {
        stompClient.send('/app/rooms/' + roomId + '/read', {}, JSON.stringify({}));
    }
}

function upsertMessage(chatLog, msg) {
    const existingRow = msg.id != null ? chatLog.querySelector(`[data-message-id="${msg.id}"]`) : null;
    if (existingRow) {
        renderMessageInto(existingRow, msg);
    } else {
        const row = document.createElement('div');
        renderMessageInto(row, msg);
        chatLog.appendChild(row);
        scrollToBottom(chatLog);
    }

    // Must run after the row is attached to the document -- the read badge is
    // looked up by id via getElementById, which can't find a detached node.
    if (msg.id != null) {
        updateReadBadge(msg.id);
    }
}

function appendMessage(chatLog, msg) {
    upsertMessage(chatLog, msg);
}

function renderMessageInto(row, msg) {
    if (msg.id != null) {
        row.dataset.messageId = msg.id;
        renderedMessages.set(msg.id, { senderId: msg.senderId, sentAt: msg.sentAt });
    }

    if (msg.type === 'TEXT') {
        const mine = msg.senderUsername === currentUsername;
        row.className = 'msg-row ' + (mine ? 'mine' : 'theirs');

        const bodyText = msg.deleted ? '삭제된 메시지입니다.' : escapeHtml(msg.content);
        const bubbleClass = 'msg-bubble' + (msg.deleted ? ' deleted' : '');
        const editedTag = (!msg.deleted && msg.editedAt) ? '<span class="msg-edited-tag">(수정됨)</span>' : '';
        const actions = (mine && !msg.deleted)
            ? `
                <div class="msg-actions">
                    <button type="button" class="msg-actions-toggle" onclick="toggleMessageMenu(${msg.id})">&#8942;</button>
                    <div class="msg-actions-menu hidden" id="msg-menu-${msg.id}">
                        <button type="button" onclick="startEditMessage(${msg.id})">수정</button>
                        <button type="button" onclick="deleteMessage(${msg.id})">삭제</button>
                    </div>
                </div>
            `
            : '';

        row.innerHTML = `
            ${mine ? '' : `<div class="msg-sender">${escapeHtml(msg.senderNickname)}</div>`}
            <div class="msg-bubble-wrap">
                <div class="${bubbleClass}" id="msg-bubble-${msg.id}">${bodyText}</div>
                ${actions}
            </div>
            <div class="msg-meta">
                ${editedTag}
                <span class="msg-time">${formatTime(msg.sentAt)}</span>
                ${mine ? `<span class="msg-read-badge" id="msg-read-${msg.id}"></span>` : ''}
            </div>
        `;
    } else {
        row.className = 'msg-row system';
        row.innerHTML = `<div class="msg-bubble">${escapeHtml(msg.content)}</div>`;
    }
}

function toggleMessageMenu(messageId) {
    document.querySelectorAll('.msg-actions-menu').forEach(menu => {
        if (menu.id !== `msg-menu-${messageId}`) {
            menu.classList.add('hidden');
        }
    });
    const menu = document.getElementById(`msg-menu-${messageId}`);
    if (menu) {
        menu.classList.toggle('hidden');
    }
}

function startEditMessage(messageId) {
    toggleMessageMenu(messageId);
    const bubble = document.getElementById(`msg-bubble-${messageId}`);
    if (!bubble) return;

    const originalText = bubble.textContent;
    bubble.innerHTML = `
        <textarea class="msg-edit-input" maxlength="2000">${escapeHtml(originalText)}</textarea>
        <div class="msg-edit-actions">
            <button type="button" onclick="submitEditMessage(${messageId})">저장</button>
            <button type="button" onclick="cancelEditMessage(${messageId}, this)">취소</button>
        </div>
    `;
    bubble.dataset.originalText = originalText;
    bubble.querySelector('textarea').focus();
}

function cancelEditMessage(messageId, btn) {
    const bubble = document.getElementById(`msg-bubble-${messageId}`);
    if (bubble) {
        bubble.textContent = bubble.dataset.originalText || '';
    }
}

function submitEditMessage(messageId) {
    const bubble = document.getElementById(`msg-bubble-${messageId}`);
    const textarea = bubble ? bubble.querySelector('textarea') : null;
    if (!textarea) return;

    const content = textarea.value.trim();
    if (!content || !stompClient || !stompClient.connected) {
        return;
    }

    stompClient.send(`/app/rooms/${roomId}/messages/${messageId}/edit`, {}, JSON.stringify({ content }));
}

function deleteMessage(messageId) {
    toggleMessageMenu(messageId);
    if (!stompClient || !stompClient.connected) {
        return;
    }
    if (!window.confirm('메시지를 삭제하시겠습니까?')) {
        return;
    }
    stompClient.send(`/app/rooms/${roomId}/messages/${messageId}/delete`, {}, JSON.stringify({}));
}

function handlePresenceEvent(event) {
    const member = members.get(event.userId);
    if (member) {
        member.online = event.online;
        renderMemberList();
    }
}

function handleReadEvent(event) {
    const member = members.get(event.userId);
    if (member) {
        member.lastReadAt = event.lastReadAt;
    }
    refreshAllReadBadges();
}

function refreshAllReadBadges() {
    renderedMessages.forEach((info, messageId) => {
        updateReadBadge(messageId);
    });
}

function updateReadBadge(messageId) {
    const badge = document.getElementById(`msg-read-${messageId}`);
    if (!badge) return;

    const info = renderedMessages.get(messageId);
    if (!info) return;

    let unread = 0;
    const sentAt = new Date(info.sentAt).getTime();
    members.forEach((m) => {
        if (m.userId === info.senderId) return;
        const lastReadAt = new Date(m.lastReadAt).getTime();
        if (isNaN(lastReadAt) || lastReadAt < sentAt) {
            unread++;
        }
    });

    badge.textContent = unread > 0 ? String(unread) : '읽음';
}

function renderMemberList() {
    const listEl = document.getElementById('member-list');
    if (!listEl) return;

    const me = Array.from(members.values()).find(m => m.username === currentUsername);
    const isOwner = !!me && me.role === 'OWNER';

    listEl.innerHTML = '';
    Array.from(members.values())
        .sort((a, b) => a.nickname.localeCompare(b.nickname))
        .forEach(m => {
            const li = document.createElement('li');
            li.className = 'member-row';
            const kickBtn = (isOwner && m.username !== currentUsername)
                ? `<button type="button" class="member-kick-btn" onclick="kickMember(${m.userId})">추방</button>`
                : '';
            li.innerHTML = `
                <span class="member-online-dot ${m.online ? 'online' : 'offline'}"></span>
                <span class="member-nickname">${escapeHtml(m.nickname)}</span>
                ${m.role === 'OWNER' ? '<span class="member-role-tag">방장</span>' : ''}
                ${m.status === 'PENDING' ? '<span class="member-role-tag pending">수락 대기</span>' : ''}
                ${kickBtn}
            `;
            listEl.appendChild(li);
        });
}

async function kickMember(userId) {
    if (!window.confirm('이 멤버를 방에서 제외하시겠습니까?')) {
        return;
    }
    try {
        const res = await AUTH.apiFetch('/api/rooms/' + roomId + '/participants/' + userId, { method: 'DELETE' });
        if (res.ok) {
            members.delete(userId);
            renderMemberList();
        }
    } catch (err) {
        // best-effort; member list will resync on next reload
    }
}

function setupMemberPanel(roomId) {
    const toggleBtn = document.getElementById('member-panel-toggle');
    const panel = document.getElementById('member-panel');
    const closeBtn = document.getElementById('member-panel-close');
    const searchInput = document.getElementById('invite-search-input');

    if (toggleBtn && panel) {
        toggleBtn.addEventListener('click', () => {
            panel.classList.toggle('hidden');
            if (!panel.classList.contains('hidden')) {
                loadMembers(roomId);
            }
        });
    }
    if (closeBtn && panel) {
        closeBtn.addEventListener('click', () => panel.classList.add('hidden'));
    }
    if (searchInput) {
        searchInput.addEventListener('input', () => {
            clearTimeout(inviteSearchTimer);
            const query = searchInput.value.trim();
            if (!query) {
                renderInviteResults([]);
                return;
            }
            inviteSearchTimer = setTimeout(() => runInviteSearch(query), 250);
        });
    }
}

async function runInviteSearch(query) {
    try {
        const res = await AUTH.apiFetch('/api/users/search?q=' + encodeURIComponent(query));
        const results = await res.json();
        renderInviteResults(results.filter(u => !members.has(u.id)));
    } catch (err) {
        renderInviteResults([]);
    }
}

function renderInviteResults(results) {
    const resultsEl = document.getElementById('invite-search-results');
    if (!resultsEl) return;

    resultsEl.innerHTML = '';
    if (results.length === 0) {
        resultsEl.classList.add('hidden');
        return;
    }

    resultsEl.classList.remove('hidden');
    results.forEach(u => {
        const li = document.createElement('li');
        li.textContent = `${u.nickname} (${u.username})`;
        li.addEventListener('click', () => inviteMember(u.username));
        resultsEl.appendChild(li);
    });
}

async function inviteMember(username) {
    try {
        await AUTH.apiFetch('/api/rooms/' + roomId + '/participants', {
            method: 'POST',
            body: JSON.stringify({ usernames: [username] })
        });
        document.getElementById('invite-search-input').value = '';
        renderInviteResults([]);
        loadMembers(roomId);
    } catch (err) {
        // invite failure surfaces via the room's system message absence; non-fatal
    }
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
