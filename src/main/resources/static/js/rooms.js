document.addEventListener('DOMContentLoaded', () => {
    AUTH.requireAuth();
    document.getElementById('current-user').textContent = AUTH.getUsername();
    document.getElementById('logout-btn').addEventListener('click', AUTH.logout);

    loadRooms();

    document.getElementById('create-room-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const errorEl = document.getElementById('create-room-error');
        errorEl.textContent = '';

        const name = document.getElementById('room-name').value.trim();
        const membersRaw = document.getElementById('room-members').value.trim();
        const memberUsernames = membersRaw.split(',').map(s => s.trim()).filter(Boolean);

        try {
            const res = await AUTH.apiFetch('/api/rooms', {
                method: 'POST',
                body: JSON.stringify({ name, memberUsernames })
            });
            if (!res.ok) {
                throw new Error('방 생성에 실패했습니다. 아이디를 확인해주세요.');
            }
            const room = await res.json();
            window.location.href = '/rooms/' + room.id;
        } catch (err) {
            errorEl.textContent = err.message || '방 생성에 실패했습니다.';
        }
    });

    document.getElementById('dm-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const errorEl = document.getElementById('dm-error');
        errorEl.textContent = '';

        const targetUsername = document.getElementById('dm-target').value.trim();

        try {
            const res = await AUTH.apiFetch('/api/rooms/dm', {
                method: 'POST',
                body: JSON.stringify({ targetUsername })
            });
            if (!res.ok) {
                throw new Error('DM 시작에 실패했습니다. 아이디를 확인해주세요.');
            }
            const room = await res.json();
            window.location.href = '/rooms/' + room.id;
        } catch (err) {
            errorEl.textContent = err.message || 'DM 시작에 실패했습니다.';
        }
    });
});

async function loadRooms() {
    const listEl = document.getElementById('room-list');
    try {
        const res = await AUTH.apiFetch('/api/rooms');
        const rooms = await res.json();

        listEl.innerHTML = '';
        if (rooms.length === 0) {
            listEl.innerHTML = '<li class="empty-hint">참여 중인 채팅방이 없습니다.</li>';
            return;
        }

        rooms.forEach(room => {
            const li = document.createElement('li');
            const displayName = room.name || room.memberNicknames.join(', ');
            li.innerHTML = `
                <a href="/rooms/${room.id}">
                    <div class="room-name">${escapeHtml(displayName)}${room.group ? '' : ' (DM)'}</div>
                    <div class="room-members">${escapeHtml(room.memberNicknames.join(', '))}</div>
                </a>
            `;
            listEl.appendChild(li);
        });
    } catch (err) {
        listEl.innerHTML = '<li class="empty-hint">채팅방 목록을 불러오지 못했습니다.</li>';
    }
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}