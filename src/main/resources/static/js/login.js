document.addEventListener('DOMContentLoaded', () => {
    if (AUTH.getToken()) {
        window.location.href = '/rooms';
        return;
    }

    const form = document.getElementById('login-form');
    const errorEl = document.getElementById('error');

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        errorEl.textContent = '';

        const username = document.getElementById('username').value.trim();
        const password = document.getElementById('password').value;

        try {
            const res = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });

            if (!res.ok) {
                throw new Error('아이디 또는 비밀번호가 올바르지 않습니다.');
            }

            const data = await res.json();
            AUTH.saveAuth(data.accessToken, username);
            window.location.href = '/rooms';
        } catch (err) {
            errorEl.textContent = err.message || '로그인에 실패했습니다.';
        }
    });
});