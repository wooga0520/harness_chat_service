let groupMemberSearchTimer = null;
let dmSearchTimer = null;
const selectedGroupMembers = new Map(); // username -> nickname
let selectedDmTarget = null; // { username, nickname }

document.addEventListener('DOMContentLoaded', () => {
    AUTH.requireAuth();
    document.getElementById('current-user').textContent = AUTH.getUsername();
    document.getElementById('logout-btn').addEventListener('click', AUTH.logout);

    loadRooms();
    setupUserSearch('room-members-search', 'room-members-results', onGroupMemberPicked);
    setupUserSearch('dm-target-search', 'dm-target-results', onDmTargetPicked);

    document.getElementById('create-room-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const errorEl = document.getElementById('create-room-error');
        errorEl.textContent = '';

        const name = document.getElementById('room-name').value.trim();
        const memberUsernames = Array.from(selectedGroupMembers.keys());
        if (memberUsernames.length === 0) {
            errorEl.textContent = '초대할 사용자를 검색해서 선택해주세요.';
            return;
        }

        try {
            const res = await AUTH.apiFetch('/api/rooms', {
                method: 'POST',
                body: JSON.stringify({ name, memberUsernames })
            });
            if (!res.ok) {
                throw new Error('방 생성에 실패했습니다.');
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

        if (!selectedDmTarget) {
            errorEl.textContent = 'DM을 시작할 사용자를 검색해서 선택해주세요.';
            return;
        }

        try {
            const res = await AUTH.apiFetch('/api/rooms/dm', {
                method: 'POST',
                body: JSON.stringify({ targetUsername: selectedDmTarget.username })
            });
            if (!res.ok) {
                throw new Error('DM 시작에 실패했습니다.');
            }
            const room = await res.json();
            window.location.href = '/rooms/' + room.id;
        } catch (err) {
            errorEl.textContent = err.message || 'DM 시작에 실패했습니다.';
        }
    });
});

function setupUserSearch(inputId, resultsId, onPick) {
    const input = document.getElementById(inputId);
    const resultsEl = document.getElementById(resultsId);
    if (!input || !resultsEl) return;

    input.addEventListener('input', () => {
        const query = input.value.trim();
        clearTimeout(input._searchTimer);
        if (!query) {
            renderUserSearchResults(resultsEl, [], onPick);
            return;
        }
        input._searchTimer = setTimeout(async () => {
            try {
                const res = await AUTH.apiFetch('/api/users/search?q=' + encodeURIComponent(query));
                const results = await res.json();
                renderUserSearchResults(resultsEl, results, onPick);
            } catch (err) {
                renderUserSearchResults(resultsEl, [], onPick);
            }
        }, 250);
    });
}

function renderUserSearchResults(resultsEl, results, onPick) {
    resultsEl.innerHTML = '';
    if (results.length === 0) {
        resultsEl.classList.add('hidden');
        return;
    }
    resultsEl.classList.remove('hidden');
    results.forEach(u => {
        const li = document.createElement('li');
        li.textContent = `${u.nickname} (${u.username})`;
        li.addEventListener('click', () => onPick(u));
        resultsEl.appendChild(li);
    });
}

function onGroupMemberPicked(user) {
    selectedGroupMembers.set(user.username, user.nickname);
    renderSelectedGroupMembers();
    document.getElementById('room-members-search').value = '';
    document.getElementById('room-members-results').classList.add('hidden');
}

function renderSelectedGroupMembers() {
    const el = document.getElementById('selected-group-members');
    el.innerHTML = '';
    selectedGroupMembers.forEach((nickname, username) => {
        const chip = document.createElement('span');
        chip.className = 'chip';
        chip.textContent = nickname;
        const removeBtn = document.createElement('button');
        removeBtn.type = 'button';
        removeBtn.textContent = '×';
        removeBtn.addEventListener('click', () => {
            selectedGroupMembers.delete(username);
            renderSelectedGroupMembers();
        });
        chip.appendChild(removeBtn);
        el.appendChild(chip);
    });
}

function onDmTargetPicked(user) {
    selectedDmTarget = user;
    document.getElementById('dm-target-search').value = `${user.nickname} (${user.username})`;
    document.getElementById('dm-target-results').classList.add('hidden');
    document.getElementById('dm-target-results').innerHTML = '';
}

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
