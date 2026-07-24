document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('signup-form');
    const errorEl = document.getElementById('error');

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        errorEl.textContent = '';

        const username = document.getElementById('username').value.trim();
        const password = document.getElementById('password').value;
        const nickname = document.getElementById('nickname').value.trim();

        try {
            const res = await fetch('/api/auth/signup', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password, nickname })
            });

            if (res.status === 409) {
                throw new Error('이미 사용 중인 아이디입니다.');
            }
            if (!res.ok) {
                throw new Error('입력값을 확인해주세요. (아이디 3자 이상, 비밀번호 4자 이상)');
            }

            window.location.href = '/login';
        } catch (err) {
            errorEl.textContent = err.message || '회원가입에 실패했습니다.';
        }
    });
});