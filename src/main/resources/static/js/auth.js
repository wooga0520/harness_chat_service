const AUTH = (() => {
    const TOKEN_KEY = 'chat_token';
    const USERNAME_KEY = 'chat_username';

    function saveAuth(token, username) {
        localStorage.setItem(TOKEN_KEY, token);
        localStorage.setItem(USERNAME_KEY, username);
    }

    function getToken() {
        return localStorage.getItem(TOKEN_KEY);
    }

    function getUsername() {
        return localStorage.getItem(USERNAME_KEY);
    }

    function clearAuth() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USERNAME_KEY);
    }

    function requireAuth() {
        if (!getToken()) {
            window.location.href = '/login';
        }
    }

    async function logout() {
        const token = getToken();
        try {
            if (token) {
                await fetch('/api/auth/logout', {
                    method: 'POST',
                    headers: { 'Authorization': 'Bearer ' + token }
                });
            }
        } catch (err) {
            // 서버 요청이 실패해도 클라이언트 쪽 로그아웃은 진행한다.
        } finally {
            clearAuth();
            window.location.href = '/login';
        }
    }

    async function apiFetch(url, options) {
        options = options || {};
        const headers = Object.assign(
            {},
            options.headers,
            {
                'Authorization': 'Bearer ' + getToken(),
                'Content-Type': 'application/json'
            }
        );
        const res = await fetch(url, Object.assign({}, options, { headers }));
        if (res.status === 401) {
            clearAuth();
            window.location.href = '/login';
            throw new Error('Unauthorized');
        }
        return res;
    }

    return { saveAuth, getToken, getUsername, clearAuth, requireAuth, logout, apiFetch };
})();